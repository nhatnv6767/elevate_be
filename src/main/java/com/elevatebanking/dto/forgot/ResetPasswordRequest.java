package com.elevatebanking.dto.forgot;

import com.elevatebanking.entity.base.EntityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "New password is required")
    @Pattern(regexp = EntityConstants.PASSWORD_PATTERN, message = EntityConstants.INVALID_PASSWORD)
    private String newPassword;
}
