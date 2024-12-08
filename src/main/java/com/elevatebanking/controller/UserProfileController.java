package com.elevatebanking.controller;

import com.elevatebanking.api.IUserProfileApi;
import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.profile.ChangePasswordRequest;
import com.elevatebanking.dto.profile.UpdateProfileRequest;
import com.elevatebanking.service.IUserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class UserProfileController implements IUserProfileApi {

    private final IUserProfileService userProfileService;

    @Override
    @GetMapping
    public ResponseEntity<AuthDTOs.AuthResponse> getCurrentProfile() {
        return ResponseEntity.ok(userProfileService.getCurrentProfile());
    }

    @Override
    @PutMapping
    public ResponseEntity<AuthDTOs.AuthResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(request));
    }

    @Override
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(request);
        return ResponseEntity.ok().build();
    }
}
