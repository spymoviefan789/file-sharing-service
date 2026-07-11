package com.quickprint.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // The bean UserService injects. BCrypt with default strength (cost 10).
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Defines which requests are public and which need a login. Without this,
    // Spring Security locks every endpoint (including our static pages).
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // /error is where Spring forwards to render exceptions;
                        // it must be public or every thrown error becomes a 403.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                // ponytail: CSRF disabled for now — only the JSON register
                // endpoint exists and there is no session yet. Re-enable and
                // scope it properly in the login/session slice (state-changing
                // authenticated actions like upload/delete are where it matters).
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
