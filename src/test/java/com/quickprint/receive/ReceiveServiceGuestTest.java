package com.quickprint.receive;

import com.quickprint.file.FileStorageService;
import com.quickprint.file.StoredFile;
import com.quickprint.file.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiveServiceGuestTest {

    private static StoredFile guestFile() {
        return StoredFile.guest("doc.pdf", "sillygoose76", "blob", "application/pdf",
                5, "ABC234", Instant.now().plus(Duration.ofHours(1)));
    }

    @Test
    void guestFileIsServedWithItsFixedCodeAndDeletedAfterUse(@TempDir Path dir) throws IOException {
        Path stored = dir.resolve("blob");
        Files.write(stored, "hello".getBytes());

        StoredFile file = guestFile();
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(repo.findByNickname("sillygoose76")).thenReturn(Optional.of(file));
        when(repo.decrementUse(any())).thenReturn(1);
        when(storage.pathFor("blob")).thenReturn(stored);

        ReceiveService service = new ReceiveService(repo, storage);
        ReceiveService.Fetched fetched = service.fetch("sillygoose76", "abc234", false);

        assertThat(fetched.content()).isEqualTo("hello".getBytes());
        // one-time file: gone after the single use
        verify(storage).delete("blob");
        verify(repo).delete(file);
    }

    @Test
    void guestFileRejectsAWrongCodeWithTheGenericNotFound() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(repo.findByNickname("sillygoose76")).thenReturn(Optional.of(guestFile()));

        ReceiveService service = new ReceiveService(repo, storage);

        assertThatThrownBy(() -> service.fetch("sillygoose76", "ZZZZZZ", false))
                .isInstanceOf(DownloadNotAvailableException.class);
    }
}
