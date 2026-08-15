"use client";

import { useRouter } from "next/navigation";
import { ArrowRight, Loader2 } from "lucide-react";
import { useState } from "react";
import { AuthShell } from "@/components/auth/auth-shell";
import { FormField } from "@/components/shared/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export default function OrgEntryPage() {
  const router = useRouter();
  const [platformSlug, setPlatformSlug] = useState("");
  const [organisationId, setOrganisationId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const submit = async () => {
    const slug = platformSlug.trim();
    const id = Number(organisationId.trim());
    if (!slug || !organisationId.trim()) {
      setError("Both the platform slug and organisation id are required.");
      return;
    }
    if (!Number.isInteger(id) || id <= 0) {
      setError("Organisation id must be a positive number.");
      return;
    }
    setError(null);
    setPending(true);
    router.push(`/org/${slug}/${id}`);
  };

  return (
    <AuthShell
      title="Organisation portal"
      subtitle="Sign in with the credentials your administrator issued to you."
      footer="You should have received a platform slug and organisation id from your administrator."
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
        className="space-y-4"
      >
        <FormField
          label="Platform slug"
          htmlFor="org-platform"
          hint="The part before your organisation, e.g. acme"
        >
          <Input
            id="org-platform"
            placeholder="acme"
            value={platformSlug}
            onChange={(e) => setPlatformSlug(e.target.value)}
          />
        </FormField>
        <FormField
          label="Organisation id"
          htmlFor="org-id"
          hint="The numeric id of your organisation"
        >
          <Input
            id="org-id"
            placeholder="12"
            inputMode="numeric"
            value={organisationId}
            onChange={(e) => setOrganisationId(e.target.value)}
          />
        </FormField>
        {error ? (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
        ) : null}
        <Button type="submit" className="w-full" disabled={pending}>
          {pending ? <Loader2 className="animate-spin" /> : <ArrowRight />}
          Continue to sign in
        </Button>
      </form>
    </AuthShell>
  );
}
