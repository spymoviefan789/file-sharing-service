package com.quickprint.user;

import com.quickprint.auth.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Business logic for users. The controller stays thin (HTTP only); the rules
// — "email must be unique", "hash the password" — live here.
@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    // Constructor injection: Spring passes in the repository and the BCrypt
    // encoder bean. Preferred over @Autowired fields — makes dependencies
    // explicit and the class easy to test.
    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }
        String passwordHash = passwordEncoder.encode(request.password());
        return users.save(new User(request.email(), passwordHash));
    }
}
