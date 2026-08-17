"use client";

import { DocumentationProvider } from "@/components/docs/docs-provider";
import { useDocumentationContext } from "@/hooks/use-docs";

/**
 * Fetches the organisation's documentation context (/docs/context, public) and
 * provides it to the docs pages below. Rendered once in the docs layout so
 * every page — overview and sub-pages — gets the context on direct load and
 * on client-side navigation.
 */
export function DocsContextProvider({
  platformSlug,
  organisationId,
  children,
}: {
  platformSlug: string;
  organisationId: number;
  children: React.ReactNode;
}) {
  const { data: context, isLoading, error } = useDocumentationContext(platformSlug, organisationId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-muted-foreground">Loading documentation...</div>
      </div>
    );
  }

  if (error || !context) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-destructive">Failed to load documentation context</div>
      </div>
    );
  }

  return <DocumentationProvider context={context}>{children}</DocumentationProvider>;
}
