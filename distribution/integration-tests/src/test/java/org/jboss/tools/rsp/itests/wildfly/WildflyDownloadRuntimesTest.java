/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.itests.wildfly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.tools.rsp.api.ServerManagementAPIConstants;
import org.jboss.tools.rsp.api.dao.DownloadRuntimeDescription;
import org.jboss.tools.rsp.api.dao.DownloadSingleRuntimeRequest;
import org.jboss.tools.rsp.api.dao.JobProgress;
import org.jboss.tools.rsp.api.dao.ListDownloadRuntimeResponse;
import org.jboss.tools.rsp.api.dao.ServerHandle;
import org.jboss.tools.rsp.api.dao.ServerState;
import org.jboss.tools.rsp.api.dao.Status;
import org.jboss.tools.rsp.api.dao.WorkflowResponse;
import org.jboss.tools.rsp.itests.RSPCase;
import org.jboss.tools.rsp.itests.util.RSPServerUtility;
import org.jboss.tools.rsp.itests.util.WaitCondition;
import org.junit.After;
import org.junit.Test;

public class WildflyDownloadRuntimesTest extends RSPCase {
	private static final String WILDFLY_RUNTIME_ID = "wildfly-1801finalruntime";
	private static final String WILDFLY_DOWNLOADED_ID = "wildfly-18.0.1.Final";
	
	private static final String DOWNLOAD_REQUEST_EMPTY = "Unable to find an executor for the given download runtime";
	private static final String INFO_REQUIRED_MESSAGE = "Please fill the requried information";
	private static final String WORKFLOW_LICENSE = "workflow.license";
	private static final String WORKFLOW_LICENSE_URL = "workflow.license.url";
	private static final String WORKFLOW_LICENSE_SIGN = "workflow.license.sign";
	private static final String DOWNLOAD_IN_PROGRESS = "Download In Progress";

	@After
	public void after() throws Exception {
		deleteServer(WILDFLY_DOWNLOADED_ID);
	}
	
	@Test
	public void testListDownloadableRuntimes() throws InterruptedException, ExecutionException, TimeoutException {
		ListDownloadRuntimeResponse response = serverProxy.listDownloadableRuntimes().get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response);
		assertNotNull(response.getRuntimes().size() > 1);
		assertTrue(response.getRuntimes().get(0) instanceof DownloadRuntimeDescription);
		assertNotNull(getRuntimeById(response.getRuntimes(), WILDFLY_RUNTIME_ID));
	}
	
	@Test
	public void testElementFromDownloadableRuntimes() throws InterruptedException, ExecutionException, TimeoutException {
		ListDownloadRuntimeResponse response = serverProxy.listDownloadableRuntimes().get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response);
		List<DownloadRuntimeDescription> runtimes = response.getRuntimes();
		DownloadRuntimeDescription rtm = getRuntimeById(runtimes, WILDFLY_RUNTIME_ID);
		assertTrue(rtm.getName().contains("WildFly 18.0.1 Final"));
		assertTrue(rtm.getHumanUrl().contains("http"));
		assertTrue(rtm.getSize().equals("?"));
	}

	@Test
	public void testInvalidDownloadRuntime() throws InterruptedException, ExecutionException, TimeoutException {
		DownloadSingleRuntimeRequest request = new DownloadSingleRuntimeRequest();
		WorkflowResponse response = serverProxy.downloadRuntime(request).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals(0, response.getItems().size());
		assertEquals(DOWNLOAD_REQUEST_EMPTY, response.getStatus().getMessage());
	}
	
	@Test
	public void testNullDownloadRuntime() throws InterruptedException, ExecutionException, TimeoutException {
		WorkflowResponse response = serverProxy.downloadRuntime(null).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response);
		assertEquals(Status.ERROR, response.getStatus().getSeverity());
		assertEquals(NULL_SERVER_ACTION_REQUEST, response.getStatus().getMessage());
	}
	
	@Test
	public void testDownloadRuntime() throws InterruptedException, ExecutionException, TimeoutException {
		// prepare request for downloading specific runtime
		DownloadSingleRuntimeRequest request = new DownloadSingleRuntimeRequest();
		request.setDownloadRuntimeId(WILDFLY_RUNTIME_ID);
		WorkflowResponse response = serverProxy.downloadRuntime(request).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response);
		// we expects info status of the request
		assertEquals(Status.INFO, response.getStatus().getSeverity());
		assertEquals(INFO_REQUIRED_MESSAGE, response.getStatus().getMessage());
		// we need to provide other information in order to construct valid request and send it
		DownloadSingleRuntimeRequest request2 = new DownloadSingleRuntimeRequest();
		HashMap<String, Object> data = new HashMap<>();
		request2.setDownloadRuntimeId(request.getDownloadRuntimeId());
		request2.setData(data);
		WorkflowResponse response2 = serverProxy.downloadRuntime(request2).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response2);
		// we expects info status of the request, we need to provide confirmation to license agreement, etc.
		assertEquals(Status.INFO, response2.getStatus().getSeverity());
		assertEquals(INFO_REQUIRED_MESSAGE, response.getStatus().getMessage());
		assertEquals(WORKFLOW_LICENSE, response2.getItems().get(0).getId());
		assertEquals(WORKFLOW_LICENSE_URL, response2.getItems().get(1).getId());
		assertEquals(WORKFLOW_LICENSE_SIGN, response2.getItems().get(2).getId());
		// prepare request that confirms licenses and is a parameter of runtime download process
		DownloadSingleRuntimeRequest request3 = new DownloadSingleRuntimeRequest();
		data.put(WORKFLOW_LICENSE_URL, "Continue...");
		data.put(WORKFLOW_LICENSE_SIGN, true);
		request3.setRequestId(response2.getRequestId());
		request3.setDownloadRuntimeId(request2.getDownloadRuntimeId());
		request3.setData(data);
		// this response contains also information about job being started that processes download
		WorkflowResponse response3 = serverProxy.downloadRuntime(request3).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(response3);
		assertEquals(Status.OK, response3.getStatus().getSeverity());
		assertEquals(DOWNLOAD_IN_PROGRESS, response3.getStatus().getMessage());
		// get download job id
		String downloadJobId = response3.getJobId();
		assertNotNull(downloadJobId);
		// wait for downloading job to finish
		RSPServerUtility.waitFor(120000, new WaitCondition() {
			
			@Override
			public boolean test() {
				List<JobProgress> jobs = new ArrayList<>();
				try {
					jobs = serverProxy.getJobs().get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					e.printStackTrace();
				}
				JobProgress jobProgress = jobs.stream().filter(job -> job.getHandle().getId().equals(downloadJobId)).findAny().orElse(null);
				return jobProgress == null ? true : false;
			}
		});
		// check server state (for predefined server id of the downloaded server) 
		ServerState state = serverProxy.getServerState(new ServerHandle(WILDFLY_DOWNLOADED_ID, wildflyType)).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
		assertNotNull(state);
		assertEquals(ServerManagementAPIConstants.STATE_STOPPED, state.getState());
	}
	
	private DownloadRuntimeDescription getRuntimeById(List<DownloadRuntimeDescription> runtimes, String id) {
		return runtimes.stream().filter(runtime -> runtime.getId().equals("wildfly-1801finalruntime")).findAny().orElse(null);
	}
}
