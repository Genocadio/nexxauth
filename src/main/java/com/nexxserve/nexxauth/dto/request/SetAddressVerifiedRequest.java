package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Admin override of an address's verified state. Setting {@code verified} to
 * false is allowed so a support agent can un-vouch an address they previously
 * approved by mistake.
 */
public record SetAddressVerifiedRequest(
        @NotNull(message = "Verified is required")
        Boolean verified
) {
}
