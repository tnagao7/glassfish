/*
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.enterprise.admin;

import com.sun.enterprise.config.serverbeans.Application;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Engine;
import com.sun.enterprise.config.serverbeans.Module;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.api.admin.config.ConfigurationUpgrade;
import org.glassfish.hk2.api.PostConstruct;
import org.jvnet.hk2.annotations.Optional;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.Transaction;
import org.jvnet.hk2.config.TransactionFailure;
import org.jvnet.hk2.config.types.Property;
import org.jvnet.hk2.config.types.PropertyBag;


/**
 * Startup service to update existing domain.xml to the latest expected format
 *
 * @author Jerome Dochez
 */
@Service
public class UpgradeService implements ConfigurationUpgrade, PostConstruct {

    @Inject
    Domain domain;

    /*
     * Required to make gms changes before any changes to a cluster
     * or config can be saved. This is because GMS changed attribute
     * names from v2 to 3.1. (Issue 15195.)
     */
    @Inject @Named("gmsupgrade") @Optional
    ConfigurationUpgrade precondition = null;

    private final static Logger logger = Logger.getAnonymousLogger();

    private static final String APPCLIENT_SNIFFER_NAME = "appclient";
    private static final String V3_0_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME = "javaWebStartEnabled";
    private static final String GF3_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME = "java-web-start-enabled";

    @Override
    public void postConstruct() {
        upgradeApplicationElements();
    }

    private void upgradeApplicationElements() {
        upgradeV3PreludeApplicationElements();
        upgradeV3_0_1_AppClientElements();
    }

    private void upgradeV3PreludeApplicationElements() {
        // in v3-prelude, engines were created under application directly,
        // in v3 final, engines are placed under individual modules composing the application
        // so if we have engines under application and not modules deployed, we need to upgrade
        List<Application> allApps = new ArrayList<Application>();
        allApps.addAll(domain.getApplications().getApplications());
        allApps.addAll(domain.getSystemApplications().getApplications());
        for (Application app : allApps) {
            if (app.getEngine()!=null && app.getEngine().size()>0 &&
                    (app.getModule()==null || app.getModule().size()==0)) {
                // we need to update the application declaration from v3 prelude,
                // we can safely assume this was a single module application
                try {
                    ConfigSupport.apply(new SingleConfigCode<Application>() {
                        public Object run(Application application) throws PropertyVetoException, TransactionFailure {
                            Module module = application.createChild(Module.class);
                            module.setName(application.getName());
                            for (Engine engine : application.getEngine()) {
                                module.getEngines().add(engine);
                            }
                            application.getModule().add(module);
                            application.getEngine().clear();
                            return null;
                        }
                    }, app);
                } catch(TransactionFailure tf) {
                    Logger.getAnonymousLogger().log(Level.SEVERE, "Failure while upgrading application "
                            + app.getName() + " please redeploy", tf);
                    throw new RuntimeException(tf);
                }
            }
        }
    }

    /**
     * Adds a property with the specified name and value to a writable config
     * object.
     * @param <T> the type of the config object
     * @param propName name of the property to add
     * @param propValue value of the property to add
     * @param owner_w the owning config object
     * @return the added Property object
     * @throws TransactionFailure
     * @throws PropertyVetoException
     */
    private <T extends PropertyBag & ConfigBeanProxy> Property addProperty(
            final String propName,
            final String propValue,
            final T owner_w) throws TransactionFailure, PropertyVetoException {
        final Property p = owner_w.createChild(Property.class);
        p.setName(propName);
        p.setValue(propValue);
        owner_w.getProperty().add(p);
        return p;
    }

    private void upgradeV3_0_1_AppClientElements() {
        /*
         * If an app client has a property setting for javaWebStartEnabled we
         * convert it to java-web-start-enabled which is the documented name.
         * App clients can be either applications or modules within an EAR.
         */
        final Transaction t = new Transaction();
        try {
            for (Application app : domain.getApplications().getApplications()) {
                System.out.println("Checking app " + app.getName());
                Application app_w = null;
                Property oldSetting = app.getProperty(V3_0_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME);
                if (oldSetting != null) {
                    logger.log(Level.INFO, "For application {0} converting property {1} to {2}",
                    new Object[] {
                        app.getName(),
                        V3_0_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME,
                        GF3_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME});
                    app_w = t.enroll(app);
                    addProperty(GF3_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME,
                            oldSetting.getValue(), app_w);
                    app_w.getProperty().remove(oldSetting);
                }
                for (Module mod : app.getModule()) {
                    if (mod.getEngine(APPCLIENT_SNIFFER_NAME) != null) {
                        /*
                         * This is an app client.  See if the client has
                         * a property setting using the old name.
                         */
                        oldSetting = mod.getProperty(V3_0_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME);
                        if (oldSetting != null) {
                            logger.log(Level.INFO, "For application {0}/module {1} converting property {2} to {3}",
                                new Object[] {
                                    app.getName(),
                                    mod.getName(),
                                    V3_0_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME,
                                    GF3_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME});
                            final Module mod_w = t.enroll(mod);
                            addProperty(GF3_1_JAVA_WEB_START_ENABLED_PROPERTY_NAME,
                                    oldSetting.getValue(),
                                    mod_w);
                            mod_w.getProperty().remove(oldSetting);
                        }
                    }
                }

            }
            t.commit();
        } catch (Exception ex) {
            t.rollback();
            throw new RuntimeException("Error upgrading application", ex);
        }
    }
}
