package com.nexxserve.nauth.controller;

import com.nexxserve.nauth.dto.response.DocumentationContextResponse;
import com.nexxserve.nauth.service.DocumentationContextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint serving organisation configuration for context-aware API docs.
 * No authentication required — anyone with the URL can read docs context.
 */
@RestController
@RequestMapping("/{slug}/organisations/{organisationId}/docs")
public class DocumentationContextController {

    private final DocumentationContextService documentationContextService;

    public DocumentationContextController(DocumentationContextService documentationContextService) {
        this.documentationContextService = documentationContextService;
    }

    @GetMapping("/context")
    public DocumentationContextResponse context(@PathVariable Long organisationId) {
        return documentationContextService.context(organisationId);
    }
}
