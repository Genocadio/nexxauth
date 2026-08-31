"use client";

import { useState } from "react";
import { AppWindow, Link, Pencil, Plus, RefreshCw, Settings, Trash2 } from "lucide-react";
import { ClientTokenDialog } from "@/components/organisations/client-token-dialog";
import { OrgClientDialog } from "@/components/organisations/org-client-dialog";
import { OrgClientLinksDialog } from "@/components/organisations/org-client-links-dialog";
import { OrgClientSettingsDialog } from "@/components/organisations/org-client-settings-dialog";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { CopyButton } from "@/components/shared/copy-button";
import { EmptyState } from "@/components/shared/empty-state";
import { ErrorState } from "@/components/shared/error-state";
import { TableSkeleton } from "@/components/shared/loading";
import { EnabledBadge, ToneBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useDeleteOrgClient, useRotateOrgClientToken } from "@/hooks/mutations";
import { useOrgClients } from "@/hooks/queries";
import { formatDate } from "@/lib/constants";
import type { OrganisationClientResponse } from "@/types/api";
import { CLIENT_TYPE_META, CLIENT_TYPE_TONE, type BadgeTone } from "@/types/enums";

interface OrgClientsTabProps {
  platformSlug: string;
  organisationId: number;
}

function authLabel(client: OrganisationClientResponse): { label: string; tone: BadgeTone } {
  return client.requireAuthentication
    ? { label: "Token auth", tone: "success" }
    : { label: "Sign-in only", tone: "secondary" };
}

function restrictionBadges(client: OrganisationClientResponse): string[] {
  const badges: string[] = [];
  if (!client.allowLogin) badges.push("No login");
  if (!client.allowRegister) badges.push("No register");
  if (client.allowedRoles.length > 0) badges.push(`${client.allowedRoles.length} role${client.allowedRoles.length === 1 ? "" : "s"}`);
  const restrictedLinks = (client.links ?? []).filter((l) => l.limitSource).length;
  if (restrictedLinks > 0) badges.push(`${restrictedLinks} restricted`);
  return badges;
}

export function OrgClientsTab({ platformSlug, organisationId }: OrgClientsTabProps) {
  const clients = useOrgClients(organisationId);
  const deleteMutation = useDeleteOrgClient(platformSlug, organisationId);
  const rotateMutation = useRotateOrgClientToken(platformSlug, organisationId);

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<OrganisationClientResponse | null>(null);
  const [settingsClient, setSettingsClient] = useState<OrganisationClientResponse | null>(null);
  const [linksClient, setLinksClient] = useState<OrganisationClientResponse | null>(null);
  const [deleting, setDeleting] = useState<OrganisationClientResponse | null>(null);
  const [rotating, setRotating] = useState<OrganisationClientResponse | null>(null);
  const [token, setToken] = useState<string | null>(null);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {clients.data ? `${clients.data.length} client${clients.data.length === 1 ? "" : "s"}` : "Clients"} of this organisation
        </p>
        <Button size="sm" onClick={() => setCreateOpen(true)}>
          <Plus /> New client
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {clients.isLoading ? (
            <div className="p-4">
              <TableSkeleton rows={4} columns={5} />
            </div>
          ) : clients.isError ? (
            <div className="p-4">
              <ErrorState error={clients.error} onRetry={() => clients.refetch()} />
            </div>
          ) : clients.data && clients.data.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Client</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Auth</TableHead>
                  <TableHead className="hidden lg:table-cell">Origins</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="hidden md:table-cell">Created</TableHead>
                  <TableHead className="w-32" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {clients.data.map((client) => {
                  const auth = authLabel(client);
                  return (
                    <TableRow key={client.clientKey}>
                      <TableCell>
                        <p className="truncate text-sm font-medium">{client.name}</p>
                        <div className="flex items-center gap-1">
                          <span className="truncate font-mono text-xs text-muted-foreground">
                            {client.clientKey}
                          </span>
                          <CopyButton
                            value={client.clientKey}
                            label={`Copy client key for ${client.name}`}
                            className="size-6"
                          />
                        </div>
                      </TableCell>
                      <TableCell>
                        <ToneBadge tone={CLIENT_TYPE_TONE[client.type]}>
                          {CLIENT_TYPE_META[client.type].label}
                        </ToneBadge>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          <ToneBadge tone={auth.tone}>{auth.label}</ToneBadge>
                          {restrictionBadges(client).map((badge) => (
                            <ToneBadge key={badge} tone="warning">{badge}</ToneBadge>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell className="hidden max-w-[200px] text-sm text-muted-foreground lg:table-cell">
                        {(client.links ?? []).length > 0 ? (
                          <div className="space-y-0.5">
                            <span className="flex items-center gap-1">
                              <Link className="h-3 w-3" />
                              {(client.links ?? []).length} link{(client.links ?? []).length === 1 ? "" : "s"}
                            </span>
                            {(client.links ?? []).some((l) => l.limitSource) && (
                              <span className="text-[11px] text-amber-600 dark:text-amber-400">
                                {(client.links ?? []).filter((l) => l.limitSource).length} source-restricted
                              </span>
                            )}
                          </div>
                        ) : client.allowedOrigins.length > 0 ? (
                          <span className="truncate block">{client.allowedOrigins.join(", ")}</span>
                        ) : (
                          "None"
                        )}
                      </TableCell>
                      <TableCell>
                        <EnabledBadge enabled={client.enabled} />
                      </TableCell>
                      <TableCell className="hidden text-sm text-muted-foreground md:table-cell">
                        {formatDate(client.createdAt)}
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Edit ${client.name}`}
                            onClick={() => setEditing(client)}
                          >
                            <Pencil />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Links for ${client.name}`}
                            onClick={() => setLinksClient(client)}
                          >
                            <Link />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Settings for ${client.name}`}
                            onClick={() => setSettingsClient(client)}
                          >
                            <Settings />
                          </Button>
                          {client.requireAuthentication ? (
                            <Button
                              variant="ghost"
                              size="icon"
                              aria-label={`Rotate token for ${client.name}`}
                              onClick={() => setRotating(client)}
                            >
                              <RefreshCw />
                            </Button>
                          ) : null}
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Delete ${client.name}`}
                            onClick={() => setDeleting(client)}
                          >
                            <Trash2 />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          ) : (
            <div className="p-4">
              <EmptyState
                icon={AppWindow}
                title="No clients yet"
                description="Clients are the external apps that talk to this organisation's API. Create one to mint a static token and a browser allow-list."
                action={
                  <Button size="sm" onClick={() => setCreateOpen(true)}>
                    <Plus /> New client
                  </Button>
                }
              />
            </div>
          )}
        </CardContent>
      </Card>

      {/* Create dialog */}
      <OrgClientDialog
        key="create"
        platformSlug={platformSlug}
        organisationId={organisationId}
        open={createOpen}
        onOpenChange={setCreateOpen}
        onCreated={(created) => {
          if (created.token) setToken(created.token);
        }}
      />

      {/* Edit dialog */}
      <OrgClientDialog
        key={editing?.clientKey ?? "edit"}
        platformSlug={platformSlug}
        organisationId={organisationId}
        open={!!editing}
        onOpenChange={(open) => !open && setEditing(null)}
        client={editing ?? undefined}
      />

      {/* Links dialog */}
      {linksClient ? (
        <OrgClientLinksDialog
          key={linksClient.clientKey}
          platformSlug={platformSlug}
          organisationId={organisationId}
          clientKey={linksClient.clientKey}
          clientName={linksClient.name}
          open={!!linksClient}
          onOpenChange={(open) => !open && setLinksClient(null)}
        />
      ) : null}

      {/* Settings dialog */}
      {settingsClient ? (
        <OrgClientSettingsDialog
          key={settingsClient.clientKey}
          platformSlug={platformSlug}
          organisationId={organisationId}
          open={!!settingsClient}
          onOpenChange={(open) => !open && setSettingsClient(null)}
          client={settingsClient}
        />
      ) : null}

      {/* Delete confirm */}
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete client?"
        description={`"${deleting?.name}" will stop being recognised immediately. Its token — if any — is revoked.`}
        confirmLabel="Delete client"
        pending={deleteMutation.isPending}
        onConfirm={async () => {
          if (deleting) await deleteMutation.mutateAsync(deleting.clientKey);
          setDeleting(null);
        }}
      />

      {/* Rotate token confirm */}
      <ConfirmDialog
        open={!!rotating}
        onOpenChange={(open) => !open && setRotating(null)}
        title="Rotate token?"
        description={`A new token replaces "${rotating?.name}"'s current one. The old token stops working immediately, and the new one is shown once.`}
        confirmLabel="Rotate token"
        pending={rotateMutation.isPending}
        onConfirm={async () => {
          if (rotating) {
            const result = await rotateMutation.mutateAsync(rotating.clientKey);
            if (result.token) setToken(result.token);
          }
          setRotating(null);
        }}
      />

      {/* Token display */}
      <ClientTokenDialog
        open={!!token}
        onOpenChange={(open) => !open && setToken(null)}
        token={token ?? ""}
        title="Client token"
      />
    </div>
  );
}
