package com.matyrobbrt.registrationutils.gradle;

import java.security.MessageDigest;

public class Utils {
    private static final char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    public static String byteArray2Hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for(final byte b : bytes) {
            sb.append(HEX[(b & 0xF0) >> 4]);
            sb.append(HEX[b & 0x0F]);
        }
        return sb.toString();
    }

    public static String getStringFromSHA256(String stringToEncrypt) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(stringToEncrypt.getBytes());
            return byteArray2Hex(messageDigest.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
