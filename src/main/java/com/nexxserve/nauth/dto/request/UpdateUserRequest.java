package com.nexxserve.nauth.dto.request;

import com.nexxserve.nauth.entity.Role;
import jakarta.validation.constraints.Size;

/**
 * Partial admin update of another user: only provided fields are applied.
 */
public record UpdateUserRequest(

        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
        String lastName,

        @Size(min = 1, max = 30, message = "Phone must be between 1 and 30 characters")
        String phone,

        Role role,

        Boolean enabled
) {
}
