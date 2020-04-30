/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.generic.servertype.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.tools.rsp.api.ServerManagementAPIConstants;
import org.jboss.tools.rsp.api.dao.DeployableReference;
import org.jboss.tools.rsp.api.dao.DeployableState;
import org.jboss.tools.rsp.api.dao.ServerActionRequest;
import org.jboss.tools.rsp.api.dao.ServerActionWorkflow;
import org.jboss.tools.rsp.api.dao.WorkflowPromptDetails;
import org.jboss.tools.rsp.api.dao.WorkflowResponse;
import org.jboss.tools.rsp.api.dao.WorkflowResponseItem;
import org.jboss.tools.rsp.eclipse.core.runtime.CoreException;
import org.jboss.tools.rsp.eclipse.core.runtime.IStatus;
import org.jboss.tools.rsp.eclipse.core.runtime.Path;
import org.jboss.tools.rsp.eclipse.core.runtime.Status;
import org.jboss.tools.rsp.launching.memento.JSONMemento;
import org.jboss.tools.rsp.server.generic.impl.Activator;
import org.jboss.tools.rsp.server.generic.jee.WarDeploymentResourceLocator;
import org.jboss.tools.rsp.server.generic.servertype.GenericServerBehavior;
import org.jboss.tools.rsp.server.model.AbstractServerDelegate;
import org.jboss.tools.rsp.server.spi.util.StatusConverter;

public class ShowInBrowserActionHandler {
	public static final String ACTION_SHOW_IN_BROWSER_JSON_ID = "showInBrowser";
	public static final String ACTION_SHOW_IN_BROWSER_ID = "ShowInBrowserActionHandler.actionId";
	public static final String ACTION_SHOW_IN_BROWSER_LABEL = "Show in browser...";
	public static final String ACTION_SHOW_IN_BROWSER_SELECTED_PROMPT_ID = "ShowInBrowserActionHandler.selection.id";
	public static final String ACTION_SHOW_IN_BROWSER_SELECTED_PROMPT_LABEL = 
			"Which deployment do you want to show in the web browser?";
	public static final String ACTION_SHOW_IN_BROWSER_SELECT_SERVER_ROOT = "Welcome Page (Index)";
	
	public static final ServerActionWorkflow getInitialWorkflow(GenericServerBehavior genericServerBehavior2) {
		return new ShowInBrowserActionHandler(genericServerBehavior2).getInitialWorkflowInternal();
	}
	
	private GenericServerBehavior genericServerBehavior;
	public ShowInBrowserActionHandler(GenericServerBehavior genericServerBehavior) {
		this.genericServerBehavior = genericServerBehavior;
	}
	
	protected ServerActionWorkflow getInitialWorkflowInternal() {
		WorkflowResponse workflow = new WorkflowResponse();
		workflow.setStatus(StatusConverter.convert(
				new Status(IStatus.INFO, Activator.BUNDLE_ID, ACTION_SHOW_IN_BROWSER_LABEL)));
		ServerActionWorkflow action = new ServerActionWorkflow(
				ACTION_SHOW_IN_BROWSER_ID, ACTION_SHOW_IN_BROWSER_LABEL, workflow);
		
		// Initial prompt 
		List<WorkflowResponseItem> items = new ArrayList<>();
		WorkflowResponseItem item1 = new WorkflowResponseItem();
		item1.setItemType(ServerManagementAPIConstants.WORKFLOW_TYPE_PROMPT_SMALL);
		item1.setId(ACTION_SHOW_IN_BROWSER_SELECTED_PROMPT_ID);
		item1.setLabel(ACTION_SHOW_IN_BROWSER_SELECTED_PROMPT_LABEL);
		
		WorkflowPromptDetails prompt = new WorkflowPromptDetails();
		prompt.setResponseSecret(false);
		prompt.setResponseType(ServerManagementAPIConstants.ATTR_TYPE_STRING);
		
		List<String> deployments2 = new ArrayList<>();
		deployments2.add(ACTION_SHOW_IN_BROWSER_SELECT_SERVER_ROOT);
		
		List<DeployableState> dss = getDeployableStatesHavingContextRoots();
		String basedir = getBaseUrl();
		for( DeployableState ds : dss ) {
			String path = basedir + "/" + getContextRoot(ds);
			deployments2.add(path);
		}
		deployments2.addAll(fromResourceLocators());
		
		
		
		prompt.setValidResponses(deployments2);
		item1.setPrompt(prompt);
		
		items.add(item1);
		workflow.setItems(items);
		return action;
	}
	
	private List<String> fromResourceLocators() {
		String locator = getResourceLocator();
		if( locator == null || locator.isEmpty())
			return Collections.EMPTY_LIST;
		
		if( "jee".equals(locator)) {
			return findJeeResourcesFromDeployments();
		}
		return Collections.EMPTY_LIST;
	}

	private List<String> findJeeResourcesFromDeployments() {
		ArrayList<String> ret = new ArrayList<>();
		List<DeployableState> states = getDeployableStatesHavingContextRoots();
		for( DeployableState ds : states ) {
			String p = ds.getReference().getPath();
			String[] oneDep = WarDeploymentResourceLocator.findResources(p);
			String baseUrl = getBaseUrl();
			String contextRoot = getContextRoot(ds);
			for( String onedepString : oneDep ) {
				ret.add(baseUrl + "/" + contextRoot + "/" + onedepString);
			}
		}
		return ret;
	}

	private List<DeployableState> getDeployableStatesHavingContextRoots() {
		List<DeployableState> dss = getDeployableStates();
		ArrayList<DeployableState> collector = new ArrayList<>();
		for( DeployableState ds : dss ) {
			if( getContextRoot(ds) != null ) 
				collector.add(ds);
		}
		return collector;
	}
	
	protected List<DeployableState> getDeployableStates() {
		return genericServerBehavior.getServerPublishModel().getDeployableStatesWithOptions();
	}

	private String getContextRoot(DeployableState ds) {
		String strat = getDeploymentStrategy();
		String deployableOutputName = new Path(ds.getReference().getPath()).lastSegment();
		if( ds.getReference().getOptions() != null ) {
			String outputName = (String)ds.getReference().getOptions().get(ServerManagementAPIConstants.DEPLOYMENT_OPTION_OUTPUT_NAME);
			if( outputName != null ) 
				deployableOutputName = outputName;
		}
		if( "appendDeploymentNameRemoveSuffix".equals(strat)) {
			String depName = deployableOutputName;
			if (depName.indexOf(".") > 0)
				depName = depName.substring(0, depName.lastIndexOf("."));
			return depName;
		} else if( "appendDeploymentName".equals(strat)) {
			return deployableOutputName;
		}
		return null;
	}
	
	protected String getOutputName(DeployableReference ref) {
		Map<String, Object> options = ref.getOptions();
		String def = null;
		if( ref.getPath() != null ) {
			def = new File(ref.getPath()).getName();
		}
		String k = ServerManagementAPIConstants.DEPLOYMENT_OPTION_OUTPUT_NAME; 
		if( options != null && options.get(k) != null ) {
			return (String)options.get(k);
		}
		return def;
	}
	
	public WorkflowResponse handle(ServerActionRequest req) {
		if( req == null || req.getData() == null ) 
			return AbstractServerDelegate.cancelWorkflowResponse();
			
		String choice = (String)req.getData().get(ACTION_SHOW_IN_BROWSER_SELECTED_PROMPT_ID);
		if( choice == null )
			return AbstractServerDelegate.cancelWorkflowResponse();
		
		String url = findUrlFromChoice(choice);
		if( url != null ) {
			WorkflowResponseItem item = new WorkflowResponseItem();
			item.setItemType(ServerManagementAPIConstants.WORKFLOW_TYPE_OPEN_BROWSER);
			item.setLabel("Open the following url: " + url);
			item.setContent(url);
			WorkflowResponse resp = new WorkflowResponse();
			resp.setItems(Arrays.asList(item));
			resp.setStatus(StatusConverter.convert(Status.OK_STATUS));
			return resp;
		}
		return AbstractServerDelegate.cancelWorkflowResponse();
	}
	
	private String findUrlFromChoice(String choice) {
		String baseUrl = getBaseUrl();
		if( choice.trim().equals(ACTION_SHOW_IN_BROWSER_SELECT_SERVER_ROOT)) {
			return baseUrl;
		} if( choice.trim().startsWith("http")) {
			return choice.trim();
		} else {
			List<DeployableState> states = getDeployableStates();
			for( DeployableState ds : states ) {
				if( ds.getReference().getPath().equals(choice)) {
					String contextRoot = getContextRoot(ds);
					if( contextRoot != null ) {
						return baseUrl + "/" + contextRoot;
					}
				}
			}
		}
		return null;
	}
	
	protected String getBaseUrl() {
		JSONMemento mem = genericServerBehavior.getActionsJSON().getChild(ACTION_SHOW_IN_BROWSER_JSON_ID);
		String ret = mem.getString("baseUrl");
		try {
			ret = genericServerBehavior.applySubstitutions(ret);
		} catch(CoreException ce) {
			// TODO log
		}
		return ret;
	}

	protected String getDeploymentStrategy() {
		JSONMemento mem = genericServerBehavior.getActionsJSON().getChild(ACTION_SHOW_IN_BROWSER_JSON_ID);
		return mem.getString("deploymentStrategy");
	}
	

	protected String getResourceLocator() {
		JSONMemento mem = genericServerBehavior.getActionsJSON().getChild(ACTION_SHOW_IN_BROWSER_JSON_ID);
		return mem.getString("resourceLocator");
	}

}
