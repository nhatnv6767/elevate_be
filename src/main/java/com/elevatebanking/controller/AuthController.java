package com.elevatebanking.controller;

import com.elevatebanking.api.IAuthApi;
import com.elevatebanking.dto.auth.AuthDTOs;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController implements IAuthApi {

//    private final IAu

    @Override
    public ResponseEntity<AuthDTOs.AuthResponse> login(AuthDTOs.AuthRequest request) {
        return null;
    }

    @Override
    public ResponseEntity<AuthDTOs.AuthResponse> register(AuthDTOs.RegisterRequest request) {
        return null;
    }

    @Override
    public ResponseEntity<AuthDTOs.AuthResponse> refreshToken(String token) {
        return null;
    }

    @Override
    public ResponseEntity<Void> logout(String token) {
        return null;
    }
}
