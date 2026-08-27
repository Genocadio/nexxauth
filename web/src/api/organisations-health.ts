import { get } from "@/lib/api-client";
import { API_BASE_URL } from "@/lib/constants";

export interface OrganisationHealth {
  organisationId: number;
  userCount: number;
  activeSessions: number;
  signingKeys: number;
  apiClients: number;
  score: number;
  previousScore: number | null;
  trend: "UP" | "DOWN" | "SAME" | null;
}

export const organisationsHealthApi = {
  list: (platformSlug: string) =>
    get<OrganisationHealth[]>(`${API_BASE_URL}/${platformSlug}/organisations/health`, "platform"),
};
