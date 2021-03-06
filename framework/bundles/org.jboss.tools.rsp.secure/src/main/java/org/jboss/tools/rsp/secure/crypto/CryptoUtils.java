/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package org.jboss.tools.rsp.secure.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * A utility class that encrypts or decrypts a file.
 *
 */
public class CryptoUtils {

	static {
		Security.setProperty("crypto.policy", "unlimited");
	}
	
	private String algo;
	private String transformation;
	public CryptoUtils(String algo, String transformation) {
		this.algo = algo;
		this.transformation = transformation;
	}

	public byte[] keyTo16(byte[] bytes) {
		if (bytes.length < 16)
			return padKey(bytes);
		if (bytes.length == 16)
			return bytes;
		byte[] ret = new byte[16];
		System.arraycopy(bytes, 0, ret, 0, 16);
		return ret;
	}

	// Pad the key to be a multiple of 16 bytes
	public byte[] padKey(byte[] bytes) {
		int mod = bytes.length % 16;
		int toPad = 16 - mod;
		byte[] ret = new byte[bytes.length + toPad];
		System.arraycopy(bytes, 0, ret, 0, bytes.length);
		for (int i = bytes.length; i < ret.length; i++) {
			ret[i] = 126; // pad character is ~
		}
		return ret;
	}

	public byte[] encrypt(byte[] key, byte[] plain) throws CryptoException {
		return doCrypto(Cipher.ENCRYPT_MODE, key, plain);
	}

	public byte[] decrypt(byte[] key, byte[] encrypted) throws CryptoException {
		return doCrypto(Cipher.DECRYPT_MODE, key, encrypted);
	}

	public void encrypt(byte[] key, File inputFile, File outputFile) throws CryptoException {
		try {
			byte[] inputBytes = getBytesFromFile(inputFile);
			byte[] encrypted = doCrypto(Cipher.ENCRYPT_MODE, key, inputBytes);
			writeBytesToFile(outputFile, encrypted);
		} catch (IOException ioe) {
			throw new CryptoException("Error encrypting/decrypting file", ioe);
		}
	}

	public void decrypt(byte[] key, File inputFile, File outputFile) throws CryptoException {
		try {
			byte[] inputBytes = getBytesFromFile(inputFile);
			byte[] encrypted = doCrypto(Cipher.DECRYPT_MODE, key, inputBytes);
			writeBytesToFile(outputFile, encrypted);
		} catch (IOException ioe) {
			throw new CryptoException("Error encrypting/decrypting file", ioe);
		}
	}

	public byte[] getBytesFromFile(File f) throws FileNotFoundException, IOException {
		try (FileInputStream inputStream = new FileInputStream(f)) {
			byte[] inputBytes = new byte[(int) f.length()];
			if( inputStream.read(inputBytes) > 0 )
				return inputBytes;
			return new byte[0];
		}
	}

	public void writeBytesToFile(File outputFile, byte[] bytes) throws IOException {
		try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
			outputStream.write(bytes);
		}
	}

	private byte[] doCrypto(int cipherMode, byte[] key, byte[] inputBytes) throws CryptoException {
		try {
			Key secretKey = new SecretKeySpec(key, algo);
			Cipher cipher = Cipher.getInstance(transformation);
			cipher.init(cipherMode, secretKey);

			byte[] outputBytes = cipher.doFinal(inputBytes);
			return outputBytes;
		} catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException
				| IllegalBlockSizeException ex) {
			throw new CryptoException("Error encrypting/decrypting file", ex);
		}
	}
}