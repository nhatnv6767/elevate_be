package com.elevatebanking.api;

import com.elevatebanking.dto.auth.AuthDTOs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Authentication", description = "Authentication APIs")
public interface IAuthApi {

    @Operation(summary = "Login", description = "Authenticate user and generate access token")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AuthDTOs.AuthResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid username or password")
    ResponseEntity<AuthDTOs.AuthResponse> login(AuthDTOs.AuthRequest request);

    @Operation(summary = "Register", description = "Register a new user")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = AuthDTOs.AuthResponse.class)))
    ResponseEntity<AuthDTOs.AuthResponse> register(AuthDTOs.RegisterRequest request);

    @Operation(summary = "Refresh JWT token", description = "Refresh JWT token using refresh token")
    ResponseEntity<AuthDTOs.AuthResponse> refreshToken(String token);

    @Operation(summary = "Logout", description = "Logout user and invalidate token")
    ResponseEntity<Void> logout(String token);

}
