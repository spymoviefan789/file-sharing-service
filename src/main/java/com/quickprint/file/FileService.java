package com.quickprint.file;

import com.quickprint.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class FileService {

    private static final Pattern NICKNAME = Pattern.compile("[a-z0-9-]{1,20}");

    private final StoredFileRepository files;
    private final FileStorageService storage;
    private final Duration defaultTtl;

    public FileService(StoredFileRepository files, FileStorageService storage,
                       @Value("${quickprint.storage.default-ttl}") Duration defaultTtl) {
        this.files = files;
        this.storage = storage;
        this.defaultTtl = defaultTtl;
    }

    @Transactional
    public StoredFile upload(User owner, MultipartFile file, String nicknameRaw,
                             Integer shareLimit, boolean oneTime) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("File is empty.");
        }
        String nickname = nicknameRaw == null ? "" : nicknameRaw.trim().toLowerCase();
        if (!NICKNAME.matcher(nickname).matches()) {
            throw new InvalidNicknameException();
        }
        if (shareLimit != null && shareLimit < 1) {
            throw new InvalidUploadException("shareLimit must be >= 1 (omit it for unlimited).");
        }
        if (files.existsByNickname(nickname)) {
            throw new NicknameTakenException(nickname);
        }

        String storageName = storage.store(file);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        // Strip any directory components a browser may include — the value is
        // only ever stored/displayed, never used as a path, but keep it clean.
        String originalName = file.getOriginalFilename();
        originalName = originalName == null ? "file" : originalName.replaceAll(".*[/\\\\]", "");

        StoredFile stored = new StoredFile(owner, originalName, nickname, storageName,
                contentType, file.getSize(), shareLimit, oneTime,
                AccessCodes.newSecret(), Instant.now().plus(defaultTtl));
        return files.save(stored);
    }

    @Transactional(readOnly = true)
    public List<StoredFile> list(User owner) {
        return files.findByOwnerOrderByCreatedAtDesc(owner);
    }
}
