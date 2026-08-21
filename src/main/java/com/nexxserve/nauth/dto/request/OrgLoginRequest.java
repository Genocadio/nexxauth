package com.nexxserve.nauth.dto.request;

import com.nexxserve.nauth.entity.AuthType;
import com.nexxserve.nauth.entity.OrgIdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Org-level login. The organisation is identified by the {@code X-Client-Id}
 * header when the request comes from a registered client (the client's
 * organisation is authoritative); otherwise {@code organisationId} is required
 * (server-side/platform-user flows).
 * <p>
 * {@code identifierType} says what kind of identifier is being sent — email,
 * username or phone. When omitted the backend falls back to trying each
 * enabled identifier in order. {@code authType} selects the authentication
 * method — today only {@code PASSWORD}, which is also the default; future
 * methods (passkey, OTP, ...) extend {@link AuthType}.
 */
public record OrgLoginRequest(

        /** Required only when no {@code X-Client-Id} header is present. */
        Long organisationId,

        @NotBlank(message = "Identifier is required")
        @Size(max = 255, message = "Identifier must be at most 255 characters")
        String identifier,

        /** Type of the identifier; defaults to auto-detection when omitted. */
        OrgIdentifierType identifierType,

        /** Authentication method; defaults to {@code PASSWORD} when omitted. */
        AuthType authType,

        /** Credential for the {@code PASSWORD} method; required for it. */
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password
) {
}
