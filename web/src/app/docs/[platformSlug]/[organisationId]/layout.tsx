import { DocsLayout } from "@/components/docs/docs-layout";

export default function DocsPageLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: { platformSlug: string; organisationId: string };
}) {
  return (
    <DocsLayout platformSlug={params.platformSlug} organisationId={params.organisationId}>
      {children}
    </DocsLayout>
  );
}
