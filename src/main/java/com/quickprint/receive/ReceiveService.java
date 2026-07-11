package com.quickprint.receive;

import com.quickprint.file.AccessCodes;
import com.quickprint.file.FileStorageService;
import com.quickprint.file.StoredFile;
import com.quickprint.file.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

// The receiving side: no account, just a nickname + the current code.
@Service
public class ReceiveService {

    private final StoredFileRepository files;
    private final FileStorageService storage;

    public ReceiveService(StoredFileRepository files, FileStorageService storage) {
        this.files = files;
        this.storage = storage;
    }

    // What the controller streams back.
    public record Fetched(String filename, String contentType, byte[] content) {
    }

    @Transactional
    public Fetched fetch(String nickname, String code, boolean preview) {
        StoredFile file = files.findByNickname(nickname)
                .filter(f -> !f.isExpired())
                .filter(f -> AccessCodes.isValid(f.getSecret(), code))
                .orElseThrow(DownloadNotAvailableException::new);

        if (preview && !isPreviewable(file.getContentType())) {
            throw new PreviewNotSupportedException();
        }

        // Atomic spend. Unlimited files (usesLeft == null) skip the guard.
        if (!file.isUnlimited() && files.decrementUse(file.getId()) == 0) {
            throw new DownloadNotAvailableException();
        }

        // ponytail: reads the whole file into memory. Fine at the 25 MB upload
        // cap; switch to streaming if larger files are ever allowed.
        byte[] content;
        try {
            content = Files.readAllBytes(storage.pathFor(file.getStorageName()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read stored file", e);
        }

        if (file.isOneTime()) {
            storage.delete(file.getStorageName());
            files.delete(file);
        }

        return new Fetched(file.getOriginalFilename(), file.getContentType(), content);
    }

    // Only types the browser renders without executing anything.
    private boolean isPreviewable(String contentType) {
        return contentType != null
                && (contentType.startsWith("image/") || contentType.equals("application/pdf"));
    }
}
