"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { AuthShell } from "@/components/auth/auth-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/shared/form-field";
import { useIsClient } from "@/hooks/use-is-client";
import { usePlatformLogin } from "@/hooks/use-auth";
import { useForm } from "@/hooks/use-form";
import { loginSchema } from "@/lib/validation";
import { selectPlatformSession, useAppSelector } from "@/store/store";

export default function LoginPage() {
  const login = usePlatformLogin();
  const router = useRouter();
  const session = useAppSelector(selectPlatformSession);
  const mounted = useIsClient();

  const form = useForm(loginSchema, { email: "", password: "" });

  useEffect(() => {
    if (mounted && session) router.replace("/console");
  }, [mounted, session, router]);

  if (!mounted || session) return null;

  return (
    <AuthShell
      title="Sign in to your platform"
      subtitle="Manage your platform, organisations and security policies."
      footer={
        <>
          New here?{" "}
          <Link href="/register" className="font-medium text-primary hover:underline">
            Create a platform
          </Link>
        </>
      }
    >
      <form onSubmit={form.handleSubmit((data) => login.mutateAsync(data))} className="space-y-4">
        <FormField label="Email" htmlFor="email" error={form.errors.email}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="you@company.com"
            value={form.values.email}
            onChange={(e) => form.setValue("email", e.target.value)}
          />
        </FormField>
        <FormField label="Password" htmlFor="password" error={form.errors.password}>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={form.values.password}
            onChange={(e) => form.setValue("password", e.target.value)}
          />
        </FormField>
        {form.submitError ? (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {form.submitError}
          </p>
        ) : null}
        <Button type="submit" className="w-full" disabled={login.isPending}>
          {login.isPending ? <Loader2 className="animate-spin" /> : null}
          Sign in
        </Button>
      </form>
    </AuthShell>
  );
}
