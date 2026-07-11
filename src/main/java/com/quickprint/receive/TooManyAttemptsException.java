package com.quickprint.receive;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException() {
        super("Too many attempts. Wait a minute and try again.");
    }
}
