package com.elevatebanking.dto.profile;

import com.elevatebanking.entity.base.EntityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Pattern(regexp = EntityConstants.PASSWORD_PATTERN, message = EntityConstants.INVALID_PASSWORD)
    private String newPassword;

}
