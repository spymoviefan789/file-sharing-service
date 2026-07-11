package com.quickprint.auth;

import com.quickprint.auth.dto.RegisterRequest;
import com.quickprint.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // @Valid triggers the DTO's validation annotations. On success we return
    // 201 Created with no body — deliberately nothing about the user, so we
    // can never leak the hash or the internal id.
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
    }

    // Who is logged in? Requires a valid session — otherwise the security entry
    // point returns 401 before we get here. Lets the frontend check auth state.
    // (login/logout are handled by Spring Security filters, not this controller.)
    @GetMapping("/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("email", principal.getName());
    }
}
