package com.elevatebanking.controller;

import com.elevatebanking.api.IAuthApi;
import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
import com.elevatebanking.dto.auth.AuthDTOs.NewPasswordRequest;
import com.elevatebanking.dto.auth.AuthDTOs.PasswordResetRequest;
import com.elevatebanking.dto.forgot.ForgotPasswordRequest;
import com.elevatebanking.dto.forgot.ResetPasswordRequest;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.InvalidTokenException;
import com.elevatebanking.service.IAuthService;
import com.elevatebanking.service.IUserService;
import com.elevatebanking.service.nonImp.PasswordResetTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AuthController implements IAuthApi {

    private final PasswordResetTokenService tokenService;
    private final IUserService userService;
    private final PasswordEncoder passwordEncoder;
    private final IAuthService authService;

    @Override
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthDTOs.LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthDTOs.TokenRefreshResponse> refreshToken(@RequestHeader("Authorization") String bearerToken) {
        log.info("Processing refresh token request");
        try {
            if (!bearerToken.startsWith("Bearer ")) {
                throw new Exception("Invalid token format - Bearer prefix missing");
            }

            String refreshToken = bearerToken.substring(7);
            AuthDTOs.TokenRefreshResponse response = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing refresh token request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    @GetMapping("/oauth2/google")
    public ResponseEntity<String> googleLogin() {
        String authorizationUrl = authService.createGoogleAuthorizationUrl();
        return ResponseEntity.ok(authorizationUrl);
    }

    @GetMapping("/oauth2/callback/google")
    public ResponseEntity<AuthResponse> googleCallback(@RequestParam("code") String code) {
        AuthResponse response = authService.processGoogleCallback(code);
        return ResponseEntity.ok(response);
    }

//    @Override
//    public ResponseEntity<AuthResponse> requestPasswordReset(PasswordResetRequest request) {
//        authService.requestPasswordReset(request);
//        return ResponseEntity.ok().build();
//    }

    @Override
    public ResponseEntity<AuthResponse> resetPassword(NewPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        log.info("Processing logout request");
        if (token != null && token.startsWith("Bearer ")) {
            authService.logout(token.substring(7));
        }
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping("/request-password-reset")
    public ResponseEntity<AuthResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        log.info("Processing password reset request for user: {}", request.getUsername());
        authService.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        tokenService.processForgotPassword(request.getEmail());
        Map<String, String> response = new HashMap<>();
        response.put("message", "If the email exists, you will receive reset instructions");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(
                response
        );
    }

    @PostMapping("/validate-reset-token")
    public ResponseEntity<?> validateResetToken(@RequestParam String token) {
        log.info("Validating reset token");
        boolean isValid = tokenService.validateToken(token).isPresent();
        Map<String, Object> response = new HashMap<>();
        response.put("valid", isValid);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

//    @Override
//    @PostMapping("/logout")
//    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
//        log.info("Processing logout request");
//        if (token != null && token.startsWith("Bearer ")) {
//            authService.logout(token.substring(7));
//        }
//        return ResponseEntity.ok().build();
//    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String email = tokenService.validateToken(request.getToken()).orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

        User user = userService.getUserByEmail(email).orElseThrow(() -> new InvalidTokenException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userService.saveNewPassword(user);

        tokenService.invalidateToken(request.getToken());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Password has been reset successfully");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(
                response
        );
    }

    @GetMapping("/check-auth")
    public ResponseEntity<?> checkAuth(HttpServletRequest request) {
        log.info("Checking authentication status");
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            boolean isValid = authService.validateToken(token);
            return ResponseEntity.ok(Map.of(
                    "authenticated", isValid,
                    "timestamp", LocalDateTime.now().toString()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "authenticated", false,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
