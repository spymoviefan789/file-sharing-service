package com.quickprint.file;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

// Fixed access codes for guest uploads. Unlike the rolling AccessCodes used by
// account files, a guest file's code never changes: it is generated once,
// stored on the file, and compared in constant time. Guest files are one-time
// use with a short TTL, which keeps the fixed code's exposure window small.
public final class FixedCodes {

    // Same unambiguous alphabet as AccessCodes (no 0/O, 1/I/L).
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private FixedCodes() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    // Constant-time compare so a timing side-channel can't reveal the code.
    public static boolean matches(String stored, String submitted) {
        if (submitted == null) {
            return false;
        }
        String normalized = submitted.trim().toUpperCase();
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                normalized.getBytes(StandardCharsets.UTF_8));
    }
}
