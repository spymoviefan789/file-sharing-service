package com.quickprint.receive;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Preview is only offered for types the browser renders inertly (images, PDF).
// Anything else must be downloaded — serving, say, an HTML file inline would
// run it on our origin (stored XSS).
@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class PreviewNotSupportedException extends RuntimeException {

    public PreviewNotSupportedException() {
        super("This file type cannot be previewed. Download it instead.");
    }
}
