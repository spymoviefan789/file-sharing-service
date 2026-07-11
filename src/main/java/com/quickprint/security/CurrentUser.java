package com.quickprint.security;

import com.quickprint.user.User;
import com.quickprint.user.UserRepository;
import org.springframework.stereotype.Component;

import java.security.Principal;

// Resolves the authenticated User entity from the session principal. The
// principal's name is the email (see AppUserDetailsService).
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User require(Principal principal) {
        return users.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getName()));
    }
}
