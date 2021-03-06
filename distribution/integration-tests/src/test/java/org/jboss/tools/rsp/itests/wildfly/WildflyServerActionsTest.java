/*******************************************************************************
 * Copyright (c) 2018-2019 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.itests.wildfly;

import static org.jboss.tools.rsp.itests.util.ServerStateUtil.waitForServerState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.tools.rsp.api.ServerManagementAPIConstants;
import org.jboss.tools.rsp.api.dao.LaunchParameters;
import org.jboss.tools.rsp.api.dao.ListServerActionResponse;
import org.jboss.tools.rsp.api.dao.ServerActionRequest;
import org.jboss.tools.rsp.api.dao.ServerActionWorkflow;
import org.jboss.tools.rsp.api.dao.ServerAttributes;
import org.jboss.tools.rsp.api.dao.ServerHandle;
import org.jboss.tools.rsp.api.dao.ServerStartingAttributes;
import org.jboss.tools.rsp.api.dao.ServerType;
import org.jboss.tools.rsp.api.dao.StartServerResponse;
import org.jboss.tools.rsp.api.dao.Status;
import org.jboss.tools.rsp.api.dao.StopServerAttributes;
import org.jboss.tools.rsp.api.dao.WorkflowResponse;
import org.jboss.tools.rsp.api.dao.WorkflowResponseItem;
import org.jboss.tools.rsp.itests.RSPCase;
import org.jboss.tools.rsp.itests.util.DummyClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for server actions
 * @author odockal
 *
 */
public class WildflyServerActionsTest extends RSPCase {

	private static final String SERVER_ID = "wildfly";
	private final DummyClient client = launcher.getClient();
	private static final String SHOWIN_ACTION_ID = "ShowInBrowserActionHandler.actionId";
	private static final String SHOWIN_ACTION_LABEL = "Show in browser...";
	private static final String EDITCONFIG_ACTION_ID = "EditServerConfigurationActionHandler.actionId";
	private static final String EDITCONFIG_ACTION_LABEL = "Edit Configuration File...";
	public static final String ACTION_SHOW_IN_BROWSER_SELECTED_PROMPT_ID = "ShowInBrowserActionHandler.selection.id";
	public static final String ACTION_SHOW_IN_BROWSER_SELECTED_PROMPT_LABEL = 
			"Which deployment do you want to show in the web browser?";
	public static final String ACTION_SHOW_IN_BROWSER_SELECT_SERVER_ROOT = "http://localhost:8080";
	
	private ServerHandle handle;

	@Before
	public void before() throws Exception {
		createServer(WILDFLY_ROOT, SERVER_ID);
		handle = new ServerHandle(SERVER_ID, wildflyType);
	}

	@After
	public void after() throws Exception {
		stopServer(client, SERVER_ID);
		deleteServer(SERVER_ID);
	}

	@Test
	public void testListServerActions() throws Exception {
		ListServerActionResponse response = serverProxy.listServerActions(handle).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.OK, response.getStatus().getSeverity());
		assertEquals(1, response.getWorkflows().size());
		ServerActionWorkflow workflow = response.getWorkflows().get(0);
		assertEquals(EDITCONFIG_ACTION_ID, workflow.getActionId());
		assertEquals(EDITCONFIG_ACTION_LABEL, workflow.getActionLabel());
	}

	@Test
	public void testListServerActionsStarted() throws Exception {
        LaunchParameters params = new LaunchParameters(
                new ServerAttributes(wildflyType.getId(), SERVER_ID, new HashMap<>()), MODE_RUN);
        StartServerResponse response = serverProxy.startServerAsync(params).get(SERVER_OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
        assertEquals(0, response.getStatus().getSeverity());
        assertEquals(STATUS_MESSAGE_OK, response.getStatus().getMessage());
        waitForServerState(ServerManagementAPIConstants.STATE_STARTED, 10, client);
        
		ListServerActionResponse actionResponse = serverProxy.listServerActions(handle).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.OK, actionResponse.getStatus().getSeverity());
		
		assertEquals(2, actionResponse.getWorkflows().size());
		ServerActionWorkflow workflow = actionResponse.getWorkflows().get(0);
		
		assertEquals(SHOWIN_ACTION_ID, workflow.getActionId());
		assertEquals(SHOWIN_ACTION_LABEL, workflow.getActionLabel());
		ServerActionWorkflow workflow2 = actionResponse.getWorkflows().get(1);
		assertEquals(EDITCONFIG_ACTION_ID, workflow2.getActionId());
		assertEquals(EDITCONFIG_ACTION_LABEL, workflow2.getActionLabel());

        serverProxy.stopServerAsync(new StopServerAttributes(SERVER_ID, true)).get();
        waitForServerState(ServerManagementAPIConstants.STATE_STOPPED, 10, client);
	}

	@Test
	public void testListServerActionsInvalidHandle() throws Exception {
		ServerHandle inHandle = new ServerHandle("foo.server.id",
				new ServerType("some.id", "my.server", "Random server type definition"));
		ListServerActionResponse response = serverProxy.listServerActions(inHandle).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals(SERVER_TYPE_NOT_FOUND, response.getStatus().getMessage());
		assertNull(response.getWorkflows());
		inHandle = new ServerHandle("foo.server.id", null);
		response = serverProxy.listServerActions(inHandle).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals(MISSING_SERVER_TYPE, response.getStatus().getMessage());
		assertNull(response.getWorkflows());
		inHandle = new ServerHandle("foo.server.id", new ServerType(null, "my.server", "Random server type definition"));
		response = serverProxy.listServerActions(inHandle).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals(MISSING_SERVER_TYPE_ID, response.getStatus().getMessage());
		assertNull(response.getWorkflows());
	}

	@Test
	public void testListServerActionsNullHandle() throws Exception {
		ListServerActionResponse response = serverProxy.listServerActions(null).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals(MISSING_SERVER_HANDLE, response.getStatus().getMessage());
		assertNull(response.getWorkflows());
	}

	@Test
	public void testExecuteServerAction() throws Exception {
		ServerActionRequest req = new ServerActionRequest();
		req.setServerId(SERVER_ID);
		req.setActionId(SHOWIN_ACTION_ID);
		Map<String, Object> data = new HashMap<>();
		data.put(ACTION_SHOW_IN_BROWSER_SELECTED_PROMPT_ID, ACTION_SHOW_IN_BROWSER_SELECT_SERVER_ROOT);
		req.setData(data);
		WorkflowResponse response = serverProxy.executeServerAction(req).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response);
		assertEquals(Status.OK, response.getStatus().getSeverity());
		assertNotNull(response.getItems());
		assertTrue(response.getItems().size() == 1);
		WorkflowResponseItem item = response.getItems().get(0);
		assertTrue(item.getContent().contains("localhost:8080"));
		assertEquals(ServerManagementAPIConstants.WORKFLOW_TYPE_OPEN_BROWSER, item.getItemType());
	}

	@Test
	public void testInvalidExecuteServerAction() throws Exception {
		ServerActionRequest req = new ServerActionRequest();
		req.setServerId(null);
		WorkflowResponse response = serverProxy.executeServerAction(req).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals(MISSING_SERVER_ID, response.getStatus().getMessage());
		assertNull(response.getItems());
		req.setActionId("fabricated.id");
		req.setServerId("nemo");
		response = serverProxy.executeServerAction(req).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals("Server nemo does not exist" , response.getStatus().getMessage());
		assertNull(response.getItems());
	}
	
	@Test
	public void testInvalidExecuteServerActionData() throws Exception {
		// no data send
		ServerActionRequest req = new ServerActionRequest();
		req.setServerId(SERVER_ID);
		req.setActionId(SHOWIN_ACTION_ID);
		WorkflowResponse response = serverProxy.executeServerAction(req).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.CANCEL, response.getStatus().getSeverity());
		assertEquals("" , response.getStatus().getMessage());
		assertTrue(response.getItems().isEmpty());
		// empty data map send
		req.setData(new HashMap<>());
		response = serverProxy.executeServerAction(req).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertEquals(Status.CANCEL, response.getStatus().getSeverity());
		assertEquals("" , response.getStatus().getMessage());
		assertTrue(response.getItems().isEmpty());
	}

	@Test
	public void testNullExecuteServerAction() throws Exception {
		WorkflowResponse response = serverProxy.executeServerAction(null).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals(NULL_SERVER_ACTION_REQUEST, response.getStatus().getMessage());
	}
}
