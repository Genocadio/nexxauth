import { get } from "@/lib/api-client";
import { endpoints } from "@/lib/endpoints";
import type { DocumentationContext } from "@/types/docs";

export const docsApi = {
  context: (platformSlug: string, organisationId: number) =>
    get<DocumentationContext>(endpoints.docs(platformSlug).context(organisationId)),
};
