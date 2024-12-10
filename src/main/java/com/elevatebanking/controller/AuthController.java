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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController implements IAuthApi {

    private final PasswordResetTokenService tokenService;
    private final IUserService userService;
    private final PasswordEncoder passwordEncoder;
    private final IAuthService authService;

    @Override
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthDTOs.AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
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

    @Override
    public ResponseEntity<AuthResponse> requestPasswordReset(PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<AuthResponse> resetPassword(NewPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> logout(String token) {
        authService.logout(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        tokenService.processForgotPassword(request.getEmail());
        return ResponseEntity.ok(
                Map.of(
                        "message", "If the email exists, you will receive reset instructions"
                )
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String email = tokenService.validateToken(request.getToken()).orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

        User user = userService.getUserByEmail(email).orElseThrow(() -> new InvalidTokenException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userService.saveNewPassword(user);

        tokenService.invalidateToken(request.getToken());

        return ResponseEntity.ok(
                Map.of(
                        "message", "Password has been reset successfully"
                )
        );
    }
}
