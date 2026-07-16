package com.quickprint.receive;

import com.quickprint.file.AccessCodes;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// A limited account file must disappear the moment its last use is spent —
// it should never linger as a dead "0×" row on the dashboard.
class ReceiveServiceLastUseTest {

    private static final String SECRET = AccessCodes.newSecret();

    private static StoredFile accountFile(int shareLimit) {
        return new StoredFile(null, "doc.pdf", "relatorio", "blob", "application/pdf",
                5, shareLimit, false, SECRET, Instant.now().plus(Duration.ofDays(1)));
    }

    @Test
    void fileIsDeletedWhenTheLastUseIsSpent(@TempDir Path dir) throws IOException {
        StoredFile file = accountFile(1);
        Path stored = dir.resolve("blob");
        Files.write(stored, "hello".getBytes());

        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(repo.findByNickname("relatorio")).thenReturn(Optional.of(file));
        when(repo.decrementUse(file.getId())).thenReturn(1);
        when(repo.usesLeft(file.getId())).thenReturn(0); // the spend took the last one
        when(storage.pathFor("blob")).thenReturn(stored);

        new ReceiveService(repo, storage).fetch("relatorio", AccessCodes.currentCode(SECRET), false);

        verify(storage).delete("blob");
        verify(repo).delete(file);
    }

    @Test
    void fileSurvivesWhileUsesRemain(@TempDir Path dir) throws IOException {
        StoredFile file = accountFile(5);
        Path stored = dir.resolve("blob");
        Files.write(stored, "hello".getBytes());

        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(repo.findByNickname("relatorio")).thenReturn(Optional.of(file));
        when(repo.decrementUse(file.getId())).thenReturn(1);
        when(repo.usesLeft(file.getId())).thenReturn(4); // still uses left
        when(storage.pathFor("blob")).thenReturn(stored);

        new ReceiveService(repo, storage).fetch("relatorio", AccessCodes.currentCode(SECRET), false);

        verify(storage, never()).delete("blob");
        verify(repo, never()).delete(file);
    }
}
