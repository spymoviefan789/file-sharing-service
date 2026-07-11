package com.quickprint.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Nicknames are the public identifier a receiver types, so they must be unique
// across the whole system.
@ResponseStatus(HttpStatus.CONFLICT)
public class NicknameTakenException extends RuntimeException {

    public NicknameTakenException(String nickname) {
        super("Nickname already in use: " + nickname);
    }
}
