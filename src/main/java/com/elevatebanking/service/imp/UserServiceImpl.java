package com.elevatebanking.service.imp;

import com.elevatebanking.dto.auth.AuthDTOs;
import com.elevatebanking.dto.auth.AuthDTOs.AuthRequest;
import com.elevatebanking.entity.enums.UserStatus;
import com.elevatebanking.entity.user.Role;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.CustomDuplicateResourceException;
import com.elevatebanking.exception.CustomResourceNotFoundException;
import com.elevatebanking.mapper.UserMapper;
import com.elevatebanking.repository.RoleRepository;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.service.IUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthDTOs.AuthResponse createUser(AuthRequest authRequest) {
        try {
            log.debug("Starting to create user: {}", authRequest.getUsername());
            if (existsByUsername(authRequest.getUsername())) {
                throw new CustomDuplicateResourceException("Username already exists");
            }
            if (existsByEmail(authRequest.getEmail())) {
                throw new CustomDuplicateResourceException("Email already exists");
            }

            // User user = new User();
            // user.setUsername(authRequest.getUsername());
            // user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
            // user.setPhone(authRequest.getPhone());
            // user.setIdentityNumber(authRequest.getIdentityNumber());
            // user.setFullName(authRequest.getFullName());
            // user.setEmail(authRequest.getEmail());
            // user.setDateOfBirth(LocalDate.parse(authRequest.getDateOfBirth()));
            //
            // user.setRoles(new ArrayList<>());

            User user = userMapper.authRequestToUser(authRequest);
            user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
            user.setRoles(new ArrayList<>());

            // add default CUSTOMER role
            Role customerRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> {
                        log.error("Default role ROLE_USER not found");
                        return new CustomResourceNotFoundException("Default role ROLE_USER not found");
                    });

            user.setRoles(new ArrayList<>(Collections.singletonList(customerRole)));
            User savedUser = userRepository.save(user);
            log.debug("User created successfully: {}", savedUser.getUsername());
            // return AuthResponse.builder()
            // .userId(savedUser.getId())
            // .username(savedUser.getUsername())
            // .phone(savedUser.getPhone())
            // .identityNumber(savedUser.getIdentityNumber())
            // .fullName(savedUser.getFullName())
            // .email(savedUser.getEmail())
            // .dateOfBirth(savedUser.getDateOfBirth())
            // .roles(savedUser.getRoles().stream().map(Role::getName).toArray(String[]::new))
            // .build();

            return userMapper.userToAuthResponse(savedUser);
        } catch (Exception e) {
            // TODO: handle exception
            throw new RuntimeException("Could not create user: " + e.getMessage());
        }
    }

    @Override
    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User updateUser(User user) {
        User existingUser = getUserById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getPhone() != null) {
            existingUser.setPhone(user.getPhone());
        }

        if (user.getIdentityNumber() != null) {
            existingUser.setIdentityNumber(user.getIdentityNumber());
        }

        if (user.getFullName() != null) {
            existingUser.setFullName(user.getFullName());
        }

        if (user.getEmail() != null) {
            existingUser.setEmail(user.getEmail());
        }

        if (user.getDateOfBirth() != null) {
            existingUser.setDateOfBirth(user.getDateOfBirth());
        }

        // dont update sensitive fields
        existingUser.setPassword(
                user.getPassword() != null ? passwordEncoder.encode(user.getPassword()) : existingUser.getPassword());
        existingUser.setRoles(user.getRoles() != null ? user.getRoles() : existingUser.getRoles());
        existingUser.setStatus(user.getStatus() != null ? user.getStatus() : existingUser.getStatus());
        return userRepository.save(existingUser);
    }

    @Override
    public void deleteUser(String id) {
        User user = getUserById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
    }

    @Override
    public User changeUserStatus(String id, UserStatus status) {
        User user = getUserById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public User updatePassword(String id, String newPassword) {
        User user = getUserById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }
}
