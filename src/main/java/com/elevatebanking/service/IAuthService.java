package com.elevatebanking.service;

import com.elevatebanking.dto.auth.AuthDTOs;

public interface IAuthService {
    AuthDTOs.AuthResponse login(AuthDTOs.AuthRequest request);

    void requestPasswordReset(AuthDTOs.PasswordResetRequest request);

    void resetPassword(AuthDTOs.NewPasswordRequest request);
    

    void logout(String token);
}
