package com.quickprint.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

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
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // The receiving side needs no account: enter nickname + code.
                        .requestMatchers("/api/receive/**").permitAll()
                        // /error is where Spring forwards to render exceptions;
                        // it must be public or every thrown error becomes a 403.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                // Session login: this filter handles POST /api/auth/login,
                // checks the password via AppUserDetailsService, and stores the
                // user in the session. We return status codes, not HTML redirects.
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> response.setStatus(HttpStatus.OK.value()))
                        .failureHandler((request, response, exception) -> response.setStatus(HttpStatus.UNAUTHORIZED.value())))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.OK.value())))
                // For an API, unauthenticated access should be 401, not a
                // redirect to a login page.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // ponytail: CSRF still disabled. It guards authenticated
                // state-changing requests, of which we have none yet. Re-enable
                // it in the upload/delete slice (which touches the fetch layer).
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
