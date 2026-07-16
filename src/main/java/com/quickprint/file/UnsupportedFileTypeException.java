package com.quickprint.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// The uploaded bytes are not a type this tier may send (checked by magic
// bytes, so renaming a file doesn't help).
@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException() {
        super("This file type is not allowed here. Allowed: images and PDF.");
    }
}
