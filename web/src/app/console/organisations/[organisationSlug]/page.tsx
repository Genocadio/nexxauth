"use client";

import { Suspense } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { OrgClientsTab } from "@/components/organisations/org-clients-tab";
import { OrgFieldsTab } from "@/components/organisations/org-fields-tab";
import { OrgKeysTab } from "@/components/organisations/org-keys-tab";
import { OrgOverviewTab } from "@/components/organisations/org-overview-tab";
import { OrgRolesTab } from "@/components/organisations/org-roles-tab";
import { OrgSettingsTab } from "@/components/organisations/org-settings-tab";
import { OrgUsersTab } from "@/components/organisations/org-users-tab";
import { FadeIn } from "@/components/shared/fade-in";
import { useOrganisations, usePlatformSlug } from "@/hooks/queries";

type Tab = "overview" | "users" | "roles" | "fields" | "keys" | "clients" | "settings";

export default function OrganisationDetailPage() {
  return (
    <Suspense fallback={null}>
      <OrganisationDetail />
    </Suspense>
  );
}

function OrganisationDetail() {
  const params = useParams<{ organisationSlug: string }>();
  const organisationSlug = params.organisationSlug;
  const platformSlug = usePlatformSlug() ?? "";
  const organisations = useOrganisations();
  const searchParams = useSearchParams();

  const org = organisations.data?.find((o) => o.slug === organisationSlug);
  const organisationId = org?.id;

  const tab = (searchParams.get("tab") as Tab) ?? "overview";

  if (!organisationId) {
    return (
      <div className="space-y-6">
        <FadeIn>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{organisationSlug}</h1>
            <p className="font-mono text-sm text-muted-foreground">Loading...</p>
          </div>
        </FadeIn>
      </div>
    );
  }

  const content: Record<Tab, React.ReactNode> = {
    overview: <OrgOverviewTab platformSlug={platformSlug} organisationId={organisationId} />,
    users: (
      <OrgUsersTab
        platformSlug={platformSlug}
        organisationId={organisationId}
        useEmailAsUsername={org?.useEmailAsUsername ?? false}
      />
    ),
    roles: <OrgRolesTab platformSlug={platformSlug} organisationId={organisationId} />,
    fields: <OrgFieldsTab platformSlug={platformSlug} organisationId={organisationId} />,
    keys: <OrgKeysTab platformSlug={platformSlug} organisationId={organisationId} />,
    clients: <OrgClientsTab platformSlug={platformSlug} organisationId={organisationId} />,
    settings: <OrgSettingsTab platformSlug={platformSlug} organisationId={organisationId} />,
  };

  return (
    <div className="space-y-6">
      <FadeIn>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{org?.name ?? organisationSlug}</h1>
          <p className="font-mono text-sm text-muted-foreground">{org?.slug}</p>
        </div>
      </FadeIn>
      {content[tab] ?? content.overview}
    </div>
  );
}
