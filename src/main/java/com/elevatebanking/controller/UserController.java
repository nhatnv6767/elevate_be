package com.elevatebanking.controller;

import com.elevatebanking.dto.auth.AuthDTOs.AuthRequest;
import com.elevatebanking.dto.auth.AuthDTOs.AuthResponse;
import com.elevatebanking.entity.user.Role;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {
    private final IUserService userService;

    @Operation(summary = "Create new user account")
    // @PreAuthorize("hasRole('ROLE_ADMIN')")
    // @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<AuthResponse> createUser(@Valid @RequestBody AuthRequest authRequest,
                                                   HttpServletRequest request) {

        try {
            StringBuilder headers = new StringBuilder("Headers:\n");
            request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                headers.append(headerName).append(": ").append(request.getHeader(headerName)).append("\n");
            });

            System.out.println("Request body: " + authRequest);
            System.out.println(headers.toString());

            System.out.println("Received password: '" + authRequest.getPassword() + "'");

            String currentUserRole = SecurityContextHolder.getContext().getAuthentication().getAuthorities().toString();
            System.out.println("Current user role: " + currentUserRole);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            System.out.println("Principal: " + auth.getPrincipal());
            System.out.println("Credentials: " + auth.getCredentials());
            System.out.println("Authorities: " + auth.getAuthorities());
            System.out.println("Details: " + auth.getDetails());
            AuthResponse createdUser = userService.createUser(authRequest);
            return ResponseEntity.ok(createdUser);
        } catch (Exception e) {
            // TODO: handle exception
            throw e;
        }
    }

    @Operation(summary = "Get all users")
    // @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    public ResponseEntity<List<AuthResponse>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        List<AuthResponse> response = users.stream()
                .map(user -> AuthResponse.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .phone(user.getPhone())
                        .identityNumber(user.getIdentityNumber())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .dateOfBirth(user.getDateOfBirth())
                        .roles(user.getRoles().stream().map(Role::getName).toArray(String[]::new)).build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

}
