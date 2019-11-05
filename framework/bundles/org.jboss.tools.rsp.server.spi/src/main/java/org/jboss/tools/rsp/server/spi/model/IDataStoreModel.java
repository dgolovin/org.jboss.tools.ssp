/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.spi.model;

import java.io.File;
import java.io.IOException;

public interface IDataStoreModel {

	public File getDataLocation();
	public boolean isInUse();
	public boolean lock() throws IOException;
	public boolean unlock() throws IOException;
}
