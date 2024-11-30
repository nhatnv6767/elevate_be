package com.elevatebanking.service.imp;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.security.JwtTokenProvider;
import com.elevatebanking.service.IAuthService;
import com.github.dockerjava.api.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthDTOs.AuthResponse login(AuthDTOs.AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        return createAuthResponse(authentication);
    }

    @Override
    @Transactional
    public AuthDTOs.AuthResponse register(AuthDTOs.RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());

        userRepository.save(user);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        return createAuthResponse(authentication);
    }

    @Override
    public AuthDTOs.AuthResponse refreshToken(String token) {
        return null;
    }

    @Override
    public void logout(String token) {

    }

    private AuthDTOs.AuthResponse createAuthResponse(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UnauthorizedException("User not found"));
        return AuthDTOs.AuthResponse.builder()
                .userId(user.getId())
                .username(username)
                .accessToken(tokenProvider.generateToken(username))
                .refreshToken(tokenProvider.generateRefreshToken(username))
                .expiresIn(tokenProvider.getExpirationTime())
                .roles(user.getRoles().stream().map(role -> role.getName()).toArray(String[]::new))
                .build();
    }
}
