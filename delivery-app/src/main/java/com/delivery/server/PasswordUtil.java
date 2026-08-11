package com.delivery.server;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Bam mat khau SHA-256 + salt ngau nhien. Luu dang "salt_hex:hash_hex".
 * (Du cho do an; san pham that nen dung BCrypt/Argon2.)
 */
public final class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private PasswordUtil() {}

    public static String hash(String plain) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt) + ":" + sha256(salt, plain);
    }

    public static boolean verify(String plain, String stored) {
        if (stored == null || !stored.contains(":")) return false;
        String[] parts = stored.split(":", 2);
        byte[] salt = HexFormat.of().parseHex(parts[0]);
        return MessageDigest.isEqual(
                sha256(salt, plain).getBytes(), parts[1].getBytes());
    }

    private static String sha256(byte[] salt, String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return HexFormat.of().formatHex(md.digest(plain.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
