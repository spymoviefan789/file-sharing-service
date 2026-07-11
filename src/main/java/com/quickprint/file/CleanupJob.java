package com.quickprint.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// Expiry is enforced on access too (ReceiveService checks isExpired), so this
// job is hygiene, not the security boundary: it reclaims disk space and removes
// dead rows. Interval configurable via quickprint.cleanup.interval.
@Component
public class CleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CleanupJob.class);

    private final StoredFileRepository files;
    private final FileStorageService storage;

    public CleanupJob(StoredFileRepository files, FileStorageService storage) {
        this.files = files;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${quickprint.cleanup.interval:PT1H}")
    @Transactional
    public void removeExpired() {
        List<StoredFile> expired = files.findByExpiresAtBefore(Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        for (StoredFile file : expired) {
            storage.delete(file.getStorageName());
        }
        files.deleteAll(expired);
        log.info("Cleanup removed {} expired file(s)", expired.size());
    }
}
