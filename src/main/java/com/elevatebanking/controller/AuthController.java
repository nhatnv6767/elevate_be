package com.elevatebanking.controller;

import com.elevatebanking.api.IAuthApi;
import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
import com.elevatebanking.dto.auth.AuthDTOs.NewPasswordRequest;
import com.elevatebanking.dto.auth.AuthDTOs.PasswordResetRequest;
import com.elevatebanking.service.IAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController implements IAuthApi {

    private final IAuthService authService;

    @Override
    public ResponseEntity<AuthResponse> login(@RequestBody AuthDTOs.AuthRequest request) {
        AuthResponse response = authService.login(request);
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
}
