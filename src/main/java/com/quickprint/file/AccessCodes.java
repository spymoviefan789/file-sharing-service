package com.quickprint.file;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

// Rolling access codes, TOTP-style. A per-file secret + the current 60s time
// window are run through HMAC-SHA256 and mapped to 6 characters. This means:
//   - the code changes every minute, in sync for everyone (one global window),
//   - nothing rotating is ever stored (no rotation job needed),
//   - the server can both display the owner's current code and validate a
//     receiver's submission by recomputing it.
public final class AccessCodes {

    public static final long PERIOD_SECONDS = 60;

    // No 0/O or 1/I/L — unambiguous when typed on a strange keyboard.
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private AccessCodes() {
    }

    public static String newSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static long currentWindow() {
        return Instant.now().getEpochSecond() / PERIOD_SECONDS;
    }

    public static long secondsRemaining() {
        return PERIOD_SECONDS - (Instant.now().getEpochSecond() % PERIOD_SECONDS);
    }

    public static String currentCode(String secret) {
        return codeFor(secret, currentWindow());
    }

    // Valid if it matches the current or the immediately previous window —
    // a small grace so a code entered right at the boundary still works.
    public static boolean isValid(String secret, String submitted) {
        if (submitted == null) {
            return false;
        }
        String normalized = submitted.trim().toUpperCase();
        long window = currentWindow();
        return constantTimeEquals(normalized, codeFor(secret, window))
                || constantTimeEquals(normalized, codeFor(secret, window - 1));
    }

    private static String codeFor(String secretBase64, long window) {
        byte[] key = Base64.getDecoder().decode(secretBase64);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] hash = mac.doFinal(Long.toString(window).getBytes(StandardCharsets.UTF_8));
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(ALPHABET.charAt((hash[i] & 0xff) % ALPHABET.length()));
            }
            return code.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    // Constant-time compare so a timing side-channel can't reveal the code.
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
