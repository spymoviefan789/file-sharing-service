package com.quickprint.file;

import com.quickprint.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileServiceAccountLimitsTest {

    private static final MockMultipartFile PDF =
            new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.7 x".getBytes());

    private FileService service(StoredFileRepository repo) {
        return new FileService(repo, mock(FileStorageService.class),
                Duration.ofDays(7), Duration.ofHours(24), 20);
    }

    @Test
    void rejectsTheUploadOnceTheAccountHoldsTwentyFiles() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        when(repo.countByOwner(any())).thenReturn(20L);

        assertThatThrownBy(() -> service(repo).upload(mock(User.class), PDF, "meu-cv", null, false))
                .isInstanceOf(FileLimitReachedException.class);
    }

    @Test
    void rejectsContentTheFreeTierMayNotSend() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        when(repo.countByOwner(any())).thenReturn(0L);
        // A plain zip: only the (future) Pro tier may send archives.
        MockMultipartFile zip = new MockMultipartFile(
                "file", "backup.zip", "application/zip", new byte[]{'P', 'K', 3, 4, 0, 0});

        assertThatThrownBy(() -> service(repo).upload(mock(User.class), zip, "backup", null, false))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }
}
