package com.quickprint.file;

// Single place for the "what is safe to render inline" rule. Serving anything
// else inline (e.g. an HTML file) would run it on our origin — stored XSS.
public final class ContentTypes {

    private ContentTypes() {
    }

    public static boolean isPreviewable(String contentType) {
        return contentType != null
                && (contentType.startsWith("image/") || contentType.equals("application/pdf"));
    }
}
