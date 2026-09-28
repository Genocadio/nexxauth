package com.nexxserve.nexxauth.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateOrganisationTemplatesRequest(
        @Size(max = 255, message = "Subject must be at most 255 characters")
        String emailSubject,
        @Size(max = 4000, message = "Email body must be at most 4000 characters")
        String emailBody,
        @Size(max = 20000, message = "Email HTML must be at most 20000 characters")
        String emailHtml,
        @Size(max = 1600, message = "SMS body must be at most 1600 characters")
        String smsBody
) {
}
