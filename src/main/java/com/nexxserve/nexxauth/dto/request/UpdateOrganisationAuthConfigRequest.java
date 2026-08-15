package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.AuthType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Partial update of an organisation's auth config: only provided fields are
 * applied. Cross-field rules (min &lt;= max, byte bounds) are validated in the
 * service.
 */
public record UpdateOrganisationAuthConfigRequest(

        AuthType authType,

        @Min(value = 1, message = "Password minimum length must be at least 1")
        @Max(value = 72, message = "Password minimum length must be at most 72")
        Integer passwordMinLength,

        @Min(value = 1, message = "Password maximum length must be at least 1")
        @Max(value = 72, message = "Password maximum length must be at most 72")
        Integer passwordMaxLength,

        @Min(value = 0, message = "Password expiration days cannot be negative")
        @Max(value = 3650, message = "Password expiration cannot exceed 3650 days")
        Integer passwordExpirationDays,

        @Min(value = 0, message = "Password history count cannot be negative")
        @Max(value = 50, message = "Password history count must be at most 50")
        Integer passwordHistoryCount
) {
}
