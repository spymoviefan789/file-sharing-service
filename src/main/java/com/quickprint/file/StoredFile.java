package com.quickprint.file;

import com.quickprint.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

// One uploaded file. The bytes live on disk under `storageName`; everything
// else (including the original filename) lives only here in the DB, so a
// crafted filename can never influence a disk path — that's our path-traversal
// defense. `nickname` is the public identifier a receiver types in.
@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null owner = guest upload (anonymous, one-time, fixed code).
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Column(name = "storage_name", nullable = false, unique = true)
    private String storageName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // null = unlimited uses. usesLeft counts down; each download or preview
    // consumes one. shareLimit is kept for display ("3× of N").
    @Column(name = "share_limit")
    private Integer shareLimit;

    @Column(name = "uses_left")
    private Integer usesLeft;

    // Per-file secret (base64) used to derive the rolling access code. Never
    // leaves the server except as the short-lived code itself.
    @Column(nullable = false)
    private String secret;

    // Fixed access code for guest files; null for account files, whose codes
    // roll (see AccessCodes). Presence of this value picks the validation path.
    @Column(name = "fixed_code")
    private String fixedCode;

    @Column(name = "one_time", nullable = false)
    private boolean oneTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected StoredFile() {
        // for JPA
    }

    public StoredFile(User owner, String originalFilename, String nickname, String storageName,
                      String contentType, long sizeBytes, Integer shareLimit, boolean oneTime,
                      String secret, Instant expiresAt) {
        this.owner = owner;
        this.originalFilename = originalFilename;
        this.nickname = nickname;
        this.storageName = storageName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.shareLimit = shareLimit;
        this.usesLeft = shareLimit; // starts full; null stays null (unlimited)
        this.oneTime = oneTime;
        this.secret = secret;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    // A guest upload: no owner, one-time use, fixed code, short TTL. A rolling
    // secret is still generated so the column's NOT NULL stays untouched — it
    // is simply never used when fixedCode is present.
    public static StoredFile guest(String originalFilename, String nickname, String storageName,
                                   String contentType, long sizeBytes, String fixedCode,
                                   Instant expiresAt) {
        StoredFile file = new StoredFile(null, originalFilename, nickname, storageName,
                contentType, sizeBytes, 1, true, AccessCodes.newSecret(), expiresAt);
        file.fixedCode = fixedCode;
        return file;
    }

    public boolean isUnlimited() {
        return shareLimit == null;
    }

    public boolean isExhausted() {
        return usesLeft != null && usesLeft <= 0;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // Consumes one use. Returns false if there was nothing left to consume.
    // Callers persist the entity after a true result.
    public boolean consumeOneUse() {
        if (isUnlimited()) {
            return true;
        }
        if (usesLeft <= 0) {
            return false;
        }
        usesLeft -= 1;
        return true;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getNickname() {
        return nickname;
    }

    public String getStorageName() {
        return storageName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getShareLimit() {
        return shareLimit;
    }

    public Integer getUsesLeft() {
        return usesLeft;
    }

    public String getSecret() {
        return secret;
    }

    public String getFixedCode() {
        return fixedCode;
    }

    public boolean isOneTime() {
        return oneTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
