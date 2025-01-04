package com.elevatebanking.service;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;

public interface IAuthService {
    AuthDTOs.AuthResponse login(AuthDTOs.LoginRequest request);

    AuthDTOs.TokenRefreshResponse refreshToken(String refreshToken);

    // OAuth2
    String createGoogleAuthorizationUrl();

    AuthResponse processGoogleCallback(String code);

    void requestPasswordReset(AuthDTOs.PasswordResetRequest request);

    void resetPassword(AuthDTOs.NewPasswordRequest request);

    void logout(String token);

    boolean validateToken(String token);
}
