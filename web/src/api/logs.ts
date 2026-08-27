import { get } from "@/lib/api-client";
import { endpoints } from "@/lib/endpoints";
import type { LogPage } from "@/types/api";

export interface LogQueryParams {
  organisationId?: number;
  level?: string;
  category?: string;
  eventType?: string;
  clientKey?: string;
  domain?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

function buildQueryString(params: Record<string, string | number>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "");
  if (entries.length === 0) return "";
  return "?" + entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join("&");
}

export const logsApi = {
  list: (platformSlug: string, params: LogQueryParams = {}) => {
    const qs = buildQueryString({
      ...(params.organisationId != null && { organisationId: params.organisationId }),
      ...(params.level && { level: params.level }),
      ...(params.category && { category: params.category }),
      ...(params.eventType && { eventType: params.eventType }),
      ...(params.clientKey && { clientKey: params.clientKey }),
      ...(params.domain && { domain: params.domain }),
      ...(params.from && { from: params.from }),
      ...(params.to && { to: params.to }),
      ...(params.page != null && { page: params.page }),
      ...(params.size != null && { size: params.size }),
    });
    return get<LogPage>(`${endpoints.logs(platformSlug).list}${qs}`, "platform");
  },

  stream: (platformSlug: string, organisationId?: number) => {
    const qs = buildQueryString(
      organisationId != null ? { organisationId } : {}
    );
    return `${endpoints.logs(platformSlug).stream}${qs}`;
  },
};
