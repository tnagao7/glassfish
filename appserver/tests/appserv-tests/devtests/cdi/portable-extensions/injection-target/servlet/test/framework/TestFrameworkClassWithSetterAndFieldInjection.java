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

package test.framework;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import test.beans.Preferred;
import test.beans.TestBean;
import test.beans.TestNamedBean;


public class TestFrameworkClassWithSetterAndFieldInjection {
    
    @Inject TestNamedBean tnb;

    private TestBean tb;
    
    private String msg = "";

    private boolean postConstructCalled = false;
    private boolean preDestroyCalled = false;

    // must have default no-arg constructor or weld will puke
    public TestFrameworkClassWithSetterAndFieldInjection() {
    }

    public TestFrameworkClassWithSetterAndFieldInjection(String magicKey){
        if(!magicKey.equals("test")) throw new RuntimeException();
    }
    
    
    @Inject
    public void setTestBean(@Preferred TestBean tb){
        System.out.println("Setter based injection " +
                "into a framework class" + tb);
        this.tb = tb;
        if (tb == null) {
            msg += "Constructor injection in a test framework class failed";
        }
        
    }
    
    @PostConstruct
    private void beanPostConstruct() {
        this.postConstructCalled = true;
        if (tnb == null) {
            msg += "regular field injection in a test framework class failed";
        }
    }

    @PreDestroy
    private void beanPreDestroy() {
        this.preDestroyCalled = true;
    }

    public String getInitialTestResults() {
        if (!postConstructCalled)
            msg += "PostConstruct was not called in test framework class";
        String response = msg;
        msg = "";
        return response;
    }

    public String getFinalTestResults() {
        if (!preDestroyCalled)
            msg += "PreDestroy was not called " + "in test framework class";
        return msg;
    }
}
