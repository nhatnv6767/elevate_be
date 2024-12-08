package com.elevatebanking.service;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.auth.UserUpdateRequest;
import com.elevatebanking.entity.enums.UserStatus;
import com.elevatebanking.entity.user.User;

import java.util.List;
import java.util.Optional;

public interface IUserService {
    AuthDTOs.AuthResponse createUser(AuthDTOs.AuthRequest authRequest);

    Optional<User> getUserById(String id);

    Optional<User> getUserByUsername(String username);

    List<User> getAllUsers();

    User updateUser(String id, UserUpdateRequest updateRequest);

    void deleteUser(String id);

    User changeUserStatus(String id, UserStatus status);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User updatePassword(String id, String newPassword);
}
