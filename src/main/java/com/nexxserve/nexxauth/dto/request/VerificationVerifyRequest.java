package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.VerificationPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Confirms a numeric OTP for an address verification purpose (EMAIL/PHONE): a
 * matching code marks the identifier as verified on the owning user.
 * Password-reset OTPs and OTP logins are confirmed on their own endpoints.
 * <p>
 * <b>External clients must never send {@code organisationId}.</b> The
 * organisation is resolved automatically from the {@code X-Client-Id} header.
 * The {@code organisationId} field is an internal detail used only by the
 * platform console portal flow (when no client header is present).
 */
public record VerificationVerifyRequest(

        @Size(max = 255, message = "Identifier must be at most 255 characters")
        String identifier,

        @NotNull(message = "Purpose is required")
        VerificationPurpose purpose,

        @NotBlank(message = "Code is required")
        @Size(max = 100, message = "Code must be at most 100 characters")
        String code,

        /** Organisation ID — <b>internal only</b>. Used by the platform console
         *  portal flow when no {@code X-Client-Id} header is present. External
         *  clients must never send this field; the organisation is resolved from
         *  the client header. The client header takes precedence when both are
         *  supplied. */
        Long organisationId
) {
}