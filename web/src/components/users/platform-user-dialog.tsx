"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
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
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { FormField } from "@/components/shared/form-field";
import { useCreatePlatformUser, useUpdatePlatformUser } from "@/hooks/mutations";
import { useForm } from "@/hooks/use-form";
import { platformUserFormSchema } from "@/lib/validation";
import { z } from "zod";
import type { PlatformUserResponse } from "@/types/api";
import { ROLE_META, type Role } from "@/types/enums";

const ROLE_OPTIONS: Role[] = ["SUPER_USER", "READ_ONLY"];

interface PlatformUserDialogProps {
  platformSlug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** When provided the dialog edits this user, otherwise it creates one. */
  user?: PlatformUserResponse;
}

export function PlatformUserDialog({ platformSlug, open, onOpenChange, user }: PlatformUserDialogProps) {
  const isEdit = !!user;
  const create = useCreatePlatformUser(platformSlug);
  const update = useUpdatePlatformUser();
  const pending = create.isPending || update.isPending;

  const form = useForm(platformUserFormSchema, {
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    phone: "",
    role: "READ_ONLY",
    enabled: true,
  } satisfies z.input<typeof platformUserFormSchema>);

  // Re-seed the form whenever a (different) user is opened, and clear on close.
  useEffect(() => {
    if (!open) return;
    if (user) {
      form.setValues({
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
        password: "",
        phone: user.phone ?? "",
        role: user.role,
        enabled: user.enabled,
      });
    } else {
      form.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, user]);

  const close = () => onOpenChange(false);

  const submit = async () => {
    const data = form.values;
    if (isEdit && user) {
      await update.mutateAsync({
        userId: user.id,
        body: {
          firstName: data.firstName,
          lastName: data.lastName,
          phone: data.phone?.trim() || undefined,
          role: data.role,
          enabled: data.enabled,
        },
      });
    } else {
      if (!data.email) {
        form.setErrors({ email: "Email is required" });
        return;
      }
      if (!data.password) {
        form.setErrors({ password: "Password is required" });
        return;
      }
      await create.mutateAsync({
        firstName: data.firstName,
        lastName: data.lastName,
        email: data.email,
        password: data.password,
        phone: data.phone?.trim() || undefined,
        role: data.role,
      });
    }
    close();
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !pending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit platform user" : "Add platform user"}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update the member's profile, role or access."
              : "New members get read-only access by default."}
          </DialogDescription>
        </DialogHeader>
        <form
          id="platform-user-form"
          onSubmit={form.handleSubmit(() => submit())}
          className="space-y-4"
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="First name" htmlFor="pu-firstName" error={form.errors.firstName}>
              <Input
                id="pu-firstName"
                value={form.values.firstName}
                onChange={(e) => form.setValue("firstName", e.target.value)}
              />
            </FormField>
            <FormField label="Last name" htmlFor="pu-lastName" error={form.errors.lastName}>
              <Input
                id="pu-lastName"
                value={form.values.lastName}
                onChange={(e) => form.setValue("lastName", e.target.value)}
              />
            </FormField>
          </div>

          {!isEdit ? (
            <FormField label="Email" htmlFor="pu-email" error={form.errors.email}>
              <Input
                id="pu-email"
                type="email"
                value={form.values.email}
                onChange={(e) => form.setValue("email", e.target.value)}
              />
            </FormField>
          ) : null}

          {!isEdit ? (
            <FormField label="Password" htmlFor="pu-password" error={form.errors.password} hint="At least 8 characters">
              <Input
                id="pu-password"
                type="password"
                autoComplete="new-password"
                value={form.values.password}
                onChange={(e) => form.setValue("password", e.target.value)}
              />
            </FormField>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Phone" htmlFor="pu-phone" error={form.errors.phone}>
              <Input
                id="pu-phone"
                value={form.values.phone}
                onChange={(e) => form.setValue("phone", e.target.value)}
              />
            </FormField>
            <FormField label="Role" error={form.errors.role}>
              <Select
                value={form.values.role}
                onValueChange={(value) => form.setValue("role", value as Role)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select a role" />
                </SelectTrigger>
                <SelectContent>
                  {ROLE_OPTIONS.map((role) => (
                    <SelectItem key={role} value={role}>
                      {ROLE_META[role].label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
          </div>

          {isEdit ? (
            <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
              <div>
                <Label htmlFor="pu-enabled" className="text-sm font-medium">
                  Account enabled
                </Label>
                <p className="text-xs text-muted-foreground">
                  Disabled users can&apos;t sign in; their tokens stop working immediately.
                </p>
              </div>
              <Switch
                id="pu-enabled"
                checked={form.values.enabled}
                onCheckedChange={(checked) => form.setValue("enabled", checked)}
              />
            </div>
          ) : null}

          {form.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {form.submitError}
            </p>
          ) : null}
        </form>
        <DialogFooter>
          <Button type="submit" form="platform-user-form" disabled={pending} className="gap-2">
            {pending ? <Loader2 className="animate-spin" /> : null}
            {isEdit ? "Save changes" : "Add user"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
