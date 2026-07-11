package com.quickprint.file;

import java.time.Instant;

// What the owner's dashboard receives per file. Includes the current rolling
// code and how many seconds until it changes, so the UI never generates codes
// itself — the server is the single source of truth.
public record FileResponse(
        Long id,
        String originalFilename,
        String nickname,
        String contentType,
        long sizeBytes,
        Integer shareLimit,
        Integer usesLeft,
        boolean unlimited,
        boolean oneTime,
        Instant createdAt,
        Instant expiresAt,
        String code,
        long codeExpiresInSeconds) {

    public static FileResponse from(StoredFile f) {
        return new FileResponse(
                f.getId(),
                f.getOriginalFilename(),
                f.getNickname(),
                f.getContentType(),
                f.getSizeBytes(),
                f.getShareLimit(),
                f.getUsesLeft(),
                f.isUnlimited(),
                f.isOneTime(),
                f.getCreatedAt(),
                f.getExpiresAt(),
                AccessCodes.currentCode(f.getSecret()),
                AccessCodes.secondsRemaining());
    }
}
