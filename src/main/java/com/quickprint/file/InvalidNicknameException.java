package com.quickprint.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidNicknameException extends RuntimeException {

    public InvalidNicknameException() {
        super("Nickname must be 1-20 characters: lowercase letters, digits or hyphens.");
    }
}
