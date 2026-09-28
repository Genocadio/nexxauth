package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddUserPhoneRequest(
        @NotBlank(message = "Phone is required")
        @Size(max = 30, message = "Phone must be at most 30 characters")
        String phone,

        Boolean isPrimary
) {
}
