package com.quickprint.file;

import com.quickprint.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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

    @Transactional
    public void delete(User owner, Long id) {
        StoredFile file = files.findById(id)
                .filter(f -> f.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        storage.delete(file.getStorageName());
        files.delete(file);
    }

    // The owner previewing/downloading their OWN file: no code, no use consumed.
    public record Content(String filename, String contentType, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public Content readOwned(User owner, Long id, boolean preview) {
        StoredFile file = files.findById(id)
                // 404 (not 403) when it isn't theirs, so ids can't be probed.
                .filter(f -> f.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (preview && !ContentTypes.isPreviewable(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        try {
            byte[] bytes = Files.readAllBytes(storage.pathFor(file.getStorageName()));
            return new Content(file.getOriginalFilename(), file.getContentType(), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read stored file", e);
        }
    }
}
