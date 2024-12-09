package com.elevatebanking.service.imp;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.profile.ChangePasswordRequest;
import com.elevatebanking.dto.profile.UpdateProfileRequest;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.mapper.UserMapper;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.service.IUserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements IUserProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public AuthDTOs.AuthResponse getCurrentProfile() {
        User currentUser = getCurrentUser();
        return userMapper.userToAuthResponse(currentUser);
    }

    @Override
    @Transactional
    public AuthDTOs.AuthResponse updateProfile(UpdateProfileRequest request) {
        User currentUser = getCurrentUser();

        if (request.getPhone() != null) {
            currentUser.setPhone(request.getPhone());
        }
        if (request.getFullName() != null) {
            currentUser.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            currentUser.setEmail(request.getEmail());
        }
        if (request.getDateOfBirth() != null) {
            currentUser.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
        }

        User updatedUser = userRepository.save(currentUser);

        return userMapper.userToAuthResponse(updatedUser);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        User currentUser = getCurrentUser();

        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            throw new InvalidOperationException("Current password is incorrect");
        }

        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByUsername(authentication.getName()).orElseThrow(() -> new InvalidOperationException("User not found"));
    }
}
