import { DocsContextProvider } from "@/components/docs/docs-context-provider";
import { DocsLayout } from "@/components/docs/docs-layout";
import { BackendOriginProvider } from "@/lib/backend-url";

// Read BACKEND_PUBLIC_URL at runtime so the docs can show the real project
// base URL (same as the dashboards) to logged-in viewers.
export const dynamic = "force-dynamic";

export default async function DocsPageLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ platformSlug: string; organisationId: string }>;
}) {
  const { platformSlug, organisationId } = await params;
  return (
    <BackendOriginProvider value={process.env.BACKEND_PUBLIC_URL ?? ""}>
      <DocsLayout platformSlug={platformSlug} organisationId={organisationId}>
        <DocsContextProvider platformSlug={platformSlug} organisationId={Number(organisationId)}>
          {children}
        </DocsContextProvider>
      </DocsLayout>
    </BackendOriginProvider>
  );
}
