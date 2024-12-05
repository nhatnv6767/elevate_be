package com.elevatebanking.service.nonImp;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.security.authentication.AuthenticationManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.auth.AuthDTOs.AuthRequest;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
import com.elevatebanking.security.JwtTokenProvider;
import com.elevatebanking.service.nonImp.EmailService;

public class AuthServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private EmailService emailService;

    @Test
    void testLogin() {
        AuthRequest request = new AuthRequest("username", "password");
        AuthResponse response = authService.login(request);
        assertNotNull(response);
        assertEquals("username", response.getUsername());
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getExpiresIn());
        assertNotNull(response.getRoles());

    }

    @Test
    void testLogout() {
        AuthRequest request = new AuthRequest("username", "password");
        authService.logout("accessToken");
        assertNull(authService.login(request));

    }

    @Test
    void testRequestPasswordReset() {
        AuthDTOs.PasswordResetRequest request = AuthDTOs.PasswordResetRequest.builder()
                .username("username")
                .email("email@example.com")
                .build();
        authService.requestPasswordReset(request);
        // Kiểm tra xem email có được gửi không
        verify(emailService, times(1)).sendPasswordResetEmail(eq(request.getEmail()), toString());
    }

    @Test
    void testResetPassword() {
        AuthDTOs.NewPasswordRequest request = AuthDTOs.NewPasswordRequest.builder()
                .token("token")
                .newPassword("newPassword")
                .build();
        authService.resetPassword(request);
        // Kiểm tra xem mật khẩu có được reset không
        verify(authService, times(1)).resetPassword(eq(request));
    }
}
