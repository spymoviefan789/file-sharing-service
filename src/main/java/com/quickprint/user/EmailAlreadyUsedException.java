package com.quickprint.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// @ResponseStatus lets Spring turn this exception into a 409 Conflict
// automatically — no @ControllerAdvice needed for this one case.
@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Email already registered: " + email);
    }
}
