// package com.elevatebanking.service.nonImp;

// import org.junit.jupiter.api.Assertions;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.security.authentication.AuthenticationManager;

// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.times;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;

// import java.util.Optional;

// import com.elevatebanking.dto.auth.AuthDTOs;
// import com.elevatebanking.dto.auth.AuthDTOs.AuthRequest;
// import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
// import com.elevatebanking.security.JwtTokenProvider;
// import com.elevatebanking.entity.user.User;
// import com.elevatebanking.repository.UserRepository;

// import
// org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.Authentication;

// @ExtendWith(MockitoExtension.class)
// public class AuthServiceTest {

// @Mock
// private AuthService authService;

// @Mock
// private AuthenticationManager authenticationManager;

// @Mock
// private JwtTokenProvider tokenProvider;

// @Mock
// private EmailService emailService;

// @Mock
// private TokenService tokenService;

// @Mock
// private UserRepository userRepository;

// @BeforeEach
// void setUp() {
// authService = new AuthService(authenticationManager, tokenProvider,
// emailService, tokenService, userRepository);
// }

// @Test
// void testLogin() {
// AuthRequest request = new AuthRequest("username", "password");
// User user = new User();
// user.setUsername("username");
// String accessToken = "mockAccessToken";

// Authentication authentication = new UsernamePasswordAuthenticationToken(user,
// null);
// when(authenticationManager.authenticate(any())).thenReturn(authentication);
// when(authService.login(request)).thenCallRealMethod();

// AuthResponse response = authService.login(request);
// Assertions.assertNotNull(response);
// Assertions.assertEquals("username", response.getUsername());
// Assertions.assertNotNull(response.getAccessToken());
// Assertions.assertNotNull(response.getExpiresIn());
// Assertions.assertNotNull(response.getRoles());
// }

// @Test
// void testLogout() {
// String accessToken = "mockAccessToken";
// authService.logout(accessToken);
// verify(tokenProvider, times(1)).blacklistToken(accessToken);
// }

// @Test
// void testRequestPasswordReset() {
// AuthDTOs.PasswordResetRequest request =
// AuthDTOs.PasswordResetRequest.builder()
// .username("username")
// .email("email@example.com")
// .build();

// authService.requestPasswordReset(request);
// verify(emailService, times(1)).sendPasswordResetEmail(eq(request.getEmail()),
// any(String.class));
// }

// @Test
// void testResetPassword() {
// AuthDTOs.NewPasswordRequest request = AuthDTOs.NewPasswordRequest.builder()
// .token("token")
// .newPassword("newPassword")
// .build();

// when(tokenService.validateTokenAndGetUsername(request.getToken())).thenReturn("username");
// User user = new User();
// user.setUsername("username");
// when(userRepository.findByUsername("username")).thenReturn(Optional.of(user));

// authService.resetPassword(request);
// verify(userRepository, times(1)).save(user);
// verify(tokenService, times(1)).invalidateToken(request.getToken());
// }

// public interface TokenService {
// // ... existing methods ...
// String validateTokenAndGetUsername(String token);

// void invalidateToken(String token);
// }
// }
