package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @ValidPassword
        String newPassword
) {
}
