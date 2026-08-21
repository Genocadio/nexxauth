package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.UserFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Define an organisation user field. The key is the attribute name used in
 * user {@code metadata} (lowercase letters, digits and hyphens) and must be
 * unique per organisation.
 */
public record CreateOrganisationUserFieldRequest(

        @NotBlank(message = "Attribute name is required")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Attribute name must be lowercase letters, digits and hyphens (e.g. employee-id)")
        @Size(max = 100, message = "Attribute name must be at most 100 characters")
        String key,

        @NotNull(message = "Field type is required")
        UserFieldType fieldType,

        /** When true, the field's value can be used as an alternative login
         * identifier. Values of login-enabled fields must be unique per org. */
        Boolean loginEnabled,

        /** When true, every user of the organisation must have a value for this
         * field; users missing it get the UPDATE_PROFILE action at login. */
        Boolean required
) {
}
