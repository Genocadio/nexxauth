package com.nexxserve.nexxauth.dto.request;

import com.nexxserve.nexxauth.entity.AuthType;
import com.nexxserve.nexxauth.entity.OrgIdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Org-level login. The organisation is identified by the {@code X-Client-Id}
 * header — the client's organisation is authoritative.
 * <p>
 * {@code identifierType} says what kind of identifier is being sent — email,
 * username or phone. When omitted the backend falls back to trying each
 * enabled identifier in order. {@code authType} selects the authentication
 * method — today only {@code PASSWORD}, which is also the default; future
 * methods (passkey, OTP, ...) extend {@link AuthType}.
 * <p>
 * <b>External clients must never send {@code organisationId}.</b> The
 * organisation is resolved automatically from the {@code X-Client-Id} header.
 * The {@code organisationId} field is an internal detail used only by the
 * platform console portal flow (when no client header is present).
 */
public record OrgLoginRequest(

        @Size(max = 255, message = "Identifier must be at most 255 characters")
        String identifier,

        /** Type of the identifier (EMAIL, PHONE, USERNAME); required for PASSWORD and PASSWORDLESS modes. */
        OrgIdentifierType identifierType,

        /** Authentication method; defaults to PASSWORD when omitted. */
        AuthType authType,

        /** Mode alias: PASSWORD, PASSWORDLESS, OTP, or VERIFY. */
        AuthType mode,

        /** Credential for the PASSWORD method; required for it. */
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password,

        /** Verification code for VERIFY or OTP / PASSWORDLESS method. */
        @Size(max = 100, message = "Code must be at most 100 characters")
        String verificationCode,

        /** Legacy alias for verificationCode. */
        @Size(max = 100, message = "Code must be at most 100 characters")
        String otpCode,

        /** Organisation ID — <b>internal only</b>. Used by the platform console
         *  portal flow when no {@code X-Client-Id} header is present. External
         *  clients must never send this field; the organisation is resolved from
         *  the client header. The client header takes precedence when both are
         *  supplied. */
        Long organisationId
) {
    public AuthType resolvedMode() {
        if (mode != null) return mode;
        if (authType != null) return authType;
        return null;
    }

    public String resolvedVerificationCode() {
        if (verificationCode != null && !verificationCode.isBlank()) {
            return verificationCode.trim();
        }
        if (otpCode != null && !otpCode.isBlank()) {
            return otpCode.trim();
        }
        return null;
    }
}
