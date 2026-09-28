package com.nexxserve.nexxauth.dto.response;

public record OrganisationTemplatesResponse(
        String flowId,
        EmailTemplate email,
        SmsTemplate sms
) {
    public record EmailTemplate(
            boolean enabled,
            String subject,
            String body,
            String html
    ) {}

    public record SmsTemplate(
            boolean enabled,
            String body
    ) {}
}
