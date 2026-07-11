package com.quickprint.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

// Owns the bytes on disk. Files get random UUID names; nothing the user typed
// ever becomes part of a path. The rest of the app only deals in these opaque
// storage names.
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${quickprint.storage.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage directory: " + root, e);
        }
    }

    // Streams the upload to a new random file and returns its storage name.
    public String store(MultipartFile file) {
        String storageName = UUID.randomUUID().toString();
        Path target = root.resolve(storageName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
        return storageName;
    }

    public Path pathFor(String storageName) {
        // resolve + normalize + containment check: defense in depth even though
        // storageName is a server-generated UUID, never user input.
        Path resolved = root.resolve(storageName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage name");
        }
        return resolved;
    }

    public void delete(String storageName) {
        try {
            Files.deleteIfExists(pathFor(storageName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete file", e);
        }
    }
}
