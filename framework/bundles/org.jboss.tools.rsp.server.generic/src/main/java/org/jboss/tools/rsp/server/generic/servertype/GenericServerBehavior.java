/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.generic.servertype;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.tools.rsp.api.DefaultServerAttributes;
import org.jboss.tools.rsp.api.ServerManagementAPIConstants;
import org.jboss.tools.rsp.api.dao.Attributes;
import org.jboss.tools.rsp.api.dao.CommandLineDetails;
import org.jboss.tools.rsp.api.dao.DeployableReference;
import org.jboss.tools.rsp.api.dao.LaunchParameters;
import org.jboss.tools.rsp.api.dao.ListServerActionResponse;
import org.jboss.tools.rsp.api.dao.ServerActionRequest;
import org.jboss.tools.rsp.api.dao.ServerAttributes;
import org.jboss.tools.rsp.api.dao.ServerStartingAttributes;
import org.jboss.tools.rsp.api.dao.StartServerResponse;
import org.jboss.tools.rsp.api.dao.WorkflowResponse;
import org.jboss.tools.rsp.eclipse.core.runtime.CoreException;
import org.jboss.tools.rsp.eclipse.core.runtime.IStatus;
import org.jboss.tools.rsp.eclipse.core.runtime.Status;
import org.jboss.tools.rsp.eclipse.debug.core.DebugException;
import org.jboss.tools.rsp.eclipse.debug.core.ILaunch;
import org.jboss.tools.rsp.eclipse.debug.core.model.IProcess;
import org.jboss.tools.rsp.launching.memento.JSONMemento;
import org.jboss.tools.rsp.server.discovery.serverbeans.ServerBeanLoader;
import org.jboss.tools.rsp.server.generic.GenericServerActivator;
import org.jboss.tools.rsp.server.generic.IPublishControllerWithOptions;
import org.jboss.tools.rsp.server.generic.servertype.launch.GenericJavaLauncher;
import org.jboss.tools.rsp.server.generic.servertype.launch.NoOpLauncher;
import org.jboss.tools.rsp.server.generic.servertype.launch.TerminateShutdownLauncher;
import org.jboss.tools.rsp.server.model.AbstractServerDelegate;
import org.jboss.tools.rsp.server.spi.launchers.IServerShutdownLauncher;
import org.jboss.tools.rsp.server.spi.launchers.IServerStartLauncher;
import org.jboss.tools.rsp.server.spi.model.polling.IPollResultListener;
import org.jboss.tools.rsp.server.spi.model.polling.IServerStatePoller;
import org.jboss.tools.rsp.server.spi.model.polling.IServerStatePoller.SERVER_STATE;
import org.jboss.tools.rsp.server.spi.model.polling.PollThreadUtils;
import org.jboss.tools.rsp.server.spi.model.polling.WebPortPoller;
import org.jboss.tools.rsp.server.spi.servertype.CreateServerValidation;
import org.jboss.tools.rsp.server.spi.servertype.IServer;
import org.jboss.tools.rsp.server.spi.servertype.IServerDelegate;
import org.jboss.tools.rsp.server.spi.servertype.IServerType;
import org.jboss.tools.rsp.server.spi.servertype.IServerWorkingCopy;
import org.jboss.tools.rsp.server.spi.util.StatusConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericServerBehavior extends AbstractServerDelegate {
	private static final Logger LOG = LoggerFactory.getLogger(GenericServerBehavior.class);
	public static final String START_LAUNCH_SHARED_DATA = "GenericServerBehavior.startLaunch";

	private JSONMemento behaviorMemento;
	private IPublishControllerWithOptions publishController;

	public GenericServerBehavior(IServer server, JSONMemento behaviorMemento) {
		super(server);
		this.behaviorMemento = behaviorMemento;
		setServerState(IServerDelegate.STATE_STOPPED);
	}

	protected ILaunch getStartLaunch() {
		return (ILaunch)getSharedData(START_LAUNCH_SHARED_DATA);
	}
	
	protected void setStartLaunch(ILaunch launch) {
		putSharedData(START_LAUNCH_SHARED_DATA, launch);
	}
	
	@Override
	public CommandLineDetails getStartLaunchCommand(String mode, ServerAttributes params) {
		try {
			return getStartLauncher().getLaunchCommand(mode);
		} catch(CoreException ce) {
			LOG.error(ce.getMessage(), ce);
			return null;
		}
	}

	@Override
	public StartServerResponse start(String mode) {
		IStatus stat = canStart(mode);
		if( !stat.isOK()) {
			org.jboss.tools.rsp.api.dao.Status s = StatusConverter.convert(stat);
			return new StartServerResponse(s, null);
		}
		
		setMode(mode);
		setServerState(IServerDelegate.STATE_STARTING);
		
		CommandLineDetails launchedDetails = null;
		try {
			launchPoller(IServerStatePoller.SERVER_STATE.UP);
			IServerStartLauncher launcher = getStartLauncher();
			ILaunch startLaunch2 = launcher.launch(mode);
			launchedDetails = launcher.getLaunchedDetails();
			if( startLaunch2 != null ) {
				setStartLaunch(startLaunch2);
				registerLaunch(startLaunch2);
			}
		} catch(CoreException ce) {
			if( getStartLaunch() != null ) {
				IProcess[] processes = getStartLaunch().getProcesses();
				for( int i = 0; i < processes.length; i++ ) {
					try {
						processes[i].terminate();
					} catch(DebugException de) {
						LOG.error(de.getMessage(), de);
					}
				}
			}
			setServerState(IServerDelegate.STATE_STOPPED);
			org.jboss.tools.rsp.api.dao.Status s = StatusConverter.convert(ce.getStatus());
			return new StartServerResponse(s, launchedDetails);
		}
		return new StartServerResponse(StatusConverter.convert(Status.OK_STATUS), launchedDetails);
	}

	
	@Override
	public IStatus stop(boolean force) {
		setServerState(IServerDelegate.STATE_STOPPING);
		ILaunch stopLaunch = null;
		launchPoller(IServerStatePoller.SERVER_STATE.DOWN);
		try {
			stopLaunch = getStopLauncher().launch(force);
			if( stopLaunch != null)
				registerLaunch(stopLaunch);
		} catch(CoreException ce) {
			// Dead code... but I feel it's not dead?  idk :( 
//			if( stopLaunch != null ) {
//				IProcess[] processes = startLaunch.getProcesses();
//				for( int i = 0; i < processes.length; i++ ) {
//					try {
//						processes[i].terminate();
//					} catch(DebugException de) {
//						LaunchingCore.log(de);
//					}
//				}
//			}
			setServerState(IServerDelegate.STATE_STARTED);
			return ce.getStatus();
		}
		return Status.OK_STATUS;

	}
	private IServerStartLauncher getStartLauncher() {
		JSONMemento startupMemento = behaviorMemento.getChild("startup");
		// TODO casting is dumb. Should be smarter than this
		return (IServerStartLauncher)getLauncher(startupMemento);
	}
	protected IServerShutdownLauncher getStopLauncher() {
		JSONMemento shutdownMemento = behaviorMemento.getChild("shutdown");
		return getLauncher(shutdownMemento);
	}
	public JSONMemento getActionsJSON() {
		return behaviorMemento.getChild("actions");
	}
	
	@Override
	public CreateServerValidation validate() {
		CreateServerValidation stat = validateServerHome(getServer());
		if( stat != null && stat.getStatus() != null && stat.getStatus().getSeverity() != IStatus.OK)
			return stat;

		return new CreateServerValidation(Status.OK_STATUS, new ArrayList<String>());
	}

	private String getServerHomeKey() {
		String path = getServer().getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String)null);
		if( path != null )
			return DefaultServerAttributes.SERVER_HOME_DIR;

		path = getServer().getAttribute(DefaultServerAttributes.SERVER_HOME_FILE, (String)null);
		if( path != null )
			return DefaultServerAttributes.SERVER_HOME_FILE;
		return null;
	}
	
	private CreateServerValidation validateServerHome(IServer server) {
		String validationType = server.getAttribute("server.home.validation", "discovery");
		// Should never happen. If it does, just don't validate I guess
		if( validationType == null )
			return null;
		
		if( "discovery".equals(validationType)) {
			return validateServerHomeDiscovery(getServer());
		}
		
		if( "isFolder".equals(validationType)) {
			return validateServerHomeFolderExists(getServer());
		}
		return null;
	}
	
	private String findServerHome(IServer server) {
		String key = getServerHomeKey();
		return key == null ? null : server.getAttribute(key, (String)null); // Should not be null
	}
	
	private CreateServerValidation validateServerHomeFolderExists(IServer server) {
		IStatus failedStat = new Status(IStatus.ERROR, GenericServerActivator.BUNDLE_ID, "Server type not found at given server home");
		String path = findServerHome(server);
		if( path == null ) {
			return new CreateServerValidation(failedStat, Arrays.asList(getServerHomeKey()));
		}
		if( !(new File(path).exists())) {
			return new CreateServerValidation(failedStat, Arrays.asList(getServerHomeKey()));
		}
		if( !(new File(path).isDirectory())) {
			return new CreateServerValidation(failedStat, Arrays.asList(getServerHomeKey()));
		}
		return null;
	}
	
	private CreateServerValidation validateServerHomeDiscovery(IServer server) {
		IStatus failedStat = new Status(IStatus.ERROR, GenericServerActivator.BUNDLE_ID, "Server type not found at given server home");

		String path = findServerHome(server);
		if( path == null ) {
			return new CreateServerValidation(failedStat, Arrays.asList(getServerHomeKey()));
		}

		ServerBeanLoader sbl = new ServerBeanLoader(new File(path), getServer().getServerManagementModel());
		String foundType = sbl.getServerAdapterId();
		if( !getServer().getServerType().getId().equals(foundType))  {
			return new CreateServerValidation(failedStat, Arrays.asList(getServerHomeKey()));
		}
		return null;
	}

	protected IServerShutdownLauncher getLauncher(JSONMemento memento) {
		String launchType = memento.getString("launchType");
		if( "java-launch".equals(launchType)) {
			return new GenericJavaLauncher(this, memento);
		}
		if( "terminateProcess".equals(launchType)) {
			ILaunch startLaunch = getStartLaunch();
			return new TerminateShutdownLauncher(this, startLaunch);
		}
		if( "noOp".equals(launchType)) {
			return new NoOpLauncher(this);
		}
		return null;
	}

	
	@Override
	public IStatus clientSetServerStarting(ServerStartingAttributes attr) {
		setServerState(STATE_STARTING, true);
		if( attr.isInitiatePolling()) {
			launchPoller(IServerStatePoller.SERVER_STATE.UP);
		}
		return Status.OK_STATUS;
	}

	private void launchPoller(SERVER_STATE upOrDown) {
		if( upOrDown == IServerStatePoller.SERVER_STATE.UP) {
			JSONMemento startupMemento = behaviorMemento.getChild("startup");
			String poller = startupMemento.getString("poller");
			if( poller != null && !poller.isEmpty()) {
				launchPoller(upOrDown, poller, startupMemento);
			}
		} else if( upOrDown == IServerStatePoller.SERVER_STATE.DOWN) {
			JSONMemento shutdownMemento = behaviorMemento.getChild("shutdown");
			String poller = shutdownMemento.getString("poller");
			if( poller != null && !poller.isEmpty()) {
				launchPoller(upOrDown, poller, shutdownMemento);
			}
		}
		
	}

	private void launchPoller(SERVER_STATE upOrDown, String pollerId, JSONMemento startupMemento) {
		// TODO eventually break this out
		if("automaticSuccess".equals(pollerId)) {
			if( upOrDown == IServerStatePoller.SERVER_STATE.UP)
				setServerState(STATE_STARTED, true);
			if( upOrDown == IServerStatePoller.SERVER_STATE.DOWN)
				setServerState(STATE_STOPPED, true);
			return;
		}
		
		if("webPoller".equals(pollerId)) {
			JSONMemento props = startupMemento.getChild("pollerProperties");
			if( props != null ) {
				String url = props.getString("url");
				IPollResultListener listener = upOrDown == IServerStatePoller.SERVER_STATE.DOWN ? 
						shutdownServerResultListener() : launchServerResultListener();
				WebPortPoller toRun = new WebPortPoller("Web Poller: " + this.getServer().getName()) {
					@Override
					protected String getURL(IServer server) {
						return url;
					}
				};
				PollThreadUtils.pollServer(getServer(), upOrDown, toRun, listener);
			}
			return;
		}
		if("noOpPoller".equals(pollerId)) {
			return;
		}
	}

	@Override
	public IStatus clientSetServerStarted(LaunchParameters attr) {
		setServerState(STATE_STARTED, true);
		return Status.OK_STATUS;
	}
	
	
	
	/*
	 * Publishing
	 */
	protected IPublishControllerWithOptions getPublishController() {
		if( publishController == null ) {
			JSONMemento publishMemento = behaviorMemento.getChild("publish");
			String deployPath = publishMemento.getString("deployPath");
			String approvedSuffixes = publishMemento.getString("approvedSuffixes");
			String[] suffixes = approvedSuffixes == null ? null : approvedSuffixes.split(",", -1);
			String supportsExploded = publishMemento.getString("supportsExploded");
			boolean exploded = (supportsExploded == null ? false : Boolean.parseBoolean(supportsExploded));
			this.publishController = new GenericServerSuffixPublishController(
					getServer(), this, 
					suffixes, deployPath, exploded);
		}
		return publishController;
	}
		
	@Override
	public IStatus canAddDeployable(DeployableReference ref) {
		return getPublishController().canAddDeployable(ref);
	}
	
	@Override
	public IStatus canRemoveDeployable(DeployableReference reference) {
		return getPublishController().canRemoveDeployable(getServerPublishModel().fillOptionsFromCache(reference));
	}
	
	@Override
	public IStatus canPublish() {
		return getPublishController().canPublish();
	}

	@Override
	protected void publishStart(int publishType) throws CoreException {
		getPublishController().publishStart(publishType);
	}

	@Override
	protected void publishFinish(int publishType) throws CoreException {
		getPublishController().publishFinish(publishType);
		super.publishFinish(publishType);
	}

	@Override
	public Attributes listDeploymentOptions() {
		return getPublishController().listDeploymentOptions();
	}

	
	@Override
	protected void publishDeployable(DeployableReference reference, 
			int publishRequestType, int modulePublishState) throws CoreException {
		int syncState = getPublishController()
				.publishModule(reference, publishRequestType, modulePublishState);
		setDeployablePublishState(reference, syncState);
		
		// TODO launch a module poller?!
		setDeployableState(reference, ServerManagementAPIConstants.STATE_STARTED);
	}

	@Override
	protected void processTerminated(IProcess p) {
		ILaunch l = p.getLaunch();
		if( l == getStartLaunch() ) {
			JSONMemento startup = behaviorMemento.getChild("startup");
			if( startup != null ) {
				String action = startup.getString("onProcessTerminated");
				if( action != null ) {
					handleOnProcessTerminated(p, action);
				}
			}
		}
		fireServerProcessTerminated(getProcessId(p));
	}

	private void handleOnProcessTerminated(IProcess p, String action) {
		if( "setServerStateStopped".equals(action)) {
			setMode(null);
			setStartLaunch(null);
			setServerState(IServerDelegate.STATE_STOPPED);
		}
		if( "setServerStateStarted".equals(action)) {
			setMode(null);
			setStartLaunch(null);
			setServerState(IServerDelegate.STATE_STARTED);
		}
	}

	public void setServerState(int state) {
		super.setServerState(state);
	}

	public void setServerState(int state, boolean fire) {
		super.setServerState(state, fire);
	}
	
	public String getPollURL(IServer server) {
		JSONMemento startupMemento = behaviorMemento.getChild("startup");
		if (startupMemento != null) {
			JSONMemento props = startupMemento.getChild("pollerProperties");
			if( props != null ) {
				String url = props.getString("url");
				return url;
			}
		}		
		return null;
	}
	
	protected GenericServerActionSupport getServerActionSupport() {
		return new GenericServerActionSupport(this, behaviorMemento);
	}
	
	@Override
	public ListServerActionResponse listServerActions() {
		return getServerActionSupport().listServerActions();
	}
	
	@Override
	public WorkflowResponse executeServerAction(ServerActionRequest req) {
		return getServerActionSupport().executeServerAction(req);
	}

	@Override
	public void setDefaults(IServerWorkingCopy server) {
		IServerType st = getServer().getServerType();
		if( st instanceof GenericServerType) {
			Map<String, Object> m = ((GenericServerType)st).getDefaults();
			for(Entry<String,Object> s : m.entrySet()) {
				Object val = s.getValue();
				if( val instanceof String )
					server.setAttribute(s.getKey(), (String)val);
				else if( val instanceof Boolean )
					server.setAttribute(s.getKey(), (Boolean)val);
				else if( val instanceof Integer)
					server.setAttribute(s.getKey(), (Integer)val);
			}
		}
	}

}
