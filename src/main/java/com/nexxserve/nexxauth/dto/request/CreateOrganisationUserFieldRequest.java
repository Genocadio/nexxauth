package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.UserFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Define an organisation user field. The key is the machine name used in user
 * {@code metadata} (lowercase letters, digits and hyphens) and must be unique
 * per organisation; the label is human-readable.
 */
public record CreateOrganisationUserFieldRequest(

        @NotBlank(message = "Key is required")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Key must be lowercase letters, digits and hyphens (e.g. employee-id)")
        @Size(max = 100, message = "Key must be at most 100 characters")
        String key,

        @NotBlank(message = "Label is required")
        @Size(max = 100, message = "Label must be at most 100 characters")
        String label,

        @NotNull(message = "Field type is required")
        UserFieldType fieldType,

        /** When true, the field's value can be used as an alternative login
         * identifier. Values of login-enabled fields must be unique per org. */
        Boolean loginEnabled
) {
}
