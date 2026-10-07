package com.digitalwill.auth.web;

import com.digitalwill.auth.security.UserPrincipal;
import com.digitalwill.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = Objects.requireNonNull(authService);
    }

    public record RegisterRequest(
            @NotBlank(message = "Email must not be blank")
            @Email(message = "Email must be valid")
            String email,

            @NotBlank(message = "Password must not be blank")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            @NotBlank(message = "Full name must not be blank")
            String fullName
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email must not be blank")
            String email,

            @NotBlank(message = "Password must not be blank")
            String password
    ) {}

    @PostMapping("/register")
    public ResponseEntity<AuthService.AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.AuthResponse response = authService.register(request.email(), request.password(), request.fullName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthService.AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResponse response = authService.login(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthService.UserProfile> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        AuthService.UserProfile profile = authService.getProfile(principal.getId());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
