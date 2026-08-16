"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
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
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { useCreateUserField, useUpdateUserField } from "@/hooks/mutations";
import { useForm } from "@/hooks/use-form";
import { userFieldSchema } from "@/lib/validation";
import { USER_FIELD_TYPE_META, type UserFieldType } from "@/types/enums";
import type { OrganisationUserFieldResponse } from "@/types/api";

const FIELD_TYPE_OPTIONS: UserFieldType[] = ["STRING", "NUMBER", "BOOLEAN", "DATE", "EMAIL", "LINK"];

interface OrgFieldDialogProps {
  platformSlug: string;
  organisationSlug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  field?: OrganisationUserFieldResponse;
}

export function OrgFieldDialog({ platformSlug, organisationSlug, open, onOpenChange, field }: OrgFieldDialogProps) {
  const isEdit = !!field;
  const create = useCreateUserField(platformSlug, organisationSlug);
  const update = useUpdateUserField(platformSlug, organisationSlug);
  const pending = create.isPending || update.isPending;

  const form = useForm(userFieldSchema, {
    key: field?.key ?? "",
    fieldType: "STRING",
    loginEnabled: false,
    required: false,
  });

  useEffect(() => {
    if (!open) return;
    if (field) {
      form.setValues({
        key: field.key,
        fieldType: field.fieldType,
        loginEnabled: field.loginEnabled,
        required: field.required,
      });
    } else {
      form.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, field]);

  const submit = async () => {
    const data = form.values;
    if (isEdit && field) {
      await update.mutateAsync({
        fieldId: field.id,
        body: { fieldType: data.fieldType, loginEnabled: data.loginEnabled, required: data.required },
      });
    } else {
      await create.mutateAsync({
        key: data.key,
        fieldType: data.fieldType,
        loginEnabled: data.loginEnabled,
        required: data.required,
      });
    }
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !pending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit user field" : "New user field"}</DialogTitle>
          <DialogDescription>
            The attribute name is used in user metadata and can never change.
          </DialogDescription>
        </DialogHeader>
        <form id="org-field-form" onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
          <FormField
            label="Attribute name"
            htmlFor="uf-key"
            error={form.errors.key}
            hint="Lowercase letters, digits and hyphens"
          >
            <Input
              id="uf-key"
              disabled={isEdit}
              placeholder="employee-id"
              value={form.values.key}
              onChange={(e) => form.setValue("key", e.target.value)}
            />
          </FormField>
          <FormField label="Value type" error={form.errors.fieldType}>
            <Select
              value={form.values.fieldType}
              onValueChange={(value) => form.setValue("fieldType", value as UserFieldType)}
            >
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Select a type" />
              </SelectTrigger>
              <SelectContent>
                {FIELD_TYPE_OPTIONS.map((type) => (
                  <SelectItem key={type} value={type}>
                    {USER_FIELD_TYPE_META[type].label} — {USER_FIELD_TYPE_META[type].description}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
          {isEdit && field && form.values.fieldType !== field.fieldType ? (
            <p className="rounded-md border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-600">
              Changing the value type is rejected while users still have values for this field —
              clear the field&apos;s values first, then change the type.
            </p>
          ) : null}
          <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div>
              <Label htmlFor="uf-login" className="text-sm font-medium">
                Login identifier
              </Label>
              <p className="text-xs text-muted-foreground">
                When enabled, a user&apos;s value for this field can also be used to log in.
              </p>
            </div>
            <Switch
              id="uf-login"
              checked={form.values.loginEnabled ?? false}
              onCheckedChange={(checked) => form.setValue("loginEnabled", checked)}
            />
          </div>
          <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div>
              <Label htmlFor="uf-required" className="text-sm font-medium">
                Required
              </Label>
              <p className="text-xs text-muted-foreground">
                Every user must have a value for this field; users missing it get an update-profile
                prompt at login.
              </p>
            </div>
            <Switch
              id="uf-required"
              checked={form.values.required ?? false}
              onCheckedChange={(checked) => form.setValue("required", checked)}
            />
          </div>
          {form.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {form.submitError}
            </p>
          ) : null}
        </form>
        <DialogFooter>
          <Button type="submit" form="org-field-form" disabled={pending} className="gap-2">
            {pending ? <Loader2 className="animate-spin" /> : null}
            {isEdit ? "Save changes" : "Create field"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
