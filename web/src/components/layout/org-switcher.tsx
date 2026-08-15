"use client";

import { useState } from "react";
import { Building2, Check, ChevronsUpDown, Plus } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { OrganisationDialog } from "@/components/organisations/organisation-dialog";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useOrganisations, usePlatformSlug } from "@/hooks/queries";

/** Header switcher: current organisation, quick switch list, and create. */
export function OrgSwitcher() {
  const organisations = useOrganisations();
  const platformSlug = usePlatformSlug() ?? "";
  const pathname = usePathname();
  const [dialogOpen, setDialogOpen] = useState(false);

  const segments = pathname.split("/");
  const currentSlug = segments[2] === "organisations" ? segments[3] : undefined;
  const current = organisations.data?.find((org) => org.slug === currentSlug);

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" className="h-9 gap-2 px-3">
            <Building2 className="h-4 w-4 shrink-0 text-muted-foreground" />
            <span className="max-w-40 truncate font-medium">
              {current?.name ?? (currentSlug ? currentSlug : "Select an organisation")}
            </span>
            <ChevronsUpDown className="h-4 w-4 shrink-0 text-muted-foreground" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-64">
          <DropdownMenuLabel className="flex items-center justify-between">
            <span>Organisations</span>
            <span className="text-xs font-normal text-muted-foreground">
              {organisations.data?.length ?? 0}
            </span>
          </DropdownMenuLabel>

          {organisations.isLoading ? (
            <DropdownMenuItem disabled>Loading…</DropdownMenuItem>
          ) : organisations.data && organisations.data.length > 0 ? (
            organisations.data.map((org) => (
              <DropdownMenuItem key={org.id} asChild>
                <Link href={`/console/organisations/${org.slug}`}>
                  <span className="min-w-0 flex-1 truncate">{org.name}</span>
                  {org.slug === currentSlug ? <Check className="size-4 shrink-0 text-primary" /> : null}
                </Link>
              </DropdownMenuItem>
            ))
          ) : (
            <DropdownMenuItem disabled>No organisations yet</DropdownMenuItem>
          )}

          <DropdownMenuSeparator />
          <DropdownMenuItem asChild>
            <Link href="/console/organisations">
              <Building2 className="size-4" /> Platform management
            </Link>
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => setDialogOpen(true)}>
            <Plus className="size-4" /> Create organisation
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <OrganisationDialog platformSlug={platformSlug} open={dialogOpen} onOpenChange={setDialogOpen} />
    </>
  );
}
