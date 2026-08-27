package com.nexxserve.nexxauth.dto.response;

/**
 * Lightweight health snapshot for an organisation: counts that let the UI
 * render a health score bar without loading the full org detail.
 * Includes trend data comparing today's score to ~7 days ago.
 */
public record OrganisationHealthResponse(
        Long organisationId,
        int userCount,
        int activeSessions,
        int signingKeys,
        int apiClients,
        int score,
        /** Previous score from ~7 days ago, or null if no history exists. */
        Integer previousScore,
        /** Trend direction: UP, DOWN, SAME, or null if no comparison possible. */
        String trend
) {
}
