package com.elevatebanking.service.nonImp;

import com.elevatebanking.dto.GoogleUserInfo;
import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
import com.elevatebanking.entity.enums.UserStatus;
import com.elevatebanking.entity.user.Role;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.mapper.UserMapper;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.security.JwtTokenProvider;
import com.elevatebanking.service.IAuthService;
import com.github.dockerjava.api.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final BAKPasswordResetTokenService tokenService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserMapper userMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public AuthResponse login(AuthDTOs.LoginRequest request) {
        try {

            Optional<User> optionalUser = userRepository.findByUsername(request.getUsername());

            if (!optionalUser.isPresent()) {
                throw new BadCredentialsException("Invalid username or password");
            }

            User user = optionalUser.get();

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new InvalidOperationException("Account is not active");
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));

            // gen token
            JwtTokenProvider.TokenPair tokenPair = tokenProvider.generateTokenPair(request.getUsername());

            AuthResponse response = userMapper.userToAuthResponse(user);
            response.setAccessToken(tokenPair.getAccessToken());
            response.setRefreshToken(tokenPair.getRefreshToken());
            response.setExpiresIn(tokenProvider.getExpirationTime());
            return response;
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
        emailService.sendResetPasswordEmail(user.getEmail(), token, request.getUsername());
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

    @Override
    public boolean validateToken(String token) {
        return tokenProvider.validateToken(token);
    }

//    private AuthResponse createAuthResponse(Authentication authentication) {
//        String username = authentication.getName();
//        User user = userRepository.findByUsername(username)
//                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
//        return AuthResponse.builder()
//                .userId(user.getId())
//                .username(username)
//                .accessToken(tokenProvider.generateToken(username))
//                .expiresIn(tokenProvider.getExpirationTime())
//                .roles(user.getRoles().stream().map(Role::getName).toArray(String[]::new))
//                .build();
//
//    }

    @Override
    public String createGoogleAuthorizationUrl() {
        ClientRegistration googleClient = clientRegistrationRepository.findByRegistrationId("google");

        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest
                .authorizationCode()
                .clientId(googleClient.getClientId())
                .authorizationUri(googleClient.getProviderDetails().getAuthorizationUri())
                .redirectUri(googleClient.getRedirectUri())
                .scopes(googleClient.getScopes())
                .state(UUID.randomUUID().toString())
                .build();

        MultiValueMap<String, String> additionalParams = new LinkedMultiValueMap<>();
        if (authorizationRequest.getAdditionalParameters() != null) {
            Map<String, String> stringParams = authorizationRequest.getAdditionalParameters().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
            additionalParams.setAll(stringParams);
        }

        return UriComponentsBuilder
                .fromUriString(authorizationRequest.getAuthorizationUri())
                .queryParams(additionalParams)
                .queryParam("client_id", authorizationRequest.getClientId())
                .queryParam("scope", String.join(" ", authorizationRequest.getScopes()))
                .queryParam("redirect_uri", authorizationRequest.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", authorizationRequest.getState())
                .build()
                .toUriString();
    }


    @Override
    public AuthResponse processGoogleCallback(String code) {
        // TODO Auto-generated method stub
        try {
            ClientRegistration googleClient = clientRegistrationRepository.findByRegistrationId("google");

            GoogleUserInfo userInfo = getGoogleUserInfo(code, googleClient);

            User user = userRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> createNewGoogleUser(userInfo));

            JwtTokenProvider.TokenPair tokenPair = tokenProvider.generateTokenPair(user.getUsername());
            String accessToken = tokenPair.getAccessToken();
            return AuthResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .accessToken(accessToken)
//                    .accessToken(tokenProvider.generateToken(user.getUsername()))
                    .expiresIn(tokenProvider.getExpirationTime())
                    .roles(user.getRoles().stream().map(Role::getName).toArray(String[]::new))
                    .build();

        } catch (Exception e) {
            // TODO: handle exception
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),
                    "Error processing Google callback", e);
        }
    }

    private GoogleUserInfo getGoogleUserInfo(String code, ClientRegistration googleClient) {
        // Gọi Google API để lấy thông tin người dùng
        String tokenUri = googleClient.getProviderDetails().getTokenUri();
        String userInfoUri = googleClient.getProviderDetails().getUserInfoEndpoint().getUri();

        // Tạo một đối tượng để gửi yêu cầu lấy access token
        MultiValueMap<String, String> tokenRequestParams = new LinkedMultiValueMap<>();
        tokenRequestParams.add("code", code);
        tokenRequestParams.add("client_id", googleClient.getClientId());
        tokenRequestParams.add("client_secret", googleClient.getClientSecret());
        tokenRequestParams.add("redirect_uri", googleClient.getRedirectUri());
        tokenRequestParams.add("grant_type", "authorization_code");

        // Gửi yêu cầu POST để lấy access token
        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenUri, tokenRequestParams, Map.class);
        String accessToken = (String) tokenResponse.getBody().get("access_token");

        // Gọi Google API để lấy thông tin người dùng
        return restTemplate.getForObject(userInfoUri + "?access_token=" + accessToken, GoogleUserInfo.class);
    }

    private User createNewGoogleUser(GoogleUserInfo userInfo) {
        User user = new User();
        user.setEmail(userInfo.getEmail());
        user.setUsername(userInfo.getEmail());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        return userRepository.save(user);
    }

    @Override
    public AuthDTOs.TokenRefreshResponse refreshToken(String refreshToken) {
        String newAccessToken = tokenProvider.refreshAccessToken(refreshToken);

        AuthDTOs.TokenRefreshResponse response = new AuthDTOs.TokenRefreshResponse();
        response.setAccessToken(newAccessToken);
        response.setExpiresIn(tokenProvider.getExpirationTime());
        response.setRefreshToken(refreshToken);

        return response;
    }
}
