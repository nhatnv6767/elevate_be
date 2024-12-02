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

    @Operation(summary = "Request password reset", description = "Request password reset")
    @ApiResponse(responseCode = "200", description = "Password reset request sent successfully")
    ResponseEntity<AuthDTOs.AuthResponse> requestPasswordReset(AuthDTOs.PasswordResetRequest request);

    @Operation(summary = "Reset password", description = "Reset password")
    @ApiResponse(responseCode = "200", description = "Password reset successfully")
    ResponseEntity<AuthDTOs.AuthResponse> resetPassword(AuthDTOs.NewPasswordRequest request);

    @Operation(summary = "Logout", description = "Logout user and invalidate token")
    ResponseEntity<Void> logout(String token);

}
