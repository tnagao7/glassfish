/*
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 * CommonHandlers.java
 *
 * Created on August 30, 2006, 4:21 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.glassfish.admingui.handlers;

import com.sun.jsftemplating.annotation.Handler;
import com.sun.jsftemplating.annotation.HandlerInput;
import com.sun.jsftemplating.annotation.HandlerOutput;
import com.sun.jsftemplating.layout.descriptors.handler.HandlerContext;
import com.sun.webui.jsf.component.Calendar;
import com.sun.webui.jsf.component.DropDown;
import com.sun.webui.jsf.component.Hyperlink;
import com.sun.webui.jsf.model.Option;
import com.sun.webui.jsf.model.OptionGroup;
import com.sun.webui.jsf.model.UploadedFile;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.admingui.common.handlers.MonitoringHandlers;
import org.glassfish.admingui.common.util.GuiUtil;
import org.glassfish.admingui.common.util.RestUtil;
import org.glassfish.admingui.util.SunOptionUtil;

public class WoodstockHandler {

    /**
     * Creates a new instance of CommonHandlers
     */
    public WoodstockHandler() {
    }

    /**
     * <p>
     * This handler will delete file from temp directory</p>
     * @param handlerCtx
     * @throws IOException
     */
    @Handler(id = "deleteFileFromTempDir",
            input = {
                @HandlerInput(name = "deleteTempFile", type = String.class)})
    public static void deleteFileFromTempDir(HandlerContext handlerCtx) throws IOException {
        Logger logger = GuiUtil.getLogger();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(GuiUtil.getCommonMessage("log.indeleteFileFromTempDir"));
        }

        String deleteTempFile = (String) handlerCtx.getInputValue("deleteTempFile");
        if (deleteTempFile == null) {
            return;
        }
        Path pathToFile = Paths.get(deleteTempFile);
        if (Files.exists(pathToFile)) {
            try {
                Files.delete(pathToFile);
                Files.delete(pathToFile.getParent());
            } catch (IOException x) {
                logger.log(Level.WARNING, prepareFileNotDeletedMessage(deleteTempFile));

            } catch (SecurityException e) {

            }
        }
    }

    static String prepareFileNotDeletedMessage(String file) {
      return GuiUtil.getCommonMessage("log.fileCouldntbeFound", new Object[] { file });
    }


    /**
     * <p>
     * This method uploads a file temp directory</p>
     * <p>
     * Input value: "file" -- Type:
     * <code>com.sun.webui.jsf.model.UploadedFile</code></p>
     * <p>
     * Output value: "uploadDir" -- Type: <code>java.lang.String</code></p>
     *
     * @param        handlerCtx        The HandlerContext.
     */
    @Handler(id = "uploadFileToTempDir",
            input = {
                @HandlerInput(name = "file", type = UploadedFile.class)},
            output = {
                @HandlerOutput(name = "origPath", type = String.class),
                @HandlerOutput(name = "uploadedTempFile", type = String.class)
            })
    public static void uploadFileToTempDir(HandlerContext handlerCtx) {
        Logger logger = GuiUtil.getLogger();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(GuiUtil.getCommonMessage("log.inUploadFileToTmpDir"));
        }
        UploadedFile uploadedFile = (UploadedFile) handlerCtx.getInputValue("file");
        File tmpFile = null;
        String uploadTmpFile = "";
        if (uploadedFile != null) {

            String name = uploadedFile.getOriginalName();
            logger.info("uploadFileName=" + name);
            //see bug# 6498910, for IE, getOriginalName() returns the full path, including the drive.
            //for any other browser, it just returns the file name.
            int lastIndex = name.lastIndexOf("\\");
            if (lastIndex != -1) {
                name = name.substring(lastIndex + 1, name.length());
            }
            int index = name.lastIndexOf(".");
            if (index <= 0) {
                logger.info("name=" + name + ",index=" + index);
                String mesg = GuiUtil.getMessage("msg.deploy.nullArchiveError");
                GuiUtil.handleError(handlerCtx, mesg);
                return;
            }
            handlerCtx.setOutputValue("origPath", name);
            try {
                // keep the filename for the temp file, it's used to generate the context root
                tmpFile = createTempFileWithOriginalFileName(name);

                // delete the file, otherwise FileUtils#moveTo called inside uploadedFile.write
                // throws file already exist error
                Files.deleteIfExists(tmpFile.toPath());

                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(GuiUtil.getCommonMessage("log.writeToTmpFile"));
                }

                uploadedFile.write(tmpFile);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(GuiUtil.getCommonMessage("log.afterWriteToTmpFile"));
                }
                uploadTmpFile = tmpFile.getCanonicalPath();
            } catch (IOException ioex) {
                try {
                    if (tmpFile != null) {
                        Files.deleteIfExists(tmpFile.toPath());
                        Files.deleteIfExists(tmpFile.toPath().getParent());
                    }
                } catch (Exception ex) {
                    // ignore nested exception, handle the original exception only
                }
                GuiUtil.handleException(handlerCtx, ioex);
            } catch (Exception ex) {
                GuiUtil.handleException(handlerCtx, ex);
            }
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(GuiUtil.getCommonMessage("log.successfullyUploadedTmp") + uploadTmpFile);
        }
        handlerCtx.setOutputValue("uploadedTempFile", uploadTmpFile);
    }

    private static File createTempFileWithOriginalFileName(String name) throws IOException {
        final Path dir = Files.createTempDirectory(name);
        dir.toFile().deleteOnExit();
        final Path file = dir.resolve(name);
        Files.createFile(file);
        final File result = file.toFile();
        result.deleteOnExit();
        return result;
    }

    /**
     * <p>
     * This handler enable or disable the table text field according to the
     * method value.
     */
    @Handler(id = "setDisableConnectionPoolTableField",
            input = {
                @HandlerInput(name = "tableDD", type = com.sun.webui.jsf.component.DropDown.class),
                @HandlerInput(name = "validationField", type = com.sun.webui.jsf.component.DropDown.class),
                @HandlerInput(name = "methodValue", type = String.class)})
    public static void setDisableConnectionPoolTableField(HandlerContext handlerCtx) {
        String methodValue = (String) handlerCtx.getInputValue("methodValue");
        DropDown tableDD = (DropDown) handlerCtx.getInputValue("tableDD");
        DropDown validationDD = (DropDown) handlerCtx.getInputValue("validationField");
        if ("table".equals(methodValue)) {
            tableDD.setDisabled(false);
            validationDD.setDisabled(true);
        } else if ("custom-validation".equals(methodValue)) {
            tableDD.setDisabled(true);
            validationDD.setDisabled(false);

        } else {
            tableDD.setDisabled(true);
            validationDD.setDisabled(true);
        }
    }

    @Handler(id = "createHyperlinkArray",
            output = {
                @HandlerOutput(name = "links", type = Hyperlink[].class)
            })
    public static void createHyperlinkArray(HandlerContext handlerCtx) {
        FacesContext ctx = handlerCtx.getFacesContext();
        ExternalContext extCtx = ctx.getExternalContext();
        Map<String, String[]> reqParams = extCtx.getRequestParameterValuesMap();
        String linkText[] = reqParams.get("text");
        String linkUrl[] = reqParams.get("urls");
        if (linkText == null) {
            // No data!  Should we add something here anyway?
            return;
        }

        int len = linkText.length;
        Hyperlink arr[] = new Hyperlink[len];
        String url = null;
        String ctxPath = extCtx.getRequestContextPath();
        int ctxPathSize = ctxPath.length();
        for (int idx = 0; idx < len; idx++) {
            // FIXME: Set parent
            arr[idx] = new Hyperlink();
            arr[idx].setId("bcLnk" + idx);
            // Set rendererType to avoid using widget renderer!!
            arr[idx].setRendererType("com.sun.webui.jsf.Hyperlink");
            arr[idx].setText(linkText[idx]);
            url = linkUrl[idx];
            if (url.startsWith(ctxPath)) {
                url = url.substring(ctxPathSize);
            }
            arr[idx].setUrl(url);
        }
        handlerCtx.setOutputValue("links", arr);
    }

    @Handler(id = "dummyHyperlinkArray",
            output = {
                @HandlerOutput(name = "links", type = Hyperlink[].class)
            })
    public static void dummyHyperlinkArray(HandlerContext handlerCtx) {
        Hyperlink arr[] = new Hyperlink[1];
        arr[0] = new Hyperlink();
        arr[0].setText(">");
        handlerCtx.setOutputValue("links", arr);
    }

    @Handler(id = "gf.stringArrayToSelectItemArray",
            input = {
                @HandlerInput(name = "stringArray", type = String[].class, required = true)},
            output = {
                @HandlerOutput(name = "item", type = SelectItem[].class)})
    public static void stringArrayToSelectItemArray(HandlerContext handlerCtx) {

        String[] stringArray = (String[]) handlerCtx.getInputValue("stringArray");
        handlerCtx.setOutputValue("item", SunOptionUtil.getOptions(stringArray));

    }

    @Handler(id = "selectItemArrayToStrArray",
            input = {
                @HandlerInput(name = "item", type = SelectItem[].class, required = true)},
            output = {
                @HandlerOutput(name = "strAry", type = String[].class)})
    public static void selectItemArrayToStrArray(HandlerContext handlerCtx) {

        SelectItem[] item = (SelectItem[]) handlerCtx.getInputValue("item");
        if (item == null || item.length == 0) {
            handlerCtx.setOutputValue("strAry", new String[0]);
            return;
        }
        String[] strAry = new String[item.length];
        for (int i = 0; i < item.length; i++) {
            strAry[i] = (String) item[i].getValue();
        }
        handlerCtx.setOutputValue("strAry", strAry);
    }

    @Handler(id = "gf.convertListToOptionArray",
            input = {
                @HandlerInput(name = "list", type = List.class, required = true)},
            output = {
                @HandlerOutput(name = "optionArray", type = Option[].class)})
    public static void convertListToOptionArray(HandlerContext handlerCtx) {

        List<String> list = (List) handlerCtx.getInputValue("list");
        if (list == null || list.isEmpty()) {
            handlerCtx.setOutputValue("optionArray", new Option[0]);
            return;
        }
        handlerCtx.setOutputValue("optionArray", SunOptionUtil.getOptionsArray(list.toArray(new String[list.size()])));
    }

    /**
     * <p>
     * Returns the date pattern for this calendar component.
     *
     */
    @Handler(id = "getDatePattern",
            input = {
                @HandlerInput(name = "calendarComponent", type = com.sun.webui.jsf.component.Calendar.class, required = true)},
            output = {
                @HandlerOutput(name = "pattern", type = String.class)})
    public static void getDatePattern(HandlerContext handlerCtx) {
        Calendar calendar = (Calendar) handlerCtx.getInputValue("calendarComponent");
        String pattern = calendar.getDateFormatPattern();

        if (pattern == null || pattern.length() == 0) {
            pattern = calendar.getDatePicker().getDateFormatPattern();

            if (pattern == null || pattern.length() == 0) {
                pattern = "MM/dd/yyyy"; //default pattern
            }
        }
        handlerCtx.setOutputValue("pattern", pattern);
    }

    /**
     * <p>
     * Returns the list of monitorable server components</p>
     *
     */
    @Handler(id = "populateServerMonitorDropDown",
            input = {
                @HandlerInput(name = "VSList", type = List.class, required = true),
                @HandlerInput(name = "GCList", type = List.class, required = true),
                @HandlerInput(name = "NLList", type = List.class, required = true),
                @HandlerInput(name = "ThreadSystemList", type = List.class, required = true)},
            output = {
                @HandlerOutput(name = "MonitorList", type = Option[].class)})
    public void populateServerMonitorDropDown(HandlerContext handlerCtx) {
        List vsList = (List) handlerCtx.getInputValue("VSList");
        List threadList = (List) handlerCtx.getInputValue("ThreadSystemList");
        List gcList = (List) handlerCtx.getInputValue("GCList");
        List nlList = (List) handlerCtx.getInputValue("NLList");
        ArrayList menuList = new ArrayList();
        menuList.add(new Option("", ""));
        // Menu for Virtual Servers
        OptionGroup vsMenuOptions = getMenuOptions(vsList, "virtual-server", "", false);
        if (vsMenuOptions != null) {
            menuList.add(vsMenuOptions);
        }

        // Menu for Listeners
        OptionGroup nlMenuOptions = getMenuOptions(nlList, "http-listener", "", false);
        if (nlMenuOptions != null) {
            menuList.add(nlMenuOptions);
        }

        // Menu for Garbage Collectors
        OptionGroup gcMenuOptions = getMenuOptions(gcList, "garbage-collector", "", false);
        if (gcMenuOptions != null) {
            menuList.add(gcMenuOptions);
        }

        // Menu for Thread System
        OptionGroup tsMenuOptions = getMenuOptions(threadList, "thread-system", "", false);
        if (tsMenuOptions != null) {
            menuList.add(tsMenuOptions);
        }
        // Add Menu Options.
        jumpMenuOptions = (Option[]) menuList.toArray(new Option[menuList.size()]);
        //Arrays.sort(jumpMenuOptions);
        handlerCtx.setOutputValue("MonitorList", jumpMenuOptions);
    }

    /**
     * <p>
     * Returns the list of monitorable resource components</p>
     *
     */
    @Handler(id = "populateResourceMonitorDropDown",
            input = {
                @HandlerInput(name = "ResourceList", type = List.class, required = true)},
            output = {
                @HandlerOutput(name = "MonitorList", type = Option[].class),
                @HandlerOutput(name = "FirstItem", type = String.class)})
    public void populateResourceMonitorDropDown(HandlerContext handlerCtx) {
        List rList = (List) handlerCtx.getInputValue("ResourceList");
        ArrayList menuList = new ArrayList();
        // Menu for Resources
        ArrayList resList = new ArrayList();
        String firstItem = null;
        if (rList != null) {
            ListIterator rl = rList.listIterator();
            while (rl.hasNext()) {
                String name = (String) rl.next();
                resList.add(new Option(name, name));
                if (firstItem == null) {
                    firstItem = name;
                }
            }
        }
        Option[] groupedOptions1 = (Option[]) resList.toArray(new Option[resList.size()]);
        OptionGroup jumpGroup1 = new OptionGroup();
        jumpGroup1.setLabel("resources");
        jumpGroup1.setOptions(groupedOptions1);
        menuList.add(jumpGroup1);

        // Add Menu Options.
        jumpMenuOptions = (Option[]) menuList.toArray(new Option[menuList.size()]);

        handlerCtx.setOutputValue("MonitorList", jumpMenuOptions);
        handlerCtx.setOutputValue("FirstItem", firstItem);
    }

    /**
     * <p>
     * Returns the list of monitorable application components</p>
     *
     */
    @Handler(id = "populateApplicationsMonitorDropDown",
            input = {
                @HandlerInput(name = "AppsList", type = List.class, required = true),
                @HandlerInput(name = "monitorURL", type = String.class, required = true)},
            output = {
                @HandlerOutput(name = "MonitorList", type = Option[].class),
                @HandlerOutput(name = "FirstItem", type = String.class)})
    public void populateApplicationsMonitorDropDown(HandlerContext handlerCtx) {
        List aList = (List) handlerCtx.getInputValue("AppsList");
        String monitorURL = (String) handlerCtx.getInputValue("monitorURL");
        ArrayList menuList = new ArrayList();
        String firstItem = null;
        if (aList != null) {
            ListIterator al = aList.listIterator();
            while (al.hasNext()) {
                ArrayList moduleList = new ArrayList();
                String appName = (String) al.next();
                //Add the application name link in the dropdown if there are any app scoped resources.
                if (MonitoringHandlers.doesMonitoringDataExist(monitorURL + "/applications/" + appName + "/resources")) {
                    moduleList.add(appName);
                }
                Set<String> modules = new HashSet<String>();
                try {
                    modules = RestUtil.getChildMap(GuiUtil.getSessionValue("REST_URL") + "/applications/application/" + appName + "/module").keySet();
                } catch (Exception ex) {
                    GuiUtil.handleException(handlerCtx, ex);
                }
                for (String moduleName : modules) {
                    if (MonitoringHandlers.doesAppProxyExist(appName, moduleName)) {
                        if (!moduleList.contains(moduleName)) {
                            moduleList.add(moduleName);
                        }
                    }
                }
                if (moduleList.isEmpty()) {
                    menuList.add(new Option(appName, appName));
                    if (firstItem == null) {
                        firstItem = appName;
                    }
                } else {
                    OptionGroup menuOptions = getMenuOptions(moduleList, appName, "", false);
                    menuList.add(menuOptions);
                    if (firstItem == null) {
                        firstItem = (String) moduleList.get(0);
                    }
                }
            }
        }

        // Add Menu Options.
        jumpMenuOptions = (Option[]) menuList.toArray(new Option[menuList.size()]);

        handlerCtx.setOutputValue("MonitorList", jumpMenuOptions);
        handlerCtx.setOutputValue("FirstItem", firstItem);
    }

    /**
     * <p>
     * Returns the list of monitorable components of an application</p>
     *
     */
    @Handler(id = "populateComponentDropDown",
            input = {
                @HandlerInput(name = "ModuleName", type = String.class, required = true),
                @HandlerInput(name = "VSList", type = List.class, required = true),
                @HandlerInput(name = "MonitorURL", type = String.class, required = true),
                @HandlerInput(name = "AppName", type = String.class, required = true)},
            output = {
                @HandlerOutput(name = "ComponentList", type = Option[].class)})
    public void populateComponentDropDown(HandlerContext handlerCtx) {
        String moduleName = (String) handlerCtx.getInputValue("ModuleName");
        String appname = (String) handlerCtx.getInputValue("AppName");
        String monitorURL = (String) handlerCtx.getInputValue("MonitorURL");
        List vsList = (List) handlerCtx.getInputValue("VSList");
        ArrayList menuList = new ArrayList();
        menuList.add(new Option("", ""));

        if (appname != null && !appname.isEmpty()) {
            //Servlet Instance Menu Options.
            List servletInstanceMenuOptions = getWebComponentMenuOptions(appname, moduleName, vsList, monitorURL, handlerCtx);
            menuList.addAll(servletInstanceMenuOptions);
            //EJB Menu options.
            if (!appname.equals(moduleName)) {
                Map<String, Object> compsMap = MonitoringHandlers.getSubComponents(appname, moduleName);
                if (compsMap != null && compsMap.size() > 0) {
                    for (Map.Entry<String, Object> e : compsMap.entrySet()) {
                        if (!e.getValue().equals("Servlet")) {
                            List compMenuOptions = getEJBComponentMenuOptions(appname, moduleName, e.getKey(), monitorURL, handlerCtx);
                            menuList.addAll(compMenuOptions);
                        }
                    }
                }
            }
            List resMenuOptions = getAppScopedResourcesMenuOptions(appname, moduleName, monitorURL, handlerCtx);
            if (resMenuOptions.size() > 0) {
                menuList.addAll(resMenuOptions);
            }
        }
        // Add Menu Options.
        jumpMenuOptions = (Option[]) menuList.toArray(new Option[menuList.size()]);
        handlerCtx.setOutputValue("ComponentList", jumpMenuOptions);
    }

    private static List getEJBComponentMenuOptions(String appname, String modulename, String compName, String monitorURL, HandlerContext handlerCtx) {
        String endpoint = monitorURL + "/applications/" + appname + "/" + modulename + "/" + compName;
        List compMenuList = new ArrayList();
        List menuList = new ArrayList();
        Set<String> compChildSet = null;
        try {
            if (appname.equals(modulename)) {
                endpoint = monitorURL + "/applications/" + appname + "/" + compName;
            }
            compChildSet = RestUtil.getChildMap(endpoint).keySet();
        } catch (Exception ex) {
            GuiUtil.getLogger().severe("Error in getEJBComponentMenuOptions ; \nendpoint = " + endpoint + "method=GET");
        }
        if (compChildSet != null) {
            for (String child : compChildSet) {
                Set<String> subCompChildSet = null;
                try {
                    subCompChildSet = RestUtil.getChildMap(endpoint + "/" + child).keySet();
                } catch (Exception ex) {
                    GuiUtil.getLogger().severe("Error in getEJBComponentMenuOptions ; \nendpoint = " + endpoint + "/" + child + "method=GET");
                }
                if ((subCompChildSet != null) && subCompChildSet.size() > 0) {
                    //For ex: bean-methods
                    OptionGroup childCompMenuOptions = getMenuOptions(new ArrayList(subCompChildSet), child, compName, true);
                    menuList.add(childCompMenuOptions);
                } else {
                    //For ex: bean-cache and bean-
                    compMenuList.add(child);
                }
            }
        }
        compMenuList.add(0, compName);
        OptionGroup compMenuOptions = getMenuOptions(compMenuList, compName, "", true);
        menuList.add(0, compMenuOptions);
        return menuList;
    }

    private static List getAppScopedResourcesMenuOptions(String appname, String modulename, String monitorURL, HandlerContext handlerCtx) {
        String endpoint = monitorURL + "/applications/" + appname + "/" + modulename + "/resources";
        List menuList = new ArrayList();
        Set<String> resChildSet = null;
        try {
            if (appname.equals(modulename)) {
                endpoint = monitorURL + "/applications/" + appname + "/resources";
            }
            resChildSet = RestUtil.getChildMap(endpoint).keySet();
        } catch (Exception ex) {
            GuiUtil.getLogger().severe("Error in getAppScopedResourcesMenuOptions ; \nendpoint = " + endpoint + "method=GET");
        }
        if (resChildSet != null && resChildSet.size() > 0) {
            OptionGroup childResMenuOptions = getMenuOptions(new ArrayList(resChildSet), "resources", "", true);
            menuList.add(childResMenuOptions);
        }
        return menuList;
    }

    private static List getWebComponentMenuOptions(String appname, String modulename, List vsList, String monitorURL, HandlerContext handlerCtx) {
        String endpoint = monitorURL + "/applications/" + appname;
        List menuList = new ArrayList();

        if (modulename != null && !modulename.trim().equals("") && !appname.equals(modulename)) {
            endpoint += "/" + modulename;
        }

        if (vsList != null) {
            ListIterator vl = vsList.listIterator();
            while (vl.hasNext()) {
                String name = (String) vl.next();
                try {
                    List servlets = new ArrayList(RestUtil.getChildMap(endpoint + "/" + name).keySet());
                    if (!servlets.isEmpty()) {
                        OptionGroup menuOptions = getMenuOptions(servlets, name, "", true);
                        menuList.add(menuOptions);
                    }
                } catch (Exception ex) {
                    GuiUtil.getLogger().severe("Error in getWebComponentMenuOptions ; \nendpoint = " + endpoint + "/" + name + "method=GET");
                }
            }
        }
        return menuList;
    }

    private static OptionGroup getMenuOptions(List values, String label, String label2, boolean addLabel) {
        if (values == null) {
            return null;
        }
        ArrayList nList = new ArrayList();
        Collections.sort(values);
        ListIterator nl = values.listIterator();
        while (nl.hasNext()) {
            String name = (String) nl.next();
            if (addLabel && label2.equals("")) {
                if (!label.equals(name)) {
                    nList.add(new Option(label + "/" + name, name));
                } else {
                    nList.add(new Option(name, name));
                }

            } else if (addLabel && !label2.equals("")) {
                nList.add(new Option(label2 + "/" + label + "/" + name, name));
            } else {
                nList.add(new Option(name, name));
            }
        }
        Option[] groupedOptions3 = (Option[]) nList.toArray(new Option[nList.size()]);
        OptionGroup jumpGroup3 = new OptionGroup();
        jumpGroup3.setLabel(label);
        jumpGroup3.setOptions(groupedOptions3);
        return jumpGroup3;
    }

    private Option[] jumpMenuOptions = null;

}

