package com.elevatebanking.service;

import com.elevatebanking.entity.enums.UserStatus;
import com.elevatebanking.entity.user.User;

import java.util.List;
import java.util.Optional;

public interface IUserService {
    User createUser(User user);

    Optional<User> getUserById(String id);

    Optional<User> getUserByUsername(String username);

    List<User> getAllUsers();

    User updateUser(User user);

    void deleteUser(String id);

    User changeUserStatus(String id, UserStatus status);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User updatePassword(String id, String newPassword);
}