package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.UserFieldType;
import jakarta.validation.constraints.Size;

/**
 * Partial update of an organisation user field. The key is immutable; only
 * the provided fields are applied. The type cannot be changed while the field
 * has values (they were stored under the old type), and enabling login
 * requires all existing values to be unique.
 */
public record UpdateOrganisationUserFieldRequest(

        @Size(min = 1, max = 100, message = "Label must be between 1 and 100 characters")
        String label,

        UserFieldType fieldType,

        Boolean loginEnabled
) {
}
