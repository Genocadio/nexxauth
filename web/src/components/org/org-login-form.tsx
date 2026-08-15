"use client";

import { Loader2 } from "lucide-react";
import { useState } from "react";
import { loginOrg } from "@/app/org/[platformSlug]/[organisationId]/actions";
import { FormField } from "@/components/shared/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useForm } from "@/hooks/use-form";
import { orgLoginSchema } from "@/lib/validation";

interface OrgLoginFormProps {
  platformSlug: string;
  organisationId: number;
}

/**
 * The org portal login form. Submits through the `loginOrg` server action,
 * which authenticates server-side, sets the httpOnly session cookies and
 * redirects to the profile. Errors are returned to the form and shown inline.
 */
export function OrgLoginForm({ platformSlug, organisationId }: OrgLoginFormProps) {
  const form = useForm(orgLoginSchema, { identifier: "", password: "" });
  const [pending, setPending] = useState(false);

  const submit = async (values: { identifier: string; password: string }) => {
    setPending(true);
    try {
      const result = await loginOrg({ platformSlug, organisationId, ...values });
      if (!result.ok) form.setSubmitError(result.error);
      // On success the action redirects to the profile — nothing to do here.
    } finally {
      setPending(false);
    }
  };

  return (
    <form onSubmit={form.handleSubmit(submit)} className="space-y-4">
      <FormField
        label="Identifier"
        htmlFor="org-identifier"
        error={form.errors.identifier}
        hint="Your username, email or a login-enabled field value"
      >
        <Input
          id="org-identifier"
          autoComplete="username"
          value={form.values.identifier}
          onChange={(e) => form.setValue("identifier", e.target.value)}
        />
      </FormField>
      <FormField label="Password" htmlFor="org-password" error={form.errors.password}>
        <Input
          id="org-password"
          type="password"
          autoComplete="current-password"
          value={form.values.password}
          onChange={(e) => form.setValue("password", e.target.value)}
        />
      </FormField>
      {form.submitError ? (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {form.submitError}
        </p>
      ) : null}
      <Button type="submit" className="w-full" disabled={pending}>
        {pending ? <Loader2 className="animate-spin" /> : null}
        Sign in
      </Button>
    </form>
  );
}
