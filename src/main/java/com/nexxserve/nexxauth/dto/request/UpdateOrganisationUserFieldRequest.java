package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.UserFieldType;

/**
 * Partial update of an organisation user field. The key (attribute name) is
 * immutable; only the provided fields are applied. The type cannot be changed
 * while the field has values (they were stored under the old type), and
 * enabling login requires all existing values to be unique.
 */
public record UpdateOrganisationUserFieldRequest(

        UserFieldType fieldType,

        Boolean loginEnabled,

        /** When provided, sets whether the field is required for every user
         * (missing values surface the UPDATE_PROFILE action at login). */
        Boolean required
) {
}
