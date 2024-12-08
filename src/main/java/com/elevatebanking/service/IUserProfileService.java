package com.elevatebanking.service;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.profile.ChangePasswordRequest;
import com.elevatebanking.dto.profile.UpdateProfileRequest;

public interface IUserProfileService {
    AuthDTOs.AuthResponse getCurrentProfile();

    AuthDTOs.AuthResponse updateProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);
}
