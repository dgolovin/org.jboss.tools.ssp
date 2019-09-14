package org.jboss.tools.rsp.server.minishift.servertype.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.jboss.tools.rsp.api.RSPClient;
import org.jboss.tools.rsp.api.dao.CommandLineDetails;
import org.jboss.tools.rsp.api.dao.ServerActionRequest;
import org.jboss.tools.rsp.api.dao.ServerActionWorkflow;
import org.jboss.tools.rsp.api.dao.StartServerResponse;
import org.jboss.tools.rsp.api.dao.StringPrompt;
import org.jboss.tools.rsp.api.dao.WorkflowResponse;
import org.jboss.tools.rsp.eclipse.core.runtime.CoreException;
import org.jboss.tools.rsp.eclipse.core.runtime.IStatus;
import org.jboss.tools.rsp.eclipse.core.runtime.Status;
import org.jboss.tools.rsp.eclipse.debug.core.DebugException;
import org.jboss.tools.rsp.eclipse.debug.core.ILaunch;
import org.jboss.tools.rsp.eclipse.debug.core.model.IProcess;
import org.jboss.tools.rsp.secure.crypto.CryptoException;
import org.jboss.tools.rsp.server.minishift.impl.Activator;
import org.jboss.tools.rsp.server.minishift.servertype.IMinishiftServerAttributes;
import org.jboss.tools.rsp.server.spi.client.ClientThreadLocal;
import org.jboss.tools.rsp.server.spi.launchers.IServerStartLauncher;
import org.jboss.tools.rsp.server.spi.model.polling.IServerStatePoller;
import org.jboss.tools.rsp.server.spi.model.polling.IServerStatePoller.SERVER_STATE;
import org.jboss.tools.rsp.server.spi.servertype.IServer;
import org.jboss.tools.rsp.server.spi.servertype.IServerDelegate;
import org.jboss.tools.rsp.server.spi.servertype.IServerWorkingCopy;
import org.jboss.tools.rsp.server.spi.util.StatusConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CRCServerDelegate extends MinishiftServerDelegate {
	
	private static final String ACTION_SETUP_CRC_ID = "CRCServerDelegate.setupCRC";
	private static final String ACTION_SETUP_CRC_LABEL = "Run setup-crc";	
	
	public static final String CRC_START_LAUNCH_SHARED_DATA = "CRCServerDelegate.startLaunch";
	
	private static final Logger LOG = LoggerFactory.getLogger(MinishiftServerDelegate.class);
	
	private ILaunch startLaunch;
	
	public CRCServerDelegate(IServer server) {
		super(server);
	}
	
	@Override
	protected void fillActionList(List<ServerActionWorkflow> allActions) {
		super.fillActionList(allActions);

		// setup-crc
		WorkflowResponse setupCrcWorkflow = new WorkflowResponse();
		setupCrcWorkflow.setStatus(StatusConverter.convert(
				new Status(IStatus.INFO, Activator.BUNDLE_ID, ACTION_SETUP_CRC_LABEL)));
		ServerActionWorkflow setupCrcAction = new ServerActionWorkflow(
				ACTION_SETUP_CRC_ID, ACTION_SETUP_CRC_LABEL, setupCrcWorkflow);
		allActions.add(setupCrcAction);
	}
	
	@Override
	public WorkflowResponse executeServerAction(ServerActionRequest req) {
		if( req != null && ACTION_SETUP_CRC_ID.equals(req.getActionId() )) {
			return runSetupCrc(req);
		}
		return super.executeServerAction(req);
	}
	
	protected WorkflowResponse runSetupCrc(ServerActionRequest req) {
		try {
			ILaunch launch = new SetupCRCLauncher(this).launch("run");
			registerLaunch(launch);
			return okWorkflowResponse();
		} catch(CoreException ce) {
			WorkflowResponse resp = new WorkflowResponse();
			resp.setStatus(StatusConverter.convert(new Status(
					IStatus.ERROR, Activator.BUNDLE_ID, 
					"Error running setup-cdk: " + ce.getMessage(), ce)));
			resp.setItems(new ArrayList<>());
			return resp;
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
			IServerStartLauncher launcher = getStartLauncher();
			startLaunch = launcher.launch(mode);
			launchedDetails = launcher.getLaunchedDetails();
			setStartLaunch(startLaunch);
			registerLaunch(startLaunch);
		} catch(CoreException ce) {
			if( startLaunch != null ) {
				IProcess[] processes = startLaunch.getProcesses();
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

	
	protected IServerStartLauncher getStartLauncher() {
		return new StartCRCLauncher(this);
	}
	
	@Override
	public void setDefaults(IServerWorkingCopy server) {
		server.setAttribute(STARTUP_PROGRAM_ARGS_STRING, "--pull-secret-file");
		HashMap<String,String> tmp = new HashMap<>();
		server.setAttribute(STARTUP_ENV_VARS_MAP, tmp);
	}
	
	protected IServerStatePoller getPoller(IServerStatePoller.SERVER_STATE expectedState) {
		return getCRCStatusPoller();
	}
	
	private IServerStatePoller getCRCStatusPoller() {
		IServerStatePoller poller = new CRCStatusPoller();
		return poller;
	}
	
	@Override
	protected void processTerminated(IProcess p) { 	
		// Time to poll to check the state
		IServerStatePoller poller = getCRCStatusPoller();
		SERVER_STATE state = poller.getCurrentStateSynchronous(getServer());
		if( state == SERVER_STATE.UP) {
			setServerState(IServerDelegate.STATE_STARTED);
		} else {
			setMode(null);
			setServerState(IServerDelegate.STATE_STOPPED);
		}
		
		fireServerProcessTerminated(getProcessId(p));
	}
	
	protected ILaunch getStartLaunch() {
		return (ILaunch)getSharedData(CRC_START_LAUNCH_SHARED_DATA);
	}
	
	protected void setStartLaunch(ILaunch launch) {
		putSharedData(CRC_START_LAUNCH_SHARED_DATA, launch);
	}
	
	public String getPullSecret() {
		// This step should only be done if this createServerDelegate is called
		// as part of a user workflow in the context of a call from a client.
		RSPClient rspc = ClientThreadLocal.getActiveClient();
		if( rspc == null )
			return null;
				
		try {
			String msg = "Please provide the path where the image pull secret is stored."; 
			StringPrompt prompt = new StringPrompt(100, msg, true);
			int tries = 0;
			int maxTries = 10;
			while(tries < maxTries) {
				String pullSecret = rspc.promptString(prompt).get();
				if( pullSecret != null && pullSecret.length() != 0 && pullSecret.trim().length() != 0) {
					return pullSecret;
				}
				tries += 1;
			}			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
		
	}
}