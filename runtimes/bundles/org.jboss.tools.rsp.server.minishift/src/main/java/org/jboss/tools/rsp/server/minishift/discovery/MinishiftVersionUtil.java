/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.server.minishift.discovery;

import org.jboss.tools.rsp.eclipse.osgi.util.NLS;
import org.jboss.tools.rsp.server.minishift.discovery.MinishiftVersionLoader.MinishiftVersions;

public class MinishiftVersionUtil {
	private static final String MINISHIFT = "Minishift";
	private static final String CDK = "CDK";
	private static final String CRC = "CRC";
	
	private MinishiftVersionUtil() {
		// Intentionally blank 
	}
	
	public static boolean matchesAny(MinishiftVersions versions) {
		return matchesCDK30(versions) == null || matchesCDK32(versions) == null 
				|| matchesMinishift17(versions) == null;
	}
	
	
	public static String matchesCDK32(MinishiftVersions versions) {
		String cdkVers = versions.getCDKVersion();
		if (cdkVers != null) {
			if (matchesCDK32(cdkVers)) {
				return null;
			}
			return notCompatible(CDK, cdkVers);
		}
		return cannotDetermine(CDK);
	}
	public static boolean matchesCDK32(String version) {
		return version.startsWith("3.") && !(matchesCDK3(version));
	}
	public static boolean matchesCDK3(String version) {
		return (version.startsWith("3.0.") || version.startsWith("3.1."));
	}

	public static String matchesCDK30(MinishiftVersions versions) {
		String cdkVers = versions.getCDKVersion();
		if (cdkVers == null) {
			return cannotDetermine(CDK);
		}
		if (matchesCDK3(cdkVers)) {
			return null;
		}
		return notCompatible(CDK, cdkVers);
	}
	
	public static boolean matchesCRC1(String version) {
		return version.startsWith("1.");
	}
	
	public static String matchesCRC10(MinishiftVersions versions) {
		String crcVers = versions.getCRCVersion();
		if (crcVers == null) {
			return cannotDetermine(CRC);
		}
		if (matchesCRC1(crcVers)) {
			return null;
		}
		return notCompatible(CRC, crcVers);
	}
	
	public static String matchesMinishift17(MinishiftVersions versions) {
		if( versions.getCDKVersion() != null ) {
			return notCompatible(CDK, versions.getCDKVersion());
		}

		String msVers = versions.getMinishiftVersion();
		if (msVers != null) {
			if (matchesMinishift17OrGreater(msVers)) {
				return null;
			}
			return notCompatible(MINISHIFT, msVers);
		}
		return cannotDetermine(MINISHIFT);
	}
	private static String cannotDetermine(String type) {
		return NLS.bind("Cannot determine {0} version.", type);
	}
	
	private static String notCompatible(String type, String vers) {
		return NLS.bind("{0} version {1} is not compatible with this server adapter.", type, vers);
	}
	

	public static boolean matchesCRC1_24_OrGreater(MinishiftVersions versions) {
		String crcVers = versions.getCRCVersion();
		if( crcVers == null )
			return false;
		
		if (crcVers.contains("+")) {
			crcVers = crcVers.substring(0, crcVers.indexOf('+'));
		}
		String[] segments = crcVers.split("\\.");
		if ("1".equals(segments[0]) && Integer.parseInt(segments[1]) >= 24) {
			return true;
		}
		return false;
	}

	public static boolean matchesMinishift17OrGreater(String version) {
		if (version.contains("+")) {
			String prefix = version.substring(0, version.indexOf('+'));
			String[] segments = prefix.split("\\.");
			if ("1".equals(segments[0]) && Integer.parseInt(segments[1]) >= 7) {
				return true;
			}
		}
		return false;
	}
}
