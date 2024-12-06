package com.elevatebanking.service.nonImp;

import com.elevatebanking.dto.auth.AuthDTOs.AuthRequest;
import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
import com.elevatebanking.entity.user.Role;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.security.JwtTokenProvider;
import com.elevatebanking.service.IAuthService;
import com.github.dockerjava.api.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordResetTokenService tokenService;

    @Override
    public AuthResponse login(AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));
            return createAuthResponse(authentication);
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid username or password");
        }
    }

    @Override
    public void requestPasswordReset(AuthDTOs.PasswordResetRequest request) {
        User user = userRepository.findByUsernameAndEmail(
                request.getUsername(),
                request.getEmail()).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String token = tokenService.createToken(user);
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    @Override
    public void resetPassword(AuthDTOs.NewPasswordRequest request) {
        String username = tokenService.validateTokenAndGetUsername(request.getToken());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        tokenService.invalidateToken(request.getToken());
    }

    @Override
    public void logout(String token) {
        tokenProvider.blacklistToken(token);
    }

    private AuthResponse createAuthResponse(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return AuthResponse.builder()
                .userId(user.getId())
                .username(username)
                .accessToken(tokenProvider.generateToken(username))
                .expiresIn(tokenProvider.getExpirationTime())
                .roles(user.getRoles().stream().map(Role::getName).toArray(String[]::new))
                .build();

    }
}
