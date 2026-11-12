package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.OrgIdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrgDeleteAccountRequest(
        @NotBlank(message = "Identifier is required")
        @Size(max = 255, message = "Identifier must be at most 255 characters")
        String identifier,

        OrgIdentifierType identifierType,

        @Size(max = 72, message = "Password must be at most 72 characters")
        String password,

        @Size(max = 100, message = "Verification code must be at most 100 characters")
        String verificationCode,

        Long organisationId
) {
    public String resolvedVerificationCode() {
        return verificationCode != null && !verificationCode.isBlank() ? verificationCode.trim() : null;
    }
}
