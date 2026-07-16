package com.quickprint.file;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

// What kind of file is this REALLY? The browser's content-type is caller
// -controlled, so tier rules ("guests may only send images/PDF") are enforced
// on the first bytes of the actual content instead.
// ponytail: tiny hand-rolled signature check for the handful of types we gate
// on; switch to Apache Tika only if the matrix ever grows.
public final class FileTypePolicy {

    public enum Kind { PNG, JPEG, GIF, WEBP, PDF, OFFICE_DOC, ZIP, UNKNOWN }

    // Video and ZIP stay out of both sets — they are the Pro tier's hook.
    private static final Set<Kind> GUEST = EnumSet.of(Kind.PNG, Kind.JPEG, Kind.GIF, Kind.WEBP, Kind.PDF);
    private static final Set<Kind> FREE = EnumSet.of(Kind.PNG, Kind.JPEG, Kind.GIF, Kind.WEBP, Kind.PDF, Kind.OFFICE_DOC);

    /** How many leading bytes callers should hand to {@link #detect}. */
    public static final int HEAD_LENGTH = 64;

    private FileTypePolicy() {
    }

    public static Kind detect(byte[] head) {
        if (head == null || head.length < 4) {
            return Kind.UNKNOWN;
        }
        if (starts(head, 0x89, 'P', 'N', 'G')) {
            return Kind.PNG;
        }
        if (starts(head, 0xFF, 0xD8, 0xFF)) {
            return Kind.JPEG;
        }
        if (starts(head, 'G', 'I', 'F', '8')) {
            return Kind.GIF;
        }
        if (starts(head, 'R', 'I', 'F', 'F') && head.length >= 12
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Kind.WEBP;
        }
        if (starts(head, '%', 'P', 'D', 'F')) {
            return Kind.PDF;
        }
        if (starts(head, 'P', 'K', 3, 4)) {
            // Office files (docx/xlsx/pptx) are zips whose first entry is
            // [Content_Types].xml — its name sits in the first local header.
            String ascii = new String(head, StandardCharsets.US_ASCII);
            return ascii.contains("[Content_Types].xml") ? Kind.OFFICE_DOC : Kind.ZIP;
        }
        return Kind.UNKNOWN;
    }

    public static boolean allowedForGuest(Kind kind) {
        return GUEST.contains(kind);
    }

    public static boolean allowedForFreeAccount(Kind kind) {
        return FREE.contains(kind);
    }

    private static boolean starts(byte[] head, int... signature) {
        for (int i = 0; i < signature.length; i++) {
            if (head[i] != (byte) signature[i]) {
                return false;
            }
        }
        return true;
    }
}
