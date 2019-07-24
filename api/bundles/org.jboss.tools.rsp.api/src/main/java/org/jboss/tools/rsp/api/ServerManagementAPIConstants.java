/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.api;

public interface ServerManagementAPIConstants extends DefaultServerAttributes {

	public static final int STREAM_TYPE_SYSERR = 1;
	public static final int STREAM_TYPE_SYSOUT = 2;
	public static final int STREAM_TYPE_OTHER = 3;
	
	
	
	// Used only for workflows?
	public static final String ATTR_TYPE_NONE = "none";
	
	public static final String ATTR_TYPE_BOOL = "bool";
	public static final String ATTR_TYPE_INT = "int";
	public static final String ATTR_TYPE_STRING= "string";
	public static final String ATTR_TYPE_LIST = "list";
	public static final String ATTR_TYPE_MAP = "map";
	
	public static final String WORKFLOW_TYPE_PROMPT_SMALL = "workflow.prompt.small";
	public static final String WORKFLOW_TYPE_PROMPT_LARGE = "workflow.prompt.large";
	public static final String WORKFLOW_TYPE_OPEN_EDITOR = "workflow.editor.open";	
	public static final String WORKFLOW_TYPE_OPEN_BROWSER = "workflow.browser.open";	


	public static final String WORKFLOW_EDITOR_PROPERTY_PATH = "workflow.editor.file.path";
	public static final String WORKFLOW_EDITOR_PROPERTY_CONTENT = "workflow.editor.file.content";
	
	
	
	/**
	 * Server state constant (value 0) indicating that the
	 * server is in an unknown state.
	 */
	public static final int STATE_UNKNOWN = 0;

	/**
	 * Server state constant (value 1) indicating that the
	 * server is starting, but not yet ready to serve content.
	 */
	public static final int STATE_STARTING = 1;

	/**
	 * Server state constant (value 2) indicating that the
	 * server is ready to serve content.
	 */
	public static final int STATE_STARTED = 2;

	/**
	 * Server state constant (value 3) indicating that the
	 * server is shutting down.
	 */
	public static final int STATE_STOPPING = 3;

	/**
	 * Server state constant (value 4) indicating that the
	 * server is stopped.
	 */
	public static final int STATE_STOPPED = 4;


	/**
	 * Publish state constant (value 1) indicating that there
	 * is no publish required.
	 */
	public static final int PUBLISH_STATE_NONE = 1;

	/**
	 * Publish state constant (value 2) indicating that an
	 * incremental publish is required.
	 */
	public static final int PUBLISH_STATE_INCREMENTAL = 2;

	/**
	 * Publish state constant (value 3) indicating that a
	 * full publish is required.
	 */
	public static final int PUBLISH_STATE_FULL = 3;
	
	/**
	 * Publish state constant (value 4) indicating that the
	 * deployable has yet to be added / deployed, and should be.
	 */
	public static final int PUBLISH_STATE_ADD = 4;

	/**
	 * Publish state constant (value 5) indicating that a
	 * removal of the deployable is required
	 */
	public static final int PUBLISH_STATE_REMOVE = 5;
	
	/**
	 * Publish state constant (value 6) indicating that it's
	 * in an unknown state.
	 */
	public static final int PUBLISH_STATE_UNKNOWN = 6;

	/**
	 * Publish kind constant (value 1) indicating an incremental publish request.
	 */
	public static final int PUBLISH_INCREMENTAL = 1;

	/**
	 * Publish kind constant (value 2) indicating a full publish request.
	 */
	public static final int PUBLISH_FULL = 2;

	/**
	 * Publish kind constant (value 3) indicating a publish clean request
	 */
	public static final int PUBLISH_CLEAN = 3;
	
	/**
	 * Publish kind constant (value 4) indicating an automatic publish request.
	 */
	public static final int PUBLISH_AUTO = 4;
	
	/*
	 * Debugging details: keys
	 * These should match the keys in org.jboss.tools.rsp.launching.utils.LaunchingDebugProperties
	 */
	public static final String DEBUG_DETAILS_HOST = "debug.details.host";
	
	public static final String DEBUG_DETAILS_PORT = "debug.details.port";
	
	public static final String DEBUG_DETAILS_TYPE = "debug.details.type";
	
	
	
	public static final String WORKFLOW_LICENSE_TEXT_ID = "workflow.license";
	public static final String WORKFLOW_LICENSE_URL_ID = "workflow.license.url";
	public static final String WORKFLOW_LICENSE_SIGN_ID = "workflow.license.sign";
	
	public static final String WORKFLOW_USERNAME_ID = "workflow.username";
	public static final String WORKFLOW_PASSWORD_ID = "workflow.password";

}
