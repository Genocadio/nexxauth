import { DocsLayout } from "@/components/docs/docs-layout";

export default async function DocsPageLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ platformSlug: string; organisationId: string }>;
}) {
  const { platformSlug, organisationId } = await params;
  return (
    <DocsLayout platformSlug={platformSlug} organisationId={organisationId}>
      {children}
    </DocsLayout>
  );
}
