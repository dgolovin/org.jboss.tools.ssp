/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.wildfly.servertype.impl;

import org.jboss.tools.rsp.eclipse.core.runtime.CoreException;
import org.jboss.tools.rsp.eclipse.core.runtime.IPath;
import org.jboss.tools.rsp.eclipse.core.runtime.Path;
import org.jboss.tools.rsp.eclipse.debug.core.ILaunch;
import org.jboss.tools.rsp.server.spi.launchers.IServerShutdownLauncher;
import org.jboss.tools.rsp.server.spi.servertype.IServer;
import org.jboss.tools.rsp.server.spi.servertype.IServerDelegate;
import org.jboss.tools.rsp.server.wildfly.servertype.AbstractJBossServerDelegate;
import org.jboss.tools.rsp.server.wildfly.servertype.AbstractLauncher;
import org.jboss.tools.rsp.server.wildfly.servertype.IJBossServerAttributes;
import org.jboss.tools.rsp.server.wildfly.servertype.JBossVMRegistryDiscovery;
import org.jboss.tools.rsp.server.wildfly.servertype.launch.IDefaultLaunchArguments;
import org.jboss.tools.rsp.server.wildfly.servertype.launch.Java9LaunchArgUtil;

public class WildFlyStopLauncher extends AbstractLauncher implements IServerShutdownLauncher{
	public WildFlyStopLauncher(IServerDelegate jBossServerDelegate) {
		super(jBossServerDelegate);
	}

	public ILaunch launch(boolean force) throws CoreException {
		IServerDelegate delegate = getDelegate();
		ILaunch launch = (ILaunch) delegate.getSharedData(AbstractJBossServerDelegate.START_LAUNCH_SHARED_DATA);
		if( force && terminateProcesses(launch)) {
			return null;
		}
		return launch("run");
	}

	protected String getWorkingDirectory() {
		String serverHome = getDelegate().getServer().getAttribute(IJBossServerAttributes.SERVER_HOME, (String) null);
		return serverHome + "/bin";
	}

	protected String getMainTypeName() {
		return "org.jboss.modules.Main";
	}

	protected String[] getClasspath() {
		String serverHome = getDelegate().getServer().getAttribute(IJBossServerAttributes.SERVER_HOME, (String) null);
		IPath home = new Path(serverHome).append("jboss-modules.jar");
		return new String[] { home.toOSString() };
	}

	protected String getVMArguments() {
		return Java9LaunchArgUtil.getJava9VMArgs(new JBossVMRegistryDiscovery().findVMInstall(getDelegate()));
	}

	protected String getProgramArguments() {
		IDefaultLaunchArguments largs = getLaunchArgs();
		if( largs != null ) {
			return largs.getDefaultStopArgs();
		}
		return "";
	}

	@Override
	public IServer getServer() {
		return getDelegate().getServer();
	}
}
