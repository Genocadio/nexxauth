package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.OrgIdentifierType;
import com.nexxserve.nexxauth.entity.VerificationChannel;
import com.nexxserve.nexxauth.entity.VerificationDelivery;
import com.nexxserve.nexxauth.entity.VerificationPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Requests a single-use verification value (numeric OTP or magic link) to be
 * sent to the given email address or phone number through nexxbotify. The
 * organisation must have the feature for {@code purpose} enabled. The delivery
 * defaults to {@link VerificationDelivery#OTP} when omitted.
 * <p>
 * <b>External clients must never send {@code organisationId}.</b> The
 * organisation is resolved automatically from the {@code X-Client-Id} header.
 * The {@code organisationId} field is an internal detail used only by the
 * platform console portal flow (when no client header is present).
 */
public record VerificationRequest(

        @NotBlank(message = "Identifier is required")
        @Size(max = 255, message = "Identifier must be at most 255 characters")
        String identifier,

        /** Explicit identifier type (EMAIL, PHONE, USERNAME); optional. */
        OrgIdentifierType identifierType,

        /** Channel (EMAIL or SMS); auto-detected from identifier or identifierType when omitted. */
        VerificationChannel channel,

        /** Purpose of the verification; defaults to LOGIN_OTP when omitted. */
        VerificationPurpose purpose,

        /** OTP (a code typed back in) or LINK (a magic link). Defaults to the
         * organisation's verification mode when omitted. */
        VerificationDelivery delivery,

        /** Organisation ID — <b>internal only</b>. Used by the platform console
         *  portal flow when no {@code X-Client-Id} header is present. External
         *  clients must never send this field; the organisation is resolved from
         *  the client header. The client header takes precedence when both are
         *  supplied. */
        Long organisationId
) {
    public VerificationRequest(String identifier, VerificationChannel channel,
                               VerificationPurpose purpose, VerificationDelivery delivery,
                               Long organisationId) {
        this(identifier, null, channel, purpose, delivery, organisationId);
    }
}