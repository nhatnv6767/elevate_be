package com.elevatebanking.service;

import com.elevatebanking.dto.auth.AuthDTOs;

public interface IAuthService {
    AuthDTOs.AuthResponse login(AuthDTOs.AuthRequest request);

    AuthDTOs.AuthResponse register(AuthDTOs.RegisterRequest request);

    AuthDTOs.AuthResponse refreshToken(String token);

    void logout(String token);
}
