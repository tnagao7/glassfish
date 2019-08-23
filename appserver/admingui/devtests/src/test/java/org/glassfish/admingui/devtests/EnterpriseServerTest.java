/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.admingui.devtests;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnterpriseServerTest extends BaseSeleniumTestClass {
    public static final String TRIGGER_ADVANCED_APPLICATIONS_CONFIGURATION = "i18nc.domain.AppsConfigPageHelp";
    public static final String TRIGGER_GENERAL_INFORMATION = "i18n.instance.GeneralTitle";
    public static final String TRIGGER_ADVANCED_DOMAIN_ATTRIBUTES = "i18nc.domain.DomainAttrsPageTitleHelp";
    public static final String TRIGGER_SYSTEM_PROPERTIES = "i18n.common.AdditionalProperties"; // There is no page help on sysprops pages anymore, it seems
    public static final String TRIGGER_RESOURCES = "i18nc.resourcesTarget.pageTitleHelp";

    // Disabling this test.  I'm not sure where this is trying to go.  jdl 10/6/10
//    @Test
    public void testAdvancedApplicationsConfiguration() {
        final String property = generateRandomString();
        final String value = property + "value";
        final String description = property + "description";

        gotoDasPage();
        clickAndWait("propertyForm:serverInstTabs:advanced", TRIGGER_ADVANCED_APPLICATIONS_CONFIGURATION);
        setFieldValue("propertyForm:propertySheet:propertSectionTextField:reloadIntervalProp:ReloadInterval", "5");
        setFieldValue("propertyForm:propertySheet:propertSectionTextField:AdminTimeoutProp:AdminTimeout", "30");

        int count = addTableRow("propertyForm:basicTable", "propertyForm:basicTable:topActionsGroup1:addSharedTableButton");

        setFieldValue("propertyForm:basicTable:rowGroup1:0:col2:col1St", property);
        setFieldValue("propertyForm:basicTable:rowGroup1:0:col3:col1St", value);
        setFieldValue("propertyForm:basicTable:rowGroup1:0:col4:col1St", description);

        clickAndWait("propertyForm:propertyContentPage:topButtons:saveButton", TRIGGER_NEW_VALUES_SAVED);

        clickAndWait("propertyForm:serverInstTabs:advanced:domainAttrs", TRIGGER_ADVANCED_DOMAIN_ATTRIBUTES);
        clickAndWait("propertyForm:serverInstTabs:advanced:appConfig", TRIGGER_ADVANCED_APPLICATIONS_CONFIGURATION);

        assertEquals("5", getFieldValue("propertyForm:propertySheet:propertSectionTextField:reloadIntervalProp:ReloadInterval"));
        assertEquals("30", getFieldValue("propertyForm:propertySheet:propertSectionTextField:AdminTimeoutProp:AdminTimeout"));
        
        assertTableRowCount("propertyForm:basicTable", count);
    }

    @Test
    public void testAdvancedDomainAttributes() {
        clickAndWait("treeForm:tree:nodes:nodes_link", TRIGGER_ADVANCED_DOMAIN_ATTRIBUTES);
        setFieldValue("propertyForm:propertySheet:propertSectionTextField:localeProp:Locale", "fr");
        clickAndWait("propertyForm:propertyContentPage:topButtons:saveButton", TRIGGER_NEW_VALUES_SAVED);

        clickAndWait("propertyForm:domainTabs:appConfig", TRIGGER_ADVANCED_APPLICATIONS_CONFIGURATION);
        clickAndWait("propertyForm:domainTabs:domainAttrs", TRIGGER_ADVANCED_DOMAIN_ATTRIBUTES);

        assertEquals("fr", getFieldValue("propertyForm:propertySheet:propertSectionTextField:localeProp:Locale"));
        setFieldValue("propertyForm:propertySheet:propertSectionTextField:localeProp:Locale", "");
        clickAndWait("propertyForm:propertyContentPage:topButtons:saveButton", TRIGGER_NEW_VALUES_SAVED);
    }

    @Test
    public void testSystemProperties() {
        final String property = generateRandomString();
        final String value = property + "value";
        final String description = property + "description";

        gotoDasPage();
        clickAndWait("propertyForm:serverInstTabs:serverInstProps", TRIGGER_SYSTEM_PROPERTIES);

        int count = addTableRow("propertyForm:sysPropsTable", "propertyForm:sysPropsTable:topActionsGroup1:addSharedTableButton");
        setFieldValue("propertyForm:sysPropsTable:rowGroup1:0:col2:col1St", property);
        setFieldValue("propertyForm:sysPropsTable:rowGroup1:0:overrideValCol:overrideVal", value);

        clickAndWait("propertyForm:SysPropsPage:topButtons:topButtons:saveButton", TRIGGER_NEW_VALUES_SAVED);
        gotoDasPage();
        clickAndWait("propertyForm:serverInstTabs:serverInstProps", TRIGGER_SYSTEM_PROPERTIES);

        assertTableRowCount("propertyForm:sysPropsTable", count);
    }

    @Test
    public void testServerResourcesPage() {
        final String jndiName = "jdbcResource"+generateRandomString();
        final String description = "devtest test for server->resources page- " + jndiName;
        final String tableID = "propertyForm:resourcesTable";

        StandaloneTest standaloneTest = new StandaloneTest();
        ClusterTest clusterTest = new ClusterTest();
        standaloneTest.deleteAllStandaloneInstances();
        clusterTest.deleteAllClusters();

        JdbcTest jdbcTest = new JdbcTest();
        jdbcTest.createJDBCResource(jndiName, description, "server", MonitoringTest.TARGET_SERVER_TYPE);
        
        gotoServerResourcesPage();
        waitForPageLoad(jndiName, TIMEOUT);
        assertTrue(isTextPresent(jndiName));

        int jdbcCount = getTableRowCountByValue(tableID, "JDBC Resources", "col3:type");
        int customCount = getTableRowCountByValue(tableID, "Custom Resources", "col3:type");

        selectDropdownOption("propertyForm:resourcesTable:topActionsGroup1:filter_list", "Custom Resources");
        waitForTableRowCount(tableID, customCount);

        gotoServerResourcesPage();

        selectDropdownOption("propertyForm:resourcesTable:topActionsGroup1:filter_list", "JDBC Resources");
        waitForTableRowCount(tableID, jdbcCount);

        gotoServerResourcesPage();
        selectTableRowByValue("propertyForm:resourcesTable", jndiName);
        pressButton("propertyForm:resourcesTable:topActionsGroup1:button1");
        waitForButtonEnabledMessage("propertyForm:resourcesTable:topActionsGroup1:button1");

        gotoServerResourcesPage();
        selectTableRowByValue("propertyForm:resourcesTable", jndiName);
        pressButton("propertyForm:resourcesTable:topActionsGroup1:button2");
        waitForButtonDisabledMessage("propertyForm:resourcesTable:topActionsGroup1:button1");

        /*selenium.select("propertyForm:resourcesTable:topActionsGroup1:actions", "JDBC Resources");
        waitForPageLoad(JdbcTest.TRIGGER_NEW_JDBC_RESOURCE, true);
        clickAndWait("form:propertyContentPage:topButtons:cancelButton", JdbcTest.TRIGGER_JDBC_RESOURCES);*/

        jdbcTest.deleteJDBCResource(jndiName, "server", MonitoringTest.TARGET_SERVER_TYPE);
    }

    public void gotoDasPage() {
        clickAndWait("treeForm:tree:applicationServer:applicationServer_link", TRIGGER_GENERAL_INFORMATION);
    }

    private void gotoServerResourcesPage() {
        reset();
        gotoDasPage();
        clickAndWait("propertyForm:serverInstTabs:resources", TRIGGER_RESOURCES);
        waitForElement("propertyForm:resourcesTable");
    }
}