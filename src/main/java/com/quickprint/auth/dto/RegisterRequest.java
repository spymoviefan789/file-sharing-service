package com.quickprint.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The exact shape the /register endpoint accepts. A record (Java 21) is an
// immutable data carrier — perfect for a DTO. Validation lives here so bad
// input is rejected (400) before any of our code runs.
public record RegisterRequest(

        @NotBlank @Email
        String email,

        // Minimum length only; BCrypt handles any length. No max that hurts
        // usability, but cap it so nobody submits a megabyte "password".
        @NotBlank @Size(min = 8, max = 100)
        String password) {
}
