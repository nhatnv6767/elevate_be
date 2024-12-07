package com.elevatebanking.dto.auth;

import java.time.LocalDate;
import java.util.List;

import com.elevatebanking.entity.base.EntityConstants;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthRequest {
        @NotBlank(message = "Username is required")
        private String username;

        @NotBlank(message = "Password is required")
        // @Pattern(regexp = EntityConstants.PASSWORD_PATTERN, message =
        // EntityConstants.INVALID_PASSWORD)
        @Pattern(regexp = EntityConstants.PASSWORD_PATTERN, message = EntityConstants.INVALID_PASSWORD)
        private String password;

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^0\\d{9}$", message = "Phone number must be 10 digits and start with '0'")
        private String phone;

        @NotBlank(message = "Identity number is required")
        @Size(min = 9, max = 12, message = "Identity number must be between 9 and 12 characters")
        private String identityNumber;

        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        private String email;

        @NotBlank(message = "Date of birth is required")
        // Bạn có thể sử dụng @Past hoặc @Pattern để xác thực định dạng ngày
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date of birth must be in the format YYYY-MM-DD")
        private String dateOfBirth;

        @NotEmpty(message = "At least one role is required")
        @Valid
        private List<RoleRequest> roles;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RoleRequest {
            @NotBlank(message = "Role is required")
            private String role;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String userId;
        private String username;
        private String password;
        private String phone;
        private String identityNumber;
        private String fullName;
        private String email;
        private LocalDate dateOfBirth;
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private Long expiresIn;
        private String[] roles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasswordResetRequest {
        @NotBlank(message = "Username is required")
        private String username;
        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        private String email;
        // @NotBlank(message = "Token is required")
        // private String token;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewPasswordRequest {
        @NotBlank(message = "Token is required")
        private String token;

        @NotBlank(message = "New password is required")
        @Pattern(regexp = EntityConstants.PASSWORD_PATTERN, message = "Password must contain at least one lowercase letter, one uppercase letter, one digit, and must be at least 8 characters long")
        private String newPassword;
    }
}
