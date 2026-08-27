import { del, get } from "@/lib/api-client";
import { endpoints } from "@/lib/endpoints";
import type { OrganisationSessionResponse, SessionTimelineEvent } from "@/types/api";

export const sessionsApi = {
  list: (platformSlug: string, organisationId: number, userId?: number, clientKey?: string) => {
    const params = new URLSearchParams();
    if (userId != null) params.set("userId", String(userId));
    if (clientKey) params.set("clientKey", clientKey);
    const qs = params.toString();
    const base = endpoints.sessions(platformSlug, organisationId).list;
    return get<OrganisationSessionResponse[]>(qs ? `${base}?${qs}` : base, "platform");
  },

  revoke: (platformSlug: string, organisationId: number, sessionId: string) =>
    del<void>(endpoints.sessions(platformSlug, organisationId).revoke(sessionId), "platform"),

  revokeAllForUser: (platformSlug: string, organisationId: number, userId: number) =>
    del<void>(endpoints.sessions(platformSlug, organisationId).revokeAllForUser(userId), "platform"),

  timeline: (platformSlug: string, organisationId: number, sessionId: string) =>
    get<SessionTimelineEvent[]>(
      `${endpoints.sessions(platformSlug, organisationId).list}/${sessionId}/timeline`,
      "platform",
    ),
};
