/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.launching.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class NativeEnvironmentUtils {
	private static final NativeEnvironmentUtils instance = new NativeEnvironmentUtils();
	public static final NativeEnvironmentUtils getDefault() {
		return instance;
	}
	
	
	/**
	 * The collection of native environment variables on the user's system. Cached
	 * after being computed once as the environment cannot change.
	 */
	private static HashMap<String, String> fgNativeEnv = null;
	private static HashMap<String, String> fgNativeEnvCasePreserved = null;
	

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.ILaunchManager#getNativeEnvironment()
	 */
	public synchronized Map<String, String> getNativeEnvironment() {
		if (fgNativeEnv == null) {
			Map<String, String> casePreserved = getNativeEnvironmentCasePreserved();
			if (OSUtils.isWindows()) {
				fgNativeEnv = new HashMap<String, String>();
				for (Entry<String, String> entry : casePreserved.entrySet()) {
					fgNativeEnv.put(entry.getKey().toUpperCase(), entry.getValue());
				}
			} else {
				fgNativeEnv = new HashMap<String, String>(casePreserved);
			}
		}
		return new HashMap<String, String>(fgNativeEnv);
	}

	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.ILaunchManager#getNativeEnvironmentCasePreserved()
	 */
	public synchronized Map<String, String> getNativeEnvironmentCasePreserved() {
		if (fgNativeEnvCasePreserved == null) {
			HashMap<String,String> tmp = new HashMap<>();
			fillNativeEnvironmentCache(tmp);
			fgNativeEnvCasePreserved = tmp;
		}
		return new HashMap<String, String>(fgNativeEnvCasePreserved);
	}
	
	private boolean isWin9xME() {
		boolean isWin9xME= false; //see bug 50567
		if (OSUtils.isWindows()) {
			String osName= System.getProperty("os.name"); //$NON-NLS-1$
			isWin9xME= osName != null && (osName.startsWith("Windows 9") || osName.startsWith("Windows ME")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return isWin9xME;
	}
	
	private String getNativeEnvCommand(boolean isWin9xME, String fileName) {
		String nativeCommand= null;
		if (OSUtils.isWindows()) {
			if (isWin9xME) {
				// Win 95, 98, and ME
				// SET might not return therefore we pipe into a file
				nativeCommand= "command.com /C set > " + fileName; //$NON-NLS-1$
			} else {
				// Win NT, 2K, XP
				nativeCommand= "cmd.exe /C set"; //$NON-NLS-1$
			}
		} else if (!OSUtils.isUnknown()){
			nativeCommand= "env";		 //$NON-NLS-1$
		}
		return nativeCommand;
	}
	
	private void fillEnvironmentWin9xME(String fileName, Map<String, String> cache) throws IOException  {
		//read piped data on Win 95, 98, and ME
		Properties p= new Properties();
		File file= new File(fileName);
		try(InputStream stream = new BufferedInputStream(new FileInputStream(file))){
			p.load(stream);
			if (!file.delete()) {
				file.deleteOnExit(); // if delete() fails try again on VM close
			}
			for (Entry<Object, Object> entry : p.entrySet()) {
				// Win32's environment variables are case insensitive. Put everything
				// to uppercase so that (for example) the "PATH" variable will match
				// "pAtH" correctly on Windows.
				String key = (String) entry.getKey();
				//no need to cast value
				cache.put(key, (String) p.get(key));
			}
		}
	}
	
	private void fillEnvironmentStandard(Process process, Map<String, String> cache) throws IOException {
		//read process directly on other platforms
		//we need to parse out matching '{' and '}' for function declarations in .bash environments
		// pattern is [func name]=() { and we must find the '}' on its own line with no trailing ';'
		try (InputStream stream = process.getInputStream();
		InputStreamReader isreader = new InputStreamReader(stream);
        BufferedReader reader = new BufferedReader(isreader)) {
			String line = reader.readLine();
			String key = null;
			String value = null;
			String newLine = System.getProperty("line.separator"); //$NON-NLS-1$
			while (line != null) {
				int func = line.indexOf("=()"); //$NON-NLS-1$
				if (func > 0) {
					key = line.substring(0, func);
					// scan until we find the closing '}' with no
					// following chars
					value = line.substring(func + 1);
					while (line != null && !line.equals("}")) { //$NON-NLS-1$
						line = reader.readLine();
						if (line != null) {
							value += newLine + line;
						}
					}
					line = reader.readLine();
				}
				else {
					int separator = line.indexOf('=');
					if (separator > 0) {
						key = line.substring(0, separator);
						value = line.substring(separator + 1);
						line = reader.readLine();
						if (line != null) {
							// this line has a '=' read ahead to check
							// next line for '=', might be broken on
							// more than one line
							// also if line starts with non-identifier -
							// it is remainder of previous variable
							while (line.indexOf('=') < 0 || (line.length() > 0 && !Character.isJavaIdentifierStart(line.charAt(0)))) {
								value += newLine + line;
								line = reader.readLine();
								if (line == null) {
									// if next line read is the end of
									// the file quit the loop
									break;
								}
							}
						}
					}
				}
				if (key != null) {
					cache.put(key, value);
					key = null;
					value = null;
				} else {
					line = reader.readLine();
				}
			}
		}
	}
	/**
	 * Computes and caches the native system environment variables as a map of
	 * variable names and values (Strings) in the given map.
	 * <p>
	 * Note that WIN32 system environment preserves
	 * the case of variable names but is otherwise case insensitive.
	 * Depending on what you intend to do with the environment, the
	 * lack of normalization may or may not be create problems. This
	 * method preserves mixed-case keys using the variable names
	 * recorded by the OS.
	 * </p>
	 * @param cache the map
	 * @since 3.1
	 */
	private void fillNativeEnvironmentCache(Map<String, String> cache) {
		try {
			boolean isWin9xME= isWin9xME();
			File f = File.createTempFile("env_", ".txt");
			f.deleteOnExit();
			String fileName= f.getAbsolutePath();
			String nativeCommand = getNativeEnvCommand(isWin9xME, fileName);
			if (nativeCommand == null) {
				return;
			}
			Process process= Runtime.getRuntime().exec(nativeCommand);
			if (isWin9xME) {
				fillEnvironmentWin9xME(fileName, cache);
			} else {
				fillEnvironmentStandard(process, cache);
			}
		} catch (IOException e) {
			// Native environment-fetching code failed.
			// This can easily happen and is not useful to log.
		}
	}

	
	public String[] getEnvironment(Map<String, String> configEnv, boolean appendNativeEnv) {
		Map<String, String> env = new HashMap<String, String>();
		if (appendNativeEnv) {
			env.putAll(NativeEnvironmentUtils.getDefault().getNativeEnvironmentCasePreserved());
		}
		if( configEnv != null ) {
			env.putAll(configEnv);
		}
		
		List<String> strings = new ArrayList<String>(env.size());
		StringBuffer buffer = null;
		for (Entry<String, String> entry : env.entrySet()) {
			buffer = new StringBuffer(entry.getKey());
			buffer.append('=').append(entry.getValue());
			strings.add(buffer.toString());
		}
		return strings.toArray(new String[strings.size()]);
	}
}
