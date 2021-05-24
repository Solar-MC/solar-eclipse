package com.solarmc.eclipse.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

    public static final MessageDigest SHA_DIGEST;

    private static String getFileChecksum(MessageDigest digest, File file) throws IOException {
        FileInputStream in = new FileInputStream(file);

        byte[] byteArray = new byte[1024];
        int bytesCount;
        while ((bytesCount = in.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        }
        in.close();

        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public static String getSha256Checksum(File file) {
        try {
            return getFileChecksum(SHA_DIGEST, file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get Sha256 checksum.", e);
        }
    }

    static {
        try {
            SHA_DIGEST = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
