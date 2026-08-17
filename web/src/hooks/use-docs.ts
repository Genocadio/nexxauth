"use client";

import { useQuery } from "@tanstack/react-query";
import { docsApi } from "@/api/docs";
import { queryKeys } from "@/lib/query-keys";

export function useDocumentationContext(platformSlug: string | undefined, organisationId: number | undefined) {
  return useQuery({
    queryKey: queryKeys.docsContext(platformSlug ?? "", organisationId ?? 0),
    queryFn: () => docsApi.context(platformSlug!, organisationId!),
    enabled: !!platformSlug && !!organisationId,
  });
}
