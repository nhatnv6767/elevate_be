package com.elevatebanking.service.nonImp;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.security.JwtTokenProvider;
import com.elevatebanking.service.IAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthDTOs.AuthResponse login(AuthDTOs.AuthRequest request) {
        return null;
    }

    @Override
    public void requestPasswordReset(AuthDTOs.PasswordResetRequest request) {

    }

    @Override
    public void resetPassword(AuthDTOs.NewPasswordRequest request) {

    }

    @Override
    public void logout(String token) {

    }
}
