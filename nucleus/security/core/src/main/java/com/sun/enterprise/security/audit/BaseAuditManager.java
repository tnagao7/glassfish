/*
 * Copyright (c) 1997, 2021 Oracle and/or its affiliates. All rights reserved.
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

/*
 * BaseAuditManager.java
 *
 * Created on July 28, 2003, 1:56 PM
 */

package com.sun.enterprise.security.audit;

import com.sun.enterprise.config.serverbeans.SecurityService;
import com.sun.enterprise.security.BaseAuditModule;
import com.sun.enterprise.security.SecurityLoggerInfo;
import com.sun.enterprise.util.LocalStringManagerImpl;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.internal.api.ServerContext;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.types.Property;

/**
 * Basic implementation of audit manager.
 * <p>
 * Projects layered on top of nucleus should extend this class, adding platform-specific methods for auditing platform-specific
 * events. See AppServerAuditManagerImpl for an example. Such implementations should be sure to invoke this class's setTypeClass
 * method. Then this class will keep a list of AuditModules of that specific type in the typedModules field which subclasses can
 * refer to directly.
 * <p>
 * (This implementation was largely refactored from the original BaseAuditManager implementation that combined nucleus and app
 * server features.)
 *
 * @author Harpreet Singh
 * @author Shing Wai Chan
 * @author tjquinn
 */
@Service
@Singleton
public class BaseAuditManager<T extends BaseAuditModule> implements AuditManager {
    static final String NAME = "name";
    static final String CLASSNAME = "classname";

    // For speed, maintain a separate list of audit modules of the specified
    // module subtype (if any).  This allows subclasses to have very efficient
    // access to the specified audit modules which the subclass audit manager
    // deals with.
    protected List<T> typedModules = Collections.synchronizedList(new ArrayList<T>());
    private final Class<T> typedModuleClass = null; // typically set by postConstruct of a subclass invoking setTypeClass

    private static final Logger _logger = SecurityLoggerInfo.getLogger();

    private static final LocalStringManagerImpl _localStrings = new LocalStringManagerImpl(BaseAuditManager.class);

    private List<BaseAuditModule> instances = Collections.synchronizedList(new ArrayList<BaseAuditModule>());
    // just a copy of names of the audit classes - helpful for log messages
    // since we will not have a lot of audit classes, keeping a duplicate copy
    // seems reasonable.
    private final Map<BaseAuditModule, String> moduleToNameMap = new HashMap<>();
    private final Map<String, BaseAuditModule> nameToModuleMap = new HashMap<>();
    // make this accessible to the containers so that the cost of non-audit case,
    // is just a comparision.
    protected boolean auditOn = false;

    @Inject
    private ServerContext serverContext;

    private static final String AUDIT_MGR_SERVER_STARTUP_KEY = "auditmgr.serverStartup";
    private static final String AUDIT_MGR_SERVER_SHUTDOWN_KEY = "auditmgr.serverShutdown";

    /**
     * This method initializes BaseAuditManager which load audit modules and audit enabled flag
     */
    @Override
    public void loadAuditModules() {
        try {
            SecurityService securityBean = serverContext.getDefaultServices().getService(SecurityService.class,
                ServerEnvironment.DEFAULT_INSTANCE_NAME);

            assert (securityBean != null);
            // @todo will be removed to incorporate the new structure.
            //v3:Commented boolean auditFlag = securityBean.isAuditEnabled();
            boolean auditFlag = Boolean.parseBoolean(securityBean.getAuditEnabled());

            setAuditOn(auditFlag);
            /*V3:Commented
            com.sun.enterprise.config.serverbeans.AuditModule[] am =
                    securityBean.getAuditModule();*/
            List<com.sun.enterprise.config.serverbeans.AuditModule> am = securityBean.getAuditModule();
            for (com.sun.enterprise.config.serverbeans.AuditModule it : am) {
                //V3:Commented for (int i = 0; i < am.length; i++){
                try {
                    //V3:Commented String name = am[i].getName();
                    //V3:Commented String classname = am[i].getClassname();
                    String name = it.getName();
                    String classname = it.getClassname();
                    Properties p = new Properties();
                    //XXX should we remove this two extra properties
                    p.setProperty(NAME, name);
                    p.setProperty(CLASSNAME, classname);
                    List<Property> ep = it.getProperty();
                    /*V3:Commented
                    ElementProperty[] ep = am[i].getElementProperty();
                    int epsize = am[i].sizeElementProperty();
                    for (int j = 0; j < epsize; j++){
                        String nme = ep[j].getName();
                        String val = ep[j].getValue();
                        p.setProperty(nme, val);
                    }*/
                    for (Property prop : ep) {
                        p.setProperty(prop.getName(), prop.getValue());
                    }
                    BaseAuditModule auditModule = loadAuditModule(classname, p);
                    instances.add(auditModule);
                    moduleToNameMap.put(auditModule, name);
                    nameToModuleMap.put(name, auditModule);
                    if (isAuditModuleOfParameterizedType(auditModule)) {
                        typedModules.add((T) auditModule);
                    }
                } catch (Exception ex) {
                    String msg = _localStrings.getLocalString("auditmgr.loaderror", "Audit: Cannot load AuditModule = {0}",
                        //V3:Commented new Object[]{ am[i].getName() });
                        new Object[] { it.getName() });
                    _logger.log(Level.WARNING, msg, ex);
                }
            }
        } catch (Exception e) {
            String msg = _localStrings.getLocalString("auditmgr.badinit",
                "Audit: Cannot load Audit Module Initialization information. AuditModules will not be loaded.");
            _logger.log(Level.WARNING, msg, e);
        }
    }

    /**
     * Add the given audit module to the list of loaded audit module. Adding the same name twice will override previous one.
     *
     * @param name of auditModule
     * @param am an instance of a class extending BaseAuditModule that has been successfully loaded into the system.
     * @exception
     */
    public BaseAuditModule addAuditModule(String name, String classname, Properties props) throws Exception {
        // make sure only a name corresponding to only one auditModule
        removeAuditModule(name);
        BaseAuditModule am = loadAuditModule(classname, props);

        moduleToNameMap.put(am, name);
        nameToModuleMap.put(name, am);
        // clone list to resolve multi-thread issues in looping instances
        instances = copyAndAdd(instances, am);
        if (isAuditModuleOfParameterizedType(am)) {
            typedModules = copyAndAdd(typedModules, (T) am);
        }
        return am;
    }

    private boolean isAuditModuleOfParameterizedType(final BaseAuditModule am) {
        return (typedModuleClass != null && typedModuleClass.isAssignableFrom(am.getClass()));
    }

    private <U extends BaseAuditModule> List<U> copyAndAdd(final List<U> orig, final U am) {
        final List<U> list = new ArrayList<>();
        Collections.copy(orig, list);
        list.add(am);
        return list;
    }

    private <U extends BaseAuditModule> List<U> copyAndRemove(final List<U> orig, final U am) {
        final List<U> list = new ArrayList<>();
        Collections.copy(orig, list);
        list.remove(am);
        return list;
    }

    /**
     * Remove the audit module of given name from the loaded list.
     *
     * @param name of auditModule
     */
    public BaseAuditModule removeAuditModule(String name) {
        final BaseAuditModule am = nameToModuleMap.get(name);
        if (am != null) {
            nameToModuleMap.remove(name);
            moduleToNameMap.remove(am);
            // clone list to resolve multi-thread issues in looping instances
            instances = copyAndRemove(instances, am);
            if (isAuditModuleOfParameterizedType(am)) {
                typedModules = copyAndRemove(typedModules, (T) am);
            }
        }
        return am;
    }

    /**
     * Get the audit module of given name from the loaded list.
     *
     * @param name of auditModule
     */
    BaseAuditModule getAuditModule(String name) {
        return nameToModuleMap.get(name);
    }

    /**
     * This method return auditModule with given classname and properties.
     *
     * @param classname
     * @param props
     * @exception
     */
    private BaseAuditModule loadAuditModule(String classname, Properties props) throws Exception {
        BaseAuditModule auditModule;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class am = Class.forName(classname, true, loader);
        Object obj = am.newInstance();
        auditModule = (BaseAuditModule) obj;
        auditModule.init(props);
        return auditModule;
    }

    public LocalStringManagerImpl getLocalStrings() {
        return _localStrings;
    }

    public Logger getLogger() {
        return _logger;
    }

    /**
     * logs the authentication call for all the loaded modules.
     *
     * @see com.sun.appserv.security.BaseAuditModule.authentication
     */
    @Override
    public void authentication(final String user, final String realm, final boolean success) {
        if (auditOn) {
            for (BaseAuditModule am : instances) {
                try {
                    am.authentication(user, realm, success);
                } catch (Exception ex) {
                    final String name = moduleName(am);
                    final String msg = _localStrings.getLocalString("auditmgr.authentication",
                        " Audit Module {0} threw the following exception during authentication:", name);
                    _logger.log(Level.INFO, msg, ex);
                }
            }
        }
    }

    @Override
    public void serverStarted() {
        if (auditOn) {
            for (BaseAuditModule am : instances) {
                try {
                    am.serverStarted();
                } catch (Exception ex) {
                    final String name = moduleName(am);
                    final String msg = _localStrings.getLocalString(AUDIT_MGR_SERVER_STARTUP_KEY,
                        " Audit Module {0} threw the following exception during server startup :", name);
                    _logger.log(Level.INFO, msg, ex);
                }
            }
        }
    }

    @Override
    public void serverShutdown() {
        if (auditOn) {
            for (BaseAuditModule am : instances) {
                try {
                    am.serverShutdown();
                } catch (Exception ex) {
                    final String name = moduleName(am);
                    final String msg = _localStrings.getLocalString(AUDIT_MGR_SERVER_SHUTDOWN_KEY,
                        " Audit Module {0} threw the following exception during server shutdown :", name);
                    _logger.log(Level.INFO, msg, ex);
                }
            }
        }
    }

    public void setAuditOn(boolean auditOn) {
        this.auditOn = auditOn;
    }

    @Override
    public boolean isAuditOn() {
        return auditOn;
    }

    protected String moduleName(final BaseAuditModule am) {
        return moduleToNameMap.get(am);
    }

    protected List<T> instances(final Class<T> c) {
        final List<T> result = new ArrayList<>();
        for (BaseAuditModule am : instances) {
            if (c.isAssignableFrom(c)) {
                result.add((T) am);
            }
        }
        return result;
    }
}
