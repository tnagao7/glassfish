/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.s1asdev.jdbc.customval.validator;

import org.glassfish.api.jdbc.ConnectionValidation;
import java.sql.Connection;

public class MyValidator implements ConnectionValidation {

    public boolean isConnectionValid(Connection con) {
	boolean valid = false;
	try {
            valid = isValid(con, "select count(*) as COUNT from DUMMY");
	} catch(Exception ex) {
            ex.printStackTrace();
	}
	return valid;
    }

   /**
    * Checks if a <code>java.sql.Connection</codeis valid or not
    * by querying a table.
    *
    * @param con       <code>java.sql.Connection</code> to be validated
    * @param tableName table which should be queried
    * @throws ResourceException if the connection is not valid
    */
    protected boolean isValid(java.sql.Connection con,
	                                           String query) throws Exception{
	int count = 1;
	boolean valid = false;
        if (con == null) {
	    throw new Exception("The connection is not valid as "
			                        + "the connection is null");
	}

        java.sql.PreparedStatement stmt = null;
        java.sql.ResultSet rs = null;
        try {
	   stmt = con.prepareStatement(query);
	   rs = stmt.executeQuery();
           while(rs.next()) {
	       int resultCount = rs.getInt("COUNT");
	       if(count == resultCount) {
		   valid = true;
               } else {
                   System.out.println("Expected count [" + count +
                         "] does not match result[" + resultCount + "]");
		   valid = false;
               }
           }

	} catch (Exception sqle) {
	    throw new Exception(sqle);
        } finally {
            try {
                if (rs != null) {
                        rs.close();
                }
            } catch (Exception e1) {}

            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (Exception e2) {}
        }
	return valid;
    }
}    
