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
import { useOrganisation, usePlatformSlug } from "@/hooks/queries";

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
  const org = useOrganisation(organisationSlug);
  const searchParams = useSearchParams();

  const tab = (searchParams.get("tab") as Tab) ?? "overview";

  const content: Record<Tab, React.ReactNode> = {
    overview: <OrgOverviewTab platformSlug={platformSlug} organisationSlug={organisationSlug} />,
    users: (
      <OrgUsersTab
        platformSlug={platformSlug}
        organisationSlug={organisationSlug}
        useEmailAsUsername={org.data?.useEmailAsUsername ?? false}
      />
    ),
    roles: <OrgRolesTab platformSlug={platformSlug} organisationSlug={organisationSlug} />,
    fields: <OrgFieldsTab platformSlug={platformSlug} organisationSlug={organisationSlug} />,
    keys: <OrgKeysTab platformSlug={platformSlug} organisationSlug={organisationSlug} />,
    clients: <OrgClientsTab platformSlug={platformSlug} organisationSlug={organisationSlug} />,
    settings: <OrgSettingsTab platformSlug={platformSlug} organisationSlug={organisationSlug} />,
  };

  return (
    <div className="space-y-6">
      <FadeIn>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{org.data?.name ?? organisationSlug}</h1>
          <p className="font-mono text-sm text-muted-foreground">{org.data?.slug}</p>
        </div>
      </FadeIn>
      {content[tab] ?? content.overview}
    </div>
  );
}
