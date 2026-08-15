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
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/form-field";
import { SlugField } from "@/components/shared/slug-field";
import { useCreateOrganisation } from "@/hooks/mutations";
import { useForm } from "@/hooks/use-form";
import { createOrganisationSchema } from "@/lib/validation";

interface OrganisationDialogProps {
  platformSlug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function OrganisationDialog({ platformSlug, open, onOpenChange }: OrganisationDialogProps) {
  const create = useCreateOrganisation(platformSlug);
  const form = useForm(createOrganisationSchema, { name: "", slug: "", description: "" });

  useEffect(() => {
    if (open) form.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = async () => {
    const data = form.values;
    await create.mutateAsync({
      name: data.name,
      slug: data.slug?.trim() || undefined,
      description: data.description?.trim() || undefined,
    });
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !create.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>New organisation</DialogTitle>
          <DialogDescription>
            Organisations are independent tenants with their own users, roles and security policies.
          </DialogDescription>
        </DialogHeader>
        <form id="organisation-form" onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
          <FormField label="Organisation name" htmlFor="org-name" error={form.errors.name}>
            <Input
              id="org-name"
              placeholder="Acme Labs"
              value={form.values.name}
              onChange={(e) => form.setValue("name", e.target.value)}
            />
          </FormField>
          <SlugField
            id="org-slug"
            label="Slug"
            name={form.values.name}
            value={form.values.slug}
            onChange={(v) => form.setValue("slug", v)}
            type="ORGANISATION"
            platformSlug={platformSlug}
            error={form.errors.slug}
            hint="Lowercase letters, digits and hyphens. Derived from the name when omitted."
            placeholder="acme-labs"
          />
          <FormField
            label="Description (optional)"
            htmlFor="org-description"
            error={form.errors.description}
          >
            <Textarea
              id="org-description"
              rows={3}
              placeholder="What is this organisation for?"
              value={form.values.description}
              onChange={(e) => form.setValue("description", e.target.value)}
            />
          </FormField>
          {form.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {form.submitError}
            </p>
          ) : null}
        </form>
        <DialogFooter>
          <Button type="submit" form="organisation-form" disabled={create.isPending} className="gap-2">
            {create.isPending ? <Loader2 className="animate-spin" /> : null}
            Create organisation
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
