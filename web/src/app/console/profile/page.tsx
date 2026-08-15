"use client";

import { useEffect, useState } from "react";
import { Loader2, Lock, UserRound } from "lucide-react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { authApi } from "@/api/auth";
import { FadeIn } from "@/components/shared/fade-in";
import { FormField } from "@/components/shared/form-field";
import { PageHeader } from "@/components/shared/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useMyProfile } from "@/hooks/queries";
import { useForm } from "@/hooks/use-form";
import { changePasswordSchema, updateProfileSchema } from "@/lib/validation";
import { clearPlatformSession, setPlatformUser } from "@/store/authSlice";
import { useAppDispatch, useAppSelector, selectPlatformSession } from "@/store/store";
import { getErrorMessage } from "@/types/errors";

export default function ProfilePage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Profile & security" description="Your account details and password." />
      <ProfileCard />
      <PasswordCard />
    </div>
  );
}

function ProfileCard() {
  const dispatch = useAppDispatch();
  const session = useAppSelector(selectPlatformSession);
  const profile = useMyProfile();
  const user = profile.data ?? session?.user;

  const form = useForm(updateProfileSchema, {
    firstName: user?.firstName ?? "",
    lastName: user?.lastName ?? "",
    phone: user?.phone ?? "",
  });

  useEffect(() => {
    if (user) {
      form.setValues({ firstName: user.firstName, lastName: user.lastName, phone: user.phone ?? "" });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id, user?.firstName, user?.lastName, user?.phone]);

  const submit = async () => {
    const data = form.values;
    const updated = await authApi.updateProfile({
      firstName: data.firstName,
      lastName: data.lastName,
      phone: data.phone?.trim() || undefined,
    });
    dispatch(setPlatformUser(updated));
    toast.success("Profile updated");
  };

  return (
    <FadeIn>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <UserRound className="h-4 w-4 text-primary" /> Profile
          </CardTitle>
          <CardDescription>How you appear to other members.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField label="First name" htmlFor="pf-firstName" error={form.errors.firstName}>
                <Input
                  id="pf-firstName"
                  value={form.values.firstName}
                  onChange={(e) => form.setValue("firstName", e.target.value)}
                />
              </FormField>
              <FormField label="Last name" htmlFor="pf-lastName" error={form.errors.lastName}>
                <Input
                  id="pf-lastName"
                  value={form.values.lastName}
                  onChange={(e) => form.setValue("lastName", e.target.value)}
                />
              </FormField>
            </div>
            <FormField label="Phone" htmlFor="pf-phone" error={form.errors.phone}>
              <Input
                id="pf-phone"
                value={form.values.phone}
                onChange={(e) => form.setValue("phone", e.target.value)}
              />
            </FormField>
            <FormField label="Email" hint="Email is your sign-in identifier and cannot be changed here.">
              <Input value={user?.email ?? ""} disabled />
            </FormField>
            {form.submitError ? (
              <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {form.submitError}
              </p>
            ) : null}
            <div className="flex justify-end">
              <Button type="submit" className="gap-2">
                Save profile
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </FadeIn>
  );
}

function PasswordCard() {
  const dispatch = useAppDispatch();
  const router = useRouter();
  const form = useForm(changePasswordSchema, { currentPassword: "", newPassword: "" });
  const [pending, setPending] = useState(false);

  const submit = async () => {
    const data = form.values;
    setPending(true);
    try {
      await authApi.changePassword({ currentPassword: data.currentPassword, newPassword: data.newPassword });
      toast.success("Password changed — please sign in again");
      dispatch(clearPlatformSession());
      router.replace("/login");
    } catch (error) {
      form.setSubmitError(getErrorMessage(error));
    } finally {
      setPending(false);
    }
  };

  return (
    <FadeIn delay={0.08}>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Lock className="h-4 w-4 text-primary" /> Change password
          </CardTitle>
          <CardDescription>
            Changing your password signs out every device, including this one.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
            <FormField label="Current password" htmlFor="pw-current" error={form.errors.currentPassword}>
              <Input
                id="pw-current"
                type="password"
                autoComplete="current-password"
                value={form.values.currentPassword}
                onChange={(e) => form.setValue("currentPassword", e.target.value)}
              />
            </FormField>
            <FormField
              label="New password"
              htmlFor="pw-new"
              error={form.errors.newPassword}
              hint="At least 8 characters"
            >
              <Input
                id="pw-new"
                type="password"
                autoComplete="new-password"
                value={form.values.newPassword}
                onChange={(e) => form.setValue("newPassword", e.target.value)}
              />
            </FormField>
            {form.submitError ? (
              <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {form.submitError}
              </p>
            ) : null}
            <div className="flex justify-end">
              <Button type="submit" disabled={pending} variant="outline" className="gap-2">
                {pending ? <Loader2 className="animate-spin" /> : null}
                Change password
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </FadeIn>
  );
}
