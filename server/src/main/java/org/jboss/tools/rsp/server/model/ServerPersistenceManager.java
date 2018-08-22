package org.jboss.tools.rsp.server.model;

import java.io.IOException;

import org.jboss.tools.rsp.api.dao.DiscoveryPath;
import org.jboss.tools.rsp.eclipse.jdt.launching.IVMInstall;
import org.jboss.tools.rsp.eclipse.jdt.launching.IVMInstallChangedListener;
import org.jboss.tools.rsp.eclipse.jdt.launching.PropertyChangeEvent;
import org.jboss.tools.rsp.launching.LaunchingCore;
import org.jboss.tools.rsp.server.ServerManagementServerLauncher;
import org.jboss.tools.rsp.server.spi.discovery.IDiscoveryPathListener;

public class ServerPersistenceManager implements IDiscoveryPathListener, IVMInstallChangedListener {

	private ServerManagementServerLauncher serverLauncher;
	public ServerPersistenceManager(ServerManagementServerLauncher serverLauncher) {
		this.serverLauncher = serverLauncher; 
		serverLauncher.getModel().getDiscoveryPathModel().addListener(this);
		
	}
	@Override
	public void discoveryPathAdded(DiscoveryPath path) {
		persisteDiscoveryPaths();
	}
	@Override
	public void discoveryPathRemoved(DiscoveryPath path) {
		persisteDiscoveryPaths();
	}
	@Override
	public void defaultVMInstallChanged(IVMInstall previous, IVMInstall current) {
		persistVms();
	}
	@Override
	public void vmChanged(PropertyChangeEvent event) {
		persistVms();
	}
	@Override
	public void vmAdded(IVMInstall vm) {
		persistVms();
	}
	@Override
	public void vmRemoved(IVMInstall vm) {
		persistVms();
	}
	
	private void persistVms() {
		try {
			this.serverLauncher.saveVMs();
		} catch (IOException e) {
			LaunchingCore.log(e);
		}
	}
	
	private void persisteDiscoveryPaths() {
		try {
			serverLauncher.saveDiscoveryPaths();
		} catch (IOException e) {
			LaunchingCore.log(e);
		}
	}
}
