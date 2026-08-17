"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { PermissionCheckboxes } from "@/components/organisations/permission-checkboxes";
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
import { Switch } from "@/components/ui/switch";
import { useCreateOrgRole, useUpdateOrgRole } from "@/hooks/mutations";
import { useForm } from "@/hooks/use-form";
import { roleSchema } from "@/lib/validation";
import type { OrganisationRoleResponse } from "@/types/api";
import type { Permission } from "@/types/enums";

interface OrgRoleDialogProps {
  platformSlug: string;
  organisationId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  role?: OrganisationRoleResponse;
}

export function OrgRoleDialog({ platformSlug, organisationId, open, onOpenChange, role }: OrgRoleDialogProps) {
  const isEdit = !!role;
  const create = useCreateOrgRole(platformSlug, organisationId);
  const update = useUpdateOrgRole(platformSlug, organisationId);
  const pending = create.isPending || update.isPending;

  const form = useForm(roleSchema, {
    name: role?.name ?? "",
    permissions: role?.permissions ?? ([] as Permission[]),
    isDefault: role?.isDefault ?? false,
  });

  useEffect(() => {
    if (!open) return;
    form.setValues({ name: role?.name ?? "", permissions: role?.permissions ?? [], isDefault: role?.isDefault ?? false });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, role]);

  const submit = async () => {
    const data = form.values;
    if (isEdit && role) {
      await update.mutateAsync({ roleId: role.id, body: { name: data.name, permissions: data.permissions, isDefault: data.isDefault } });
    } else {
      await create.mutateAsync({ name: data.name, permissions: data.permissions, isDefault: data.isDefault });
    }
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !pending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit role" : "New role"}</DialogTitle>
          <DialogDescription>
            Roles group the app-fixed permissions. Users hold roles — never direct permissions.
          </DialogDescription>
        </DialogHeader>
        <form id="org-role-form" onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
          <FormField label="Role name" htmlFor="role-name" error={form.errors.name}>
            <Input
              id="role-name"
              placeholder="e.g. manager"
              value={form.values.name}
              onChange={(e) => form.setValue("name", e.target.value)}
            />
          </FormField>
          <FormField label="Permissions" hint="A role with no permissions is valid but grants no access.">
            <PermissionCheckboxes
              selected={form.values.permissions}
              onChange={(permissions) => form.setValue("permissions", permissions)}
            />
          </FormField>
          <FormField label="Default role" hint="New users of this organisation inherit this role automatically on register.">
            <label className="flex items-center justify-between gap-4 rounded-lg border p-3">
              <span className="text-sm">Assign to new users on register</span>
              <Switch checked={form.values.isDefault} onCheckedChange={(v) => form.setValue("isDefault", v)} />
            </label>
          </FormField>
          {form.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {form.submitError}
            </p>
          ) : null}
        </form>
        <DialogFooter>
          <Button type="submit" form="org-role-form" disabled={pending} className="gap-2">
            {pending ? <Loader2 className="animate-spin" /> : null}
            {isEdit ? "Save changes" : "Create role"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
