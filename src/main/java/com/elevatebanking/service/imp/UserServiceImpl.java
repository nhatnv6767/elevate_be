package com.elevatebanking.service.imp;

import com.elevatebanking.entity.enums.UserStatus;
import com.elevatebanking.entity.user.Role;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.repository.RoleRepository;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.DuplicateResourceException;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class UserServiceImpl implements IUserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }

    @Override
    public User createUser(User user) {
        if (existsByUsername(user.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(UserStatus.ACTIVE);

        // add default CUSTOMER role
        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseThrow(() -> new ResourceNotFoundException("Default role not found"));
        user.getRoles().add(customerRole);
        return userRepository.save(user);
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
        User existingUser = getUserById(user.getId()).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // dont update sensitive fields
        user.setPassword(existingUser.getPassword());
        user.setRoles(existingUser.getRoles());
        user.setStatus(existingUser.getStatus());
        return userRepository.save(user);
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
