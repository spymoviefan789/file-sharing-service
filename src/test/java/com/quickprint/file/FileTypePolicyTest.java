package com.quickprint.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypePolicyTest {

    private static byte[] head(int... bytes) {
        byte[] out = new byte[64];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = (byte) bytes[i];
        }
        return out;
    }

    @Test
    void detectsCommonTypesByMagicBytes() {
        assertThat(FileTypePolicy.detect(head(0x89, 'P', 'N', 'G'))).isEqualTo(FileTypePolicy.Kind.PNG);
        assertThat(FileTypePolicy.detect(head(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(FileTypePolicy.Kind.JPEG);
        assertThat(FileTypePolicy.detect(head('G', 'I', 'F', '8'))).isEqualTo(FileTypePolicy.Kind.GIF);
        assertThat(FileTypePolicy.detect(head('%', 'P', 'D', 'F'))).isEqualTo(FileTypePolicy.Kind.PDF);
    }

    @Test
    void detectsWebpInsideRiff() {
        byte[] webp = head('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');
        assertThat(FileTypePolicy.detect(webp)).isEqualTo(FileTypePolicy.Kind.WEBP);
    }

    @Test
    void distinguishesOfficeDocsFromPlainZips() {
        byte[] docx = head('P', 'K', 3, 4);
        byte[] marker = "[Content_Types].xml".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, docx, 30, marker.length);
        byte[] zip = head('P', 'K', 3, 4);

        assertThat(FileTypePolicy.detect(docx)).isEqualTo(FileTypePolicy.Kind.OFFICE_DOC);
        assertThat(FileTypePolicy.detect(zip)).isEqualTo(FileTypePolicy.Kind.ZIP);
    }

    @Test
    void renamedExecutableIsUnknownRegardlessOfDeclaredType() {
        // "MZ" — a Windows .exe renamed to .pdf still starts with MZ.
        assertThat(FileTypePolicy.detect(head('M', 'Z', 0x90, 0)))
                .isEqualTo(FileTypePolicy.Kind.UNKNOWN);
        assertThat(FileTypePolicy.detect(null)).isEqualTo(FileTypePolicy.Kind.UNKNOWN);
        assertThat(FileTypePolicy.detect(Arrays.copyOf(new byte[0], 2)))
                .isEqualTo(FileTypePolicy.Kind.UNKNOWN);
    }

    @Test
    void guestAllowsOnlyImagesAndPdf() {
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.PNG)).isTrue();
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.PDF)).isTrue();
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.OFFICE_DOC)).isFalse();
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.ZIP)).isFalse();
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.UNKNOWN)).isFalse();
    }

    @Test
    void freeAccountAlsoAllowsOfficeDocsButNotZips() {
        assertThat(FileTypePolicy.allowedForFreeAccount(FileTypePolicy.Kind.OFFICE_DOC)).isTrue();
        assertThat(FileTypePolicy.allowedForFreeAccount(FileTypePolicy.Kind.ZIP)).isFalse();
        assertThat(FileTypePolicy.allowedForFreeAccount(FileTypePolicy.Kind.UNKNOWN)).isFalse();
    }
}
