package com.quickprint.file;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileServiceGuestUploadTest {

    private static final byte[] PDF_HEAD = "%PDF-1.7 minimal".getBytes();

    private FileService service(StoredFileRepository repo, FileStorageService storage) {
        return new FileService(repo, storage, Duration.ofDays(7), Duration.ofHours(24), 20);
    }

    @Test
    void guestUploadReturnsGeneratedNicknameAndCode() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(repo.existsByNickname(anyString())).thenReturn(false);
        when(repo.save(any(StoredFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storage.store(any())).thenReturn("uuid-on-disk");

        FileService.GuestUpload result = service(repo, storage)
                .uploadGuest(new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF_HEAD));

        assertThat(result.nickname()).matches("[a-z]+[0-9]{2}");
        assertThat(result.code()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}");
    }

    @Test
    void guestUploadRejectsNonImageNonPdfContent() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        // A Windows executable renamed to .pdf: content wins, not the name.
        MockMultipartFile exe = new MockMultipartFile(
                "file", "totally-a.pdf", "application/pdf", new byte[]{'M', 'Z', (byte) 0x90, 0});

        assertThatThrownBy(() -> service(repo, storage).uploadGuest(exe))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void guestUploadRejectsAnEmptyFile() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service(repo, storage).uploadGuest(empty))
                .isInstanceOf(InvalidUploadException.class);
    }
}
