package com.elevatebanking.dto.auth;

import java.util.List;

import com.elevatebanking.dto.auth.AuthDTOs.AuthRequest.RoleRequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    private String password;

    @Pattern(regexp = "^0\\d{9}$", message = "Phone number must be 10 digits and start with '0'")
    private String phone;

    @Size(min = 9, max = 12, message = "Identity number must be between 9 and 12 characters")
    private String identityNumber;

    private String fullName;

    @Email(message = "Email is not valid")
    private String email;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date of birth must be in the format YYYY-MM-DD")
    private String dateOfBirth;

    private String status;

    private List<RoleRequest> roles;

}
