"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { RoleCheckboxes } from "@/components/organisations/role-checkboxes";
import { FormField } from "@/components/shared/form-field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useCreateOrgUser } from "@/hooks/mutations";
import { useForm } from "@/hooks/use-form";
import { orgUserFormSchema } from "@/lib/validation";
import { z } from "zod";
import type { OrganisationRoleResponse, OrganisationUserFieldResponse } from "@/types/api";

interface OrgUserDialogProps {
  platformSlug: string;
  organisationId: number;
  onOpenChange: (open: boolean) => void;
  roles: OrganisationRoleResponse[];
  fields: OrganisationUserFieldResponse[];
  useEmailAsUsername: boolean;
}

const EMPTY_VALUES = {
  firstName: "",
  lastName: "",
  username: "",
  email: "",
  phone: "",
  enabled: true,
  roleIds: [] as number[],
  password: "",
} satisfies z.input<typeof orgUserFormSchema>;

/**
 * Create dialog. Editing an existing user lives in OrgUserSettingsDialog, which
 * is where addresses, roles, profile and credentials are managed — this stays
 * the single "add user" path so there is one place that creates accounts.
 */
export function OrgUserDialog({ open, onOpenChange, ...rest }: OrgUserDialogProps & { open: boolean }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? <OrgUserDialogInner {...rest} onOpenChange={onOpenChange} /> : null}
    </Dialog>
  );
}

function OrgUserDialogInner({
  platformSlug,
  organisationId,
  onOpenChange,
  roles,
  fields,
  useEmailAsUsername,
}: Omit<OrgUserDialogProps, "open">) {
  const create = useCreateOrgUser(platformSlug, organisationId);
  const pending = create.isPending;

  const [metadata, setMetadata] = useState<Record<string, string>>({});

  const form = useForm(orgUserFormSchema, EMPTY_VALUES);

  const close = () => onOpenChange(false);

  const submit = async () => {
    const data = form.values;
    await create.mutateAsync({
      firstName: data.firstName,
      lastName: data.lastName,
      username: data.username.trim() || undefined,
      email: data.email.trim() || undefined,
      phone: data.phone.trim() || undefined,
      roleIds: data.roleIds,
      // Omitted without a password: the account is created as a placeholder
      // with no usable credential, and an admin configures it afterwards.
      password: data.password.trim() || undefined,
      metadata,
    });
    close();
  };

  return (
    <DialogContent className="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>Create organisation user</DialogTitle>
        <DialogDescription>
          Without a password the account is created as a placeholder: it cannot sign in until an
          administrator gives it a password or a one-time-code login method.
        </DialogDescription>
      </DialogHeader>
      <form id="org-user-form" onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="First name" htmlFor="ou-firstName" error={form.errors.firstName}>
            <Input
              id="ou-firstName"
              value={form.values.firstName}
              onChange={(e) => form.setValue("firstName", e.target.value)}
            />
          </FormField>
          <FormField label="Last name" htmlFor="ou-lastName" error={form.errors.lastName} hint="Optional">
            <Input
              id="ou-lastName"
              value={form.values.lastName}
              onChange={(e) => form.setValue("lastName", e.target.value)}
            />
          </FormField>
        </div>

        {useEmailAsUsername ? (
          <>
            <FormField
              label="Email (login identifier)"
              htmlFor="ou-email"
              error={form.errors.email}
              hint="This organisation uses email as the username."
            >
              <Input
                id="ou-email"
                type="email"
                value={form.values.email}
                onChange={(e) => form.setValue("email", e.target.value)}
              />
            </FormField>
            <FormField label="Username (optional)" htmlFor="ou-username" error={form.errors.username}>
              <Input
                id="ou-username"
                value={form.values.username}
                onChange={(e) => form.setValue("username", e.target.value)}
              />
            </FormField>
            <FormField label="Phone" htmlFor="ou-phone" error={form.errors.phone}>
              <Input
                id="ou-phone"
                type="tel"
                value={form.values.phone}
                onChange={(e) => form.setValue("phone", e.target.value)}
              />
            </FormField>
          </>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Username" htmlFor="ou-username" error={form.errors.username}>
              <Input
                id="ou-username"
                value={form.values.username}
                onChange={(e) => form.setValue("username", e.target.value)}
              />
            </FormField>
            <FormField label="Email" htmlFor="ou-email" error={form.errors.email}>
              <Input
                id="ou-email"
                type="email"
                value={form.values.email}
                onChange={(e) => form.setValue("email", e.target.value)}
              />
            </FormField>
            <FormField label="Phone" htmlFor="ou-phone" error={form.errors.phone}>
              <Input
                id="ou-phone"
                type="tel"
                value={form.values.phone}
                onChange={(e) => form.setValue("phone", e.target.value)}
              />
            </FormField>
          </div>
        )}

        <FormField label="Roles" hint="Users can hold several roles; permissions come from their roles.">
          <RoleCheckboxes
            roles={roles}
            selected={form.values.roleIds}
            onChange={(roleIds) => form.setValue("roleIds", roleIds)}
          />
        </FormField>

        <FormField
          label="Password (optional)"
          htmlFor="ou-password"
          error={form.errors.password}
          hint="Omit to create the user without login, then configure them from the Settings dialog."
        >
          <Input
            id="ou-password"
            type="password"
            autoComplete="new-password"
            value={form.values.password}
            onChange={(e) => form.setValue("password", e.target.value)}
          />
        </FormField>

        {fields.length > 0 ? (
          <div className="space-y-4 rounded-lg border p-3">
            <p className="text-sm font-medium">User fields</p>
            {fields.map((field) => (
              <FieldValueInput
                key={field.id}
                field={field}
                value={metadata[field.key] ?? ""}
                onChange={(value) => setMetadata((prev) => ({ ...prev, [field.key]: value }))}
              />
            ))}
            <p className="text-xs text-muted-foreground">Leave a field blank to remove its value.</p>
          </div>
        ) : null}

        {form.submitError ? (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {form.submitError}
          </p>
        ) : null}
      </form>
      <DialogFooter>
        <Button type="submit" form="org-user-form" disabled={pending} className="gap-2">
          {pending ? <Loader2 className="animate-spin" /> : null}
          Create user
        </Button>
      </DialogFooter>
    </DialogContent>
  );
}

/**
 * Input for one user-field value, matched to the field's type: a boolean
 * yes/no select, a native date picker, a decimal number input, or plain text.
 * An empty value removes the stored metadata (the backend treats blank as
 * removal).
 */
function FieldValueInput({
  field,
  value,
  onChange,
}: {
  field: OrganisationUserFieldResponse;
  value: string;
  onChange: (value: string) => void;
}) {
  const id = `ou-field-${field.key}`;

  if (field.fieldType === "BOOLEAN") {
    const selected = value || "unset";
    return (
      <FormField label={field.key} htmlFor={id}>
        <Select value={selected} onValueChange={(next) => onChange(next === "unset" ? "" : next)}>
          <SelectTrigger id={id} className="w-full">
            <SelectValue placeholder="Not set" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="unset">Not set</SelectItem>
            <SelectItem value="true">Yes</SelectItem>
            <SelectItem value="false">No</SelectItem>
          </SelectContent>
        </Select>
      </FormField>
    );
  }

  return (
    <FormField
      label={field.key}
      htmlFor={id}
      hint={field.fieldType === "NUMBER" ? "Decimal number (e.g. 1.5)" : field.fieldType === "DATE" ? "Date (yyyy-MM-dd)" : undefined}
    >
      <Input
        id={id}
        type={field.fieldType === "DATE" ? "date" : field.fieldType === "NUMBER" ? "number" : "text"}
        step={field.fieldType === "NUMBER" ? "any" : undefined}
        placeholder={field.key}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </FormField>
  );
}
