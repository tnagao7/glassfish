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

package test.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;

import test.beans.TestBeanInterface;
import test.beans.artifacts.Preferred;
import test.beans.artifacts.ProducedViaProducerMethod;
import test.beans.artifacts.ProducedViaStaticField;
import test.util.JpaTest;

@WebServlet(name = "mytest", urlPatterns = { "/myurl" })
public class JPAResourceInjectionServletFromSingletonEJB extends HttpServlet {

    @PersistenceUnit(unitName = "pu1")
    private EntityManagerFactory emf;

    @Inject
    @ProducedViaProducerMethod
    private EntityManagerFactory emf_producer;
    
    @Inject
    @ProducedViaStaticField
    private EntityManagerFactory emf_static;
    

    private @Resource
    UserTransaction utx;
    
    @Inject
    @Preferred
    TestBeanInterface tbi;

    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException,
            IOException {
        PrintWriter writer = response.getWriter();
        writer.write("Hello from Servlet 3.0.");
        String msg = "";
        
        if (emf == null)
            msg += "Simple injection of EntityManagerFactory through " +
            		"@PersistenceUnit failed";
        String testcase = request.getParameter("testcase");
        System.out.println("testcase=" + testcase);
        
        String whichEMF = request.getParameter("whichemf");
        System.out.println("whichEMF=" + whichEMF);

        EntityManager em = null;
        if (whichEMF != null){
            EntityManagerFactory emf = null;
            if (whichEMF.equals("producer")){
                emf = emf_producer;
            } else if (whichEMF.equals("static")) {
                emf = emf_static;
            }
            //System.out.println("JPAResourceInjectionServlet::@PersistenceUnit " +
            //        "CDI EntityManagerFactory to run against=" + emf);
            em = emf.createEntityManager();
            System.out.println("JPAResourceInjectionServlet::createEM" +
                    "EntityManager=" + em);
        }
        
        if (testcase != null) {
            
            JpaTest jt = new JpaTest(em, utx);
            boolean status = false;
            if ("llinit".equals(testcase)) {
                status = jt.lazyLoadingInit();
            } else if ("llfind".equals(testcase)) {
                status = jt.lazyLoadingByFind(1);
            } else if ("llquery".equals(testcase)) {
                status = jt.lazyLoadingByQuery("Carla");
            } else if ("llinj".equals(testcase)){
                status = ((tbi != null) && 
                        (tbi.testDatasourceInjection().trim().length()==0));
            }
            
            if (status) {
                msg += "";// pass
            } else {
                msg += (testcase + ":fail");
            }
        }

        writer.write(msg + "\n");

    }
}
