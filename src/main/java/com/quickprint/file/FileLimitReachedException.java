package com.quickprint.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Free accounts hold at most a fixed number of active files at once.
@ResponseStatus(HttpStatus.CONFLICT)
public class FileLimitReachedException extends RuntimeException {

    public FileLimitReachedException(int max) {
        super("File limit reached (" + max + "). Delete a file to upload a new one.");
    }
}
