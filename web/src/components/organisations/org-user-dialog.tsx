"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { RoleCheckboxes } from "@/components/organisations/role-checkboxes";
import { FormField } from "@/components/shared/form-field";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useCreateOrgUser, useUpdateOrgUser } from "@/hooks/mutations";
import { useForm } from "@/hooks/use-form";
import { orgUserFormSchema } from "@/lib/validation";
import { z } from "zod";
import type { OrganisationRoleResponse, OrganisationUserFieldResponse, OrganisationUserResponse } from "@/types/api";

interface OrgUserDialogProps {
  platformSlug: string;
  organisationSlug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  roles: OrganisationRoleResponse[];
  fields: OrganisationUserFieldResponse[];
  useEmailAsUsername: boolean;
  /** When provided the dialog edits this user, otherwise it creates one. */
  user?: OrganisationUserResponse;
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
 * Controlled dialog. The inner component is keyed by the edited user so its
 * form/metadata state is seeded fresh every time the dialog opens — no effects
 * needed to re-seed state.
 */
export function OrgUserDialog(props: OrgUserDialogProps) {
  const { open, onOpenChange, user } = props;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? <OrgUserDialogInner key={user?.id ?? "new"} {...props} /> : null}
    </Dialog>
  );
}

function OrgUserDialogInner({
  platformSlug,
  organisationSlug,
  onOpenChange,
  roles,
  fields,
  useEmailAsUsername,
  user,
}: OrgUserDialogProps) {
  const isEdit = !!user;
  const create = useCreateOrgUser(platformSlug, organisationSlug);
  const update = useUpdateOrgUser(platformSlug, organisationSlug);
  const pending = create.isPending || update.isPending;

  const [metadata, setMetadata] = useState<Record<string, string>>(user?.metadata ?? {});
  const [clearPassword, setClearPassword] = useState(false);

  const form = useForm(
    orgUserFormSchema,
    user
      ? {
          firstName: user.firstName,
          lastName: user.lastName,
          username: user.username ?? "",
          email: user.email ?? "",
          phone: user.phone ?? "",
          enabled: user.enabled,
          roleIds: user.roles.map((r) => r.id),
          password: "",
        }
      : EMPTY_VALUES,
  );

  const close = () => onOpenChange(false);

  const submit = async () => {
    const data = form.values;
    if (isEdit && user) {
      await update.mutateAsync({
        userId: user.id,
        body: {
          firstName: data.firstName,
          lastName: data.lastName,
          username: data.username.trim() || undefined,
          email: data.email.trim() || undefined,
          phone: data.phone.trim() || undefined,
          enabled: data.enabled,
          roleIds: data.roleIds,
          // Blank password = no change; clearPassword sends "" to remove auth.
          password: clearPassword ? "" : data.password.trim() || undefined,
          metadata,
        },
      });
    } else {
      await create.mutateAsync({
        firstName: data.firstName,
        lastName: data.lastName,
        username: data.username.trim() || undefined,
        email: data.email.trim() || undefined,
        phone: data.phone.trim() || undefined,
        roleIds: data.roleIds,
        password: data.password.trim() || undefined,
        metadata,
      });
    }
    close();
  };

  return (
    <DialogContent className="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>{isEdit ? "Edit organisation user" : "Create organisation user"}</DialogTitle>
        <DialogDescription>
          {isEdit
            ? "Update profile, roles, password or user-field values."
            : "Without a password the user can sign up themselves later, but cannot log in yet."}
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
          <FormField label="Last name" htmlFor="ou-lastName" error={form.errors.lastName}>
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

        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label={isEdit ? "New password (optional)" : "Password (optional)"}
            htmlFor="ou-password"
            error={form.errors.password}
            hint={isEdit ? "Leave blank to keep the current password." : "Omit to create the user without login."}
          >
            <Input
              id="ou-password"
              type="password"
              autoComplete="new-password"
              value={form.values.password}
              onChange={(e) => form.setValue("password", e.target.value)}
            />
          </FormField>
          {isEdit ? (
            <div className="flex h-full items-end pb-1">
              <label className="flex items-center gap-2 text-sm">
                <Checkbox
                  checked={clearPassword}
                  onCheckedChange={(checked) => setClearPassword(!!checked)}
                  disabled={!user?.authType}
                />
                Remove password (can&apos;t log in)
              </label>
            </div>
          ) : null}
        </div>

        {isEdit ? (
          <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div>
              <Label htmlFor="ou-enabled" className="text-sm font-medium">
                Account enabled
              </Label>
              <p className="text-xs text-muted-foreground">
                Disabled users can&apos;t log in; existing sessions stop working immediately.
              </p>
            </div>
            <Switch
              id="ou-enabled"
              checked={form.values.enabled}
              onCheckedChange={(checked) => form.setValue("enabled", checked)}
            />
          </div>
        ) : null}

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
          {isEdit ? "Save changes" : "Create user"}
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
