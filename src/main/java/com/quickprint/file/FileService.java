package com.quickprint.file;

import com.quickprint.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.quickprint.guest.NicknameGenerator;

import java.io.IOException;
import java.io.InputStream;
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
    private final Duration guestTtl;
    private final int freeMaxFiles;

    public FileService(StoredFileRepository files, FileStorageService storage,
                       @Value("${quickprint.storage.default-ttl}") Duration defaultTtl,
                       @Value("${quickprint.storage.guest-ttl}") Duration guestTtl,
                       @Value("${quickprint.limits.free-max-files}") int freeMaxFiles) {
        this.files = files;
        this.storage = storage;
        this.defaultTtl = defaultTtl;
        this.guestTtl = guestTtl;
        this.freeMaxFiles = freeMaxFiles;
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

    // Anonymous one-time upload: generated name + fixed code, images/PDF only,
    // short TTL. Returns exactly what the sender must hand to the receiver.
    public record GuestUpload(String nickname, String code) {
    }

    @Transactional
    public GuestUpload uploadGuest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("File is empty.");
        }
        FileTypePolicy.Kind kind = FileTypePolicy.detect(headOf(file));
        if (!FileTypePolicy.allowedForGuest(kind)) {
            throw new UnsupportedFileTypeException();
        }

        String nickname = freshGuestNickname();
        String code = FixedCodes.generate();

        String storageName = storage.store(file);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String originalName = file.getOriginalFilename();
        originalName = originalName == null ? "file" : originalName.replaceAll(".*[/\\\\]", "");

        files.save(StoredFile.guest(originalName, nickname, storageName, contentType,
                file.getSize(), code, Instant.now().plus(guestTtl)));
        return new GuestUpload(nickname, code);
    }

    // The generator's space is ~36k names; retry a few times on collision.
    private String freshGuestNickname() {
        for (int attempt = 0; attempt < 30; attempt++) {
            String candidate = NicknameGenerator.generate();
            if (!files.existsByNickname(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free guest nickname.");
    }

    // First bytes of the real content — what FileTypePolicy decides on.
    private static byte[] headOf(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(FileTypePolicy.HEAD_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
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
