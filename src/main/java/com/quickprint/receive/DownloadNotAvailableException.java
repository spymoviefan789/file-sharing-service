package com.quickprint.receive;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// One generic error for every "you can't have this file" case — unknown
// nickname, wrong code, expired, or exhausted. Never reveal which, so guessable
// nicknames can't be probed for existence.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DownloadNotAvailableException extends RuntimeException {

    public DownloadNotAvailableException() {
        super("File not found, or the code is invalid, expired, or used up.");
    }
}
