"use client";

import { useState } from "react";
import { Fingerprint, Pencil, Plus, ShieldCheck, Sparkles, Trash2 } from "lucide-react";
import { OrgFieldDialog } from "@/components/organisations/org-field-dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { ErrorState } from "@/components/shared/error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { TableSkeleton } from "@/components/shared/loading";
import { ToneBadge, UserFieldTypeBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useDeleteUserField, useUpdateOrganisation, useUpdateUserField } from "@/hooks/mutations";
import { useOrganisation, useOrgUserFields } from "@/hooks/queries";
import type { OrganisationUserFieldResponse } from "@/types/api";
import { toast } from "sonner";

interface OrgFieldsTabProps {
  platformSlug: string;
  organisationId: number;
}

export function OrgFieldsTab({ platformSlug, organisationId }: OrgFieldsTabProps) {
  const org = useOrganisation(organisationId);
  const fields = useOrgUserFields(organisationId);
  const updateOrg = useUpdateOrganisation(platformSlug, organisationId);
  const updateField = useUpdateUserField(platformSlug, organisationId);
  const deleteMutation = useDeleteUserField(platformSlug, organisationId);

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<OrganisationUserFieldResponse | null>(null);
  const [deleting, setDeleting] = useState<OrganisationUserFieldResponse | null>(null);

  const orgData = org.data;

  const setIdentifierFlag = (
    key: "emailRequired" | "usernameRequired" | "phoneRequired" | "emailCanLogin" | "usernameCanLogin" | "phoneCanLogin",
    value: boolean
  ) => {
    if (!orgData) return;

    const nextEmailCanLogin = key === "emailCanLogin" ? value : orgData.emailCanLogin;
    const nextUsernameCanLogin = key === "usernameCanLogin" ? value : orgData.usernameCanLogin;
    const nextPhoneCanLogin = key === "phoneCanLogin" ? value : orgData.phoneCanLogin;

    if (!nextEmailCanLogin && !nextUsernameCanLogin && !nextPhoneCanLogin) {
      toast.error("At least one identifier must remain enabled for login.");
      return;
    }

    updateOrg
      .mutateAsync({
        emailRequired: key === "emailRequired" ? value : orgData.emailRequired,
        usernameRequired: key === "usernameRequired" ? value : orgData.usernameRequired,
        phoneRequired: key === "phoneRequired" ? value : orgData.phoneRequired,
        emailCanLogin: nextEmailCanLogin,
        usernameCanLogin: nextUsernameCanLogin,
        phoneCanLogin: nextPhoneCanLogin,
      })
      .catch(() => undefined);
  };

  const toggleCustomField = (
    field: OrganisationUserFieldResponse,
    prop: "loginEnabled" | "required",
    value: boolean
  ) => {
    updateField
      .mutateAsync({
        fieldId: field.id,
        body: {
          fieldType: field.fieldType,
          loginEnabled: prop === "loginEnabled" ? value : field.loginEnabled,
          required: prop === "required" ? value : field.required,
        },
      })
      .catch(() => undefined);
  };

  return (
    <div className="space-y-8">
      {/* 1. Built-in User Attributes */}
      <div className="space-y-3">
        <div>
          <h3 className="text-base font-semibold tracking-tight">Built-in User Attributes</h3>
          <p className="text-sm text-muted-foreground">
            Standard core identity fields on every user. Configure which identifiers are required on register and usable for sign-in.
          </p>
        </div>

        <Card>
          <CardContent className="p-0">
            {org.isLoading ? (
              <div className="p-4">
                <TableSkeleton rows={5} columns={4} />
              </div>
            ) : org.isError || !orgData ? (
              <div className="p-4">
                <ErrorState error={org.error ?? new Error("Organisation not found")} onRetry={() => org.refetch()} />
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[200px]">Attribute</TableHead>
                    <TableHead className="w-[120px]">Type</TableHead>
                    <TableHead className="w-[110px]">Scope</TableHead>
                    <TableHead className="w-[220px]">Login Identifier</TableHead>
                    <TableHead className="w-[180px]">Requirement</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {/* First Name */}
                  <TableRow>
                    <TableCell>
                      <div className="min-w-0">
                        <p className="text-sm font-medium">First name</p>
                        <code className="text-xs text-muted-foreground">firstName</code>
                      </div>
                    </TableCell>
                    <TableCell>
                      <ToneBadge tone="secondary">Text</ToneBadge>
                    </TableCell>
                    <TableCell>
                      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <ShieldCheck className="h-3.5 w-3.5 text-primary" /> Built-in
                      </span>
                    </TableCell>
                    <TableCell>
                      <span className="text-xs text-muted-foreground">-</span>
                    </TableCell>
                    <TableCell>
                      <ToneBadge tone="secondary" className="font-medium">
                        System Required
                      </ToneBadge>
                    </TableCell>
                  </TableRow>

                  {/* Last Name */}
                  <TableRow>
                    <TableCell>
                      <div className="min-w-0">
                        <p className="text-sm font-medium">Last name</p>
                        <code className="text-xs text-muted-foreground">lastName</code>
                      </div>
                    </TableCell>
                    <TableCell>
                      <ToneBadge tone="secondary">Text</ToneBadge>
                    </TableCell>
                    <TableCell>
                      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <ShieldCheck className="h-3.5 w-3.5 text-primary" /> Built-in
                      </span>
                    </TableCell>
                    <TableCell>
                      <span className="text-xs text-muted-foreground">-</span>
                    </TableCell>
                    <TableCell>
                      <span className="text-xs text-muted-foreground">Optional</span>
                    </TableCell>
                  </TableRow>

                  {/* Email */}
                  <TableRow>
                    <TableCell>
                      <div className="min-w-0">
                        <p className="text-sm font-medium">Email address</p>
                        <code className="text-xs text-muted-foreground">email</code>
                      </div>
                    </TableCell>
                    <TableCell>
                      <ToneBadge tone="secondary">Email</ToneBadge>
                    </TableCell>
                    <TableCell>
                      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <ShieldCheck className="h-3.5 w-3.5 text-primary" /> Built-in
                      </span>
                    </TableCell>
                    <TableCell>
                      <label className="inline-flex items-center gap-2 cursor-pointer text-xs">
                        <Switch
                          checked={orgData.emailCanLogin}
                          disabled={updateOrg.isPending}
                          onCheckedChange={(checked) => setIdentifierFlag("emailCanLogin", checked)}
                        />
                        <span className={orgData.emailCanLogin ? "font-medium text-foreground" : "text-muted-foreground"}>
                          {orgData.emailCanLogin ? "Can log in" : "Disabled for login"}
                        </span>
                      </label>
                    </TableCell>
                    <TableCell>
                      <label className="inline-flex items-center gap-2 cursor-pointer text-xs">
                        <Switch
                          checked={orgData.emailRequired}
                          disabled={updateOrg.isPending}
                          onCheckedChange={(checked) => setIdentifierFlag("emailRequired", checked)}
                        />
                        <span className={orgData.emailRequired ? "font-medium text-foreground" : "text-muted-foreground"}>
                          {orgData.emailRequired ? "Required" : "Optional"}
                        </span>
                      </label>
                    </TableCell>
                  </TableRow>

                  {/* Username */}
                  <TableRow>
                    <TableCell>
                      <div className="min-w-0">
                        <p className="text-sm font-medium">Username</p>
                        <code className="text-xs text-muted-foreground">username</code>
                      </div>
                    </TableCell>
                    <TableCell>
                      <ToneBadge tone="secondary">Text</ToneBadge>
                    </TableCell>
                    <TableCell>
                      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <ShieldCheck className="h-3.5 w-3.5 text-primary" /> Built-in
                      </span>
                    </TableCell>
                    <TableCell>
                      <label className="inline-flex items-center gap-2 cursor-pointer text-xs">
                        <Switch
                          checked={orgData.usernameCanLogin}
                          disabled={updateOrg.isPending}
                          onCheckedChange={(checked) => setIdentifierFlag("usernameCanLogin", checked)}
                        />
                        <span className={orgData.usernameCanLogin ? "font-medium text-foreground" : "text-muted-foreground"}>
                          {orgData.usernameCanLogin ? "Can log in" : "Disabled for login"}
                        </span>
                      </label>
                    </TableCell>
                    <TableCell>
                      <label className="inline-flex items-center gap-2 cursor-pointer text-xs">
                        <Switch
                          checked={orgData.usernameRequired}
                          disabled={updateOrg.isPending}
                          onCheckedChange={(checked) => setIdentifierFlag("usernameRequired", checked)}
                        />
                        <span className={orgData.usernameRequired ? "font-medium text-foreground" : "text-muted-foreground"}>
                          {orgData.usernameRequired ? "Required" : "Optional"}
                        </span>
                      </label>
                    </TableCell>
                  </TableRow>

                  {/* Phone */}
                  <TableRow>
                    <TableCell>
                      <div className="min-w-0">
                        <p className="text-sm font-medium">Phone number</p>
                        <code className="text-xs text-muted-foreground">phone</code>
                      </div>
                    </TableCell>
                    <TableCell>
                      <ToneBadge tone="secondary">Phone</ToneBadge>
                    </TableCell>
                    <TableCell>
                      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <ShieldCheck className="h-3.5 w-3.5 text-primary" /> Built-in
                      </span>
                    </TableCell>
                    <TableCell>
                      <label className="inline-flex items-center gap-2 cursor-pointer text-xs">
                        <Switch
                          checked={orgData.phoneCanLogin}
                          disabled={updateOrg.isPending}
                          onCheckedChange={(checked) => setIdentifierFlag("phoneCanLogin", checked)}
                        />
                        <span className={orgData.phoneCanLogin ? "font-medium text-foreground" : "text-muted-foreground"}>
                          {orgData.phoneCanLogin ? "Can log in" : "Disabled for login"}
                        </span>
                      </label>
                    </TableCell>
                    <TableCell>
                      <label className="inline-flex items-center gap-2 cursor-pointer text-xs">
                        <Switch
                          checked={orgData.phoneRequired}
                          disabled={updateOrg.isPending}
                          onCheckedChange={(checked) => setIdentifierFlag("phoneRequired", checked)}
                        />
                        <span className={orgData.phoneRequired ? "font-medium text-foreground" : "text-muted-foreground"}>
                          {orgData.phoneRequired ? "Required" : "Optional"}
                        </span>
                      </label>
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 2. Custom Organisation Fields (Metadata) */}
      <div className="space-y-3">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-base font-semibold tracking-tight">Custom Organisation Fields</h3>
            <p className="text-sm text-muted-foreground">
              Domain-specific custom attributes stored per user, returned in <code className="font-mono">metadata</code>.
            </p>
          </div>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="self-start sm:self-auto">
            <Plus /> New field
          </Button>
        </div>

        <Card>
          <CardContent className="p-0">
            {fields.isLoading ? (
              <div className="p-4">
                <TableSkeleton rows={3} columns={5} />
              </div>
            ) : fields.isError ? (
              <div className="p-4">
                <ErrorState error={fields.error} onRetry={() => fields.refetch()} />
              </div>
            ) : fields.data && fields.data.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[200px]">Field Key</TableHead>
                    <TableHead className="w-[120px]">Type</TableHead>
                    <TableHead className="w-[220px]">Login Identifier</TableHead>
                    <TableHead className="w-[180px]">Requirement</TableHead>
                    <TableHead className="w-20 text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {fields.data.map((field) => (
                    <TableRow key={field.id}>
                      <TableCell>
                        <p className="text-sm font-medium font-mono">{field.key}</p>
                      </TableCell>
                      <TableCell>
                        <UserFieldTypeBadge fieldType={field.fieldType} />
                      </TableCell>
                      <TableCell>
                        <label className="inline-flex items-center gap-2 cursor-pointer text-xs">
                          <Switch
                            checked={field.loginEnabled}
                            disabled={updateField.isPending}
                            onCheckedChange={(checked) => toggleCustomField(field, "loginEnabled", checked)}
                          />
                          <span className={field.loginEnabled ? "font-medium text-foreground" : "text-muted-foreground"}>
                            {field.loginEnabled ? "Can log in" : "No"}
                          </span>
                        </label>
                      </TableCell>
                      <TableCell>
                        <label className="inline-flex items-center gap-2 cursor-pointer text-xs">
                          <Switch
                            checked={field.required}
                            disabled={updateField.isPending}
                            onCheckedChange={(checked) => toggleCustomField(field, "required", checked)}
                          />
                          <span className={field.required ? "font-medium text-foreground" : "text-muted-foreground"}>
                            {field.required ? "Required" : "Optional"}
                          </span>
                        </label>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Edit ${field.key}`}
                            onClick={() => setEditing(field)}
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Delete ${field.key}`}
                            className="text-destructive hover:text-destructive hover:bg-destructive/10"
                            onClick={() => setDeleting(field)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <div className="p-6">
                <EmptyState
                  icon={Sparkles}
                  title="No custom fields defined"
                  description="Add custom attributes (e.g. employee_id, department, national_id) that attach to every user's profile metadata."
                  action={
                    <Button size="sm" onClick={() => setCreateOpen(true)}>
                      <Plus /> New field
                    </Button>
                  }
                />
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <OrgFieldDialog
        platformSlug={platformSlug}
        organisationId={organisationId}
        open={createOpen}
        onOpenChange={setCreateOpen}
      />
      <OrgFieldDialog
        platformSlug={platformSlug}
        organisationId={organisationId}
        open={!!editing}
        onOpenChange={(open) => !open && setEditing(null)}
        field={editing ?? undefined}
      />

      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete user field"
        description={
          deleting
            ? `"${deleting.key}" and every value stored under it will be removed. Users lose that metadata.`
            : ""
        }
        confirmLabel="Delete field"
        pending={deleteMutation.isPending}
        onConfirm={async () => {
          if (deleting) {
            await deleteMutation.mutateAsync(deleting.id);
            setDeleting(null);
          }
        }}
      />
    </div>
  );
}
