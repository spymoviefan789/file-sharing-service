package com.quickprint.security;

import com.quickprint.user.User;
import com.quickprint.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// The bridge Spring Security uses to find a user by its login name (here, the
// email). Spring then compares the submitted password against the stored hash
// using our BCryptPasswordEncoder — we only provide the lookup.
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = users.findByEmail(email)
                // Same message whether the email is unknown or the password is
                // wrong — never reveal which emails exist (enumeration defense).
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("USER")
                .build();
    }
}
