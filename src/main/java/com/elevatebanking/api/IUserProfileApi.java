package com.elevatebanking.api;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.profile.ChangePasswordRequest;
import com.elevatebanking.dto.profile.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "User Profile", description = "APIs for managing user's own profiles")
@SecurityRequirement(name = "Bearer Authentication")
public interface IUserProfileApi {

    @Operation(summary = "Get current user profile")
    ResponseEntity<AuthDTOs.AuthResponse> getCurrentProfile();

    @Operation(summary = "Update current user profile")
    ResponseEntity<AuthDTOs.AuthResponse> updateProfile(UpdateProfileRequest request);

    @Operation(summary = "Change password")
    ResponseEntity<Void> changePassword(ChangePasswordRequest request);
}
