/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.minishift.servertype.impl;

import java.util.HashMap;
import java.util.Map;

import org.jboss.tools.rsp.launching.utils.NativeEnvironmentUtils;
import org.jboss.tools.rsp.server.minishift.servertype.MinishiftPropertyUtility;
import org.jboss.tools.rsp.server.spi.servertype.IServer;

public class EnvironmentUtility {
	// Isolating duplicated code. Doesn't use IServer yet but will.
	private IServer server;
	public EnvironmentUtility(IServer server) {
		this.server = server;
	}
	
	public String[] getEnvironment() {
		return getEnvironment(true, true);
	}
	
	protected String[] getEnvironment(boolean appendNativeEnv, boolean appendCredentials) {
		Map<String, String> configEnv = null;
		if( appendCredentials ) 
			configEnv = getEnvironmentFromServer();
		else 
			configEnv = new HashMap<String,String>();
		
		return NativeEnvironmentUtils.getDefault().getEnvironment(configEnv, appendNativeEnv);
	}

	protected Map<String, String> getEnvironmentFromServer() {
		HashMap<String,String> ret = new HashMap<String, String>();
		String user = MinishiftPropertyUtility.getMinishiftUsername(server);
		String pass = MinishiftPropertyUtility.getMinishiftPassword(server);
		if( user != null && pass != null && !user.isEmpty() && !pass.isEmpty()) {
			ret.put("MINISHIFT_USERNAME", user);
			ret.put("MINISHIFT_PASSWORD", pass);
		}
		return ret;
	}
	
	
}
