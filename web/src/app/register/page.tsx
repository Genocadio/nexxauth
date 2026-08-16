"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { AuthShell } from "@/components/auth/auth-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/shared/form-field";
import { SlugField } from "@/components/shared/slug-field";
import { useIsClient } from "@/hooks/use-is-client";
import { usePlatformRegister } from "@/hooks/use-auth";
import { useForm } from "@/hooks/use-form";
import { registerSchema } from "@/lib/validation";
import { selectPlatformSession, useAppSelector } from "@/store/store";

export default function RegisterPage() {
  const register = usePlatformRegister();
  const router = useRouter();
  const session = useAppSelector(selectPlatformSession);
  const mounted = useIsClient();

  const form = useForm(registerSchema, {
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    phone: "",
    platformName: "",
    platformSlug: "",
  });

  // True from the moment the user submits the form: after a successful
  // register the session appears in the store, and the bounce-below must not
  // fight the onboarding redirect fired by the register mutation.
  const submitted = useRef(false);

  useEffect(() => {
    // Already signed in (e.g. a returning user visiting /register): go to the
    // console. Skipped right after a fresh register — that navigation is
    // handled by usePlatformRegister (onboarding wizard).
    if (mounted && session && !submitted.current) router.replace("/console");
  }, [mounted, session, router]);

  if (!mounted || session) return null;

  return (
    <AuthShell
      title="Create your platform"
      subtitle="Your platform becomes the tenant that owns your organisations."
      footer={
        <>
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form
        onSubmit={form.handleSubmit((data) => {
          submitted.current = true;
          return register.mutateAsync({
            firstName: data.firstName,
            lastName: data.lastName,
            email: data.email,
            password: data.password,
            phone: data.phone?.trim() || undefined,
            platformName: data.platformName,
            platformSlug: data.platformSlug?.trim() || undefined,
          });
        })}
        className="space-y-4"
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="First name" htmlFor="firstName" error={form.errors.firstName}>
            <Input
              id="firstName"
              autoComplete="given-name"
              value={form.values.firstName}
              onChange={(e) => form.setValue("firstName", e.target.value)}
            />
          </FormField>
          <FormField label="Last name" htmlFor="lastName" error={form.errors.lastName}>
            <Input
              id="lastName"
              autoComplete="family-name"
              value={form.values.lastName}
              onChange={(e) => form.setValue("lastName", e.target.value)}
            />
          </FormField>
        </div>
        <FormField label="Work email" htmlFor="email" error={form.errors.email}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@company.com"
            value={form.values.email}
            onChange={(e) => form.setValue("email", e.target.value)}
          />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="Platform name"
            htmlFor="platformName"
            error={form.errors.platformName}
            hint="Shown to everyone in your organisation"
          >
            <Input
              id="platformName"
              placeholder="Acme Inc."
              value={form.values.platformName}
              onChange={(e) => form.setValue("platformName", e.target.value)}
            />
          </FormField>
          <SlugField
            id="platformSlug"
            label="Platform slug"
            name={form.values.platformName}
            value={form.values.platformSlug}
            onChange={(v) => form.setValue("platformSlug", v)}
            type="PLATFORM"
            error={form.errors.platformSlug}
            hint="Lowercase letters, digits and hyphens. Derived from the name if omitted — and it can never change."
            placeholder="acme"
          />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Password" htmlFor="password" error={form.errors.password} hint="At least 8 characters">
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              value={form.values.password}
              onChange={(e) => form.setValue("password", e.target.value)}
            />
          </FormField>
          <FormField label="Phone (optional)" htmlFor="phone" error={form.errors.phone}>
            <Input
              id="phone"
              autoComplete="tel"
              value={form.values.phone}
              onChange={(e) => form.setValue("phone", e.target.value)}
            />
          </FormField>
        </div>
        {form.submitError ? (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {form.submitError}
          </p>
        ) : null}
        <Button type="submit" className="w-full" disabled={register.isPending}>
          {register.isPending ? <Loader2 className="animate-spin" /> : null}
          Create platform
        </Button>
      </form>
    </AuthShell>
  );
}
