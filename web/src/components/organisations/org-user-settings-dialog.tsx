"use client";

import { useState } from "react";
import {
  BadgeCheck,
  KeyRound,
  Loader2,
  Mail,
  Phone,
  Plus,
  Send,
  ShieldCheck,
  Star,
  Trash2,
} from "lucide-react";
import { RoleCheckboxes } from "@/components/organisations/role-checkboxes";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { FormField } from "@/components/shared/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
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
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  useAddOrgUserEmail,
  useAddOrgUserPhone,
  useDeleteOrgUser,
  useDeleteOrgUserEmail,
  useDeleteOrgUserPhone,
  useSendOrgUserPasswordReset,
  useSendOrgUserVerification,
  useSetOrgUserEmailVerified,
  useSetOrgUserPhoneVerified,
  useSetOrgUserPrimaryEmail,
  useSetOrgUserPrimaryPhone,
  useUpdateOrgUser,
} from "@/hooks/mutations";
import { useOrgUser } from "@/hooks/queries";
import { useForm } from "@/hooks/use-form";
import {
  addUserEmailSchema,
  addUserPhoneSchema,
  orgUserPasswordSchema,
  orgUserProfileSchema,
} from "@/lib/validation";
import { getErrorMessage } from "@/types/errors";
import { USER_LOGIN_METHOD_META, type UserLoginMethod } from "@/types/enums";
import type { OrganisationRoleResponse, OrganisationUserFieldResponse, OrganisationUserResponse } from "@/types/api";

interface OrgUserSettingsDialogProps {
  platformSlug: string;
  organisationId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  roles: OrganisationRoleResponse[];
  fields: OrganisationUserFieldResponse[];
  useEmailAsUsername: boolean;
  user: OrganisationUserResponse;
  /** Lifts the freshly saved user up so the row behind the dialog stays true. */
  onUserChange: (user: OrganisationUserResponse) => void;
}

/**
 * Per-user settings, split so each area can be saved on its own: addresses
 * (Main), roles, profile, and everything that decides how the account signs in
 * (Security).
 *
 * The list row is not the source of truth: it carries no address collections,
 * so the dialog loads the user itself and then prefers whatever a mutation has
 * just returned. The user id is the query key, so saving never re-reads stale
 * data, and the row behind the dialog is updated through onUserChange.
 */
export function OrgUserSettingsDialog(props: OrgUserSettingsDialogProps) {
  const { open, onOpenChange, user } = props;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? <OrgUserSettingsDialogInner key={user.id} {...props} /> : null}
    </Dialog>
  );
}

function OrgUserSettingsDialogInner({
  platformSlug,
  organisationId,
  onOpenChange,
  roles,
  fields,
  useEmailAsUsername,
  user: rowUser,
  onUserChange,
}: OrgUserSettingsDialogProps) {
  const loaded = useOrgUser(organisationId, rowUser.id);
  const [fresh, setFresh] = useState<OrganisationUserResponse | null>(null);

  // A saved mutation is newer than the query cache, so it wins until the refetch
  // lands; keying on the id drops it when a different user is opened.
  const user = fresh ?? loaded.data ?? rowUser;
  const adopt = (next: OrganisationUserResponse) => {
    setFresh(next);
    onUserChange(next);
  };

  const update = useUpdateOrgUser(platformSlug, organisationId);

  /** Every mutation returns the updated user; push it up so the dialog and the
   * row behind it never disagree about who can sign in. */
  const save = async (body: Parameters<typeof update.mutateAsync>[0]["body"]) => {
    adopt(await update.mutateAsync({ userId: user.id, body }));
  };

  return (
    <DialogContent className="sm:max-w-2xl">
      <DialogHeader>
        <DialogTitle>User settings</DialogTitle>
        <DialogDescription>
          Addresses, roles and profile for this user, and how the account signs in. Each section saves on
          its own.
        </DialogDescription>
      </DialogHeader>

      <Tabs defaultValue="main" className="gap-4">
        <TabsList>
          <TabsTrigger value="main">Main</TabsTrigger>
          <TabsTrigger value="roles">Roles</TabsTrigger>
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="security">Security</TabsTrigger>
        </TabsList>

        <TabsContent value="main" className="space-y-4">
          <AddressesTab
            platformSlug={platformSlug}
            organisationId={organisationId}
            user={user}
            onUserChange={adopt}
          />
        </TabsContent>

        <TabsContent value="roles" className="space-y-4">
          <FormField
            label="Roles"
            hint="Users can hold several roles; permissions come from the roles, not the user."
          >
            <RoleCheckboxes
              roles={roles}
              selected={roles.filter((r) => user.roles.includes(r.name)).map((r) => r.id)}
              onChange={(roleIds) => save({ roleIds })}
            />
          </FormField>
          {user.roles.length > 0 ? (
            <p className="text-xs text-muted-foreground">
              Changing the selection saves immediately. Currently held: {user.roles.join(", ") || "none"}.
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">
              This user holds no roles, so they have no permissions in this organisation.
            </p>
          )}
        </TabsContent>

        <TabsContent value="profile">
          <ProfileTab
            user={user}
            fields={fields}
            useEmailAsUsername={useEmailAsUsername}
            onSave={save}
            pending={update.isPending}
          />
        </TabsContent>

        <TabsContent value="security">
          <SecurityTab
            platformSlug={platformSlug}
            organisationId={organisationId}
            user={user}
            onUserChange={adopt}
          />
        </TabsContent>
      </Tabs>

      <DialogFooter>
        <Button variant="outline" onClick={() => onOpenChange(false)}>
          Close
        </Button>
      </DialogFooter>
    </DialogContent>
  );
}

// ---------------------------------------------------------------------------
// Main: email addresses and phone numbers
// ---------------------------------------------------------------------------

function AddressesTab({
  platformSlug,
  organisationId,
  user,
  onUserChange: adopt,
}: {
  platformSlug: string;
  organisationId: number;
  user: OrganisationUserResponse;
  onUserChange: (user: OrganisationUserResponse) => void;
}) {
  const addEmail = useAddOrgUserEmail(platformSlug, organisationId);
  const deleteEmail = useDeleteOrgUserEmail(platformSlug, organisationId);
  const setPrimaryEmail = useSetOrgUserPrimaryEmail(platformSlug, organisationId);
  const setEmailVerified = useSetOrgUserEmailVerified(platformSlug, organisationId);
  const addPhone = useAddOrgUserPhone(platformSlug, organisationId);
  const deletePhone = useDeleteOrgUserPhone(platformSlug, organisationId);
  const setPrimaryPhone = useSetOrgUserPrimaryPhone(platformSlug, organisationId);
  const setPhoneVerified = useSetOrgUserPhoneVerified(platformSlug, organisationId);
  const sendVerification = useSendOrgUserVerification(platformSlug, organisationId);

  const [removingEmail, setRemovingEmail] = useState<number | null>(null);
  const [removingPhone, setRemovingPhone] = useState<number | null>(null);

  const emailForm = useForm(addUserEmailSchema, { email: "" });
  const phoneForm = useForm(addUserPhoneSchema, { phone: "" });

  const apply = async (run: () => Promise<OrganisationUserResponse>) => {
    adopt(await run());
  };

  return (
    <div className="max-h-[60vh] space-y-4 overflow-y-auto pr-1">
      <section className="space-y-2">
        <p className="text-sm font-medium">Email addresses</p>
        {(user.emails ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground">No email address on this account yet.</p>
        ) : (
          (user.emails ?? []).map((email) => (
            <div
              key={email.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3"
            >
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                <Mail className="size-4 shrink-0 text-muted-foreground" />
                <span className="truncate text-sm">{email.email}</span>
                {email.isPrimary ? <Badge variant="secondary">Primary</Badge> : null}
                {email.verified ? (
                  <Badge variant="outline" className="border-emerald-500/40 text-emerald-600 dark:text-emerald-400">
                    <BadgeCheck className="size-3" /> Verified
                  </Badge>
                ) : (
                  <Badge variant="outline" className="text-muted-foreground">Unverified</Badge>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-1">
                {!email.verified ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={sendVerification.isPending}
                    onClick={() =>
                      sendVerification
                        .mutateAsync({
                          userId: user.id,
                          body: { channel: "EMAIL", emailId: email.id },
                        })
                        .catch(() => {})
                    }
                  >
                    <Send /> Send verification
                  </Button>
                ) : null}
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    apply(() =>
                      setEmailVerified.mutateAsync({
                        userId: user.id,
                        emailId: email.id,
                        verified: !email.verified,
                      }),
                    ).catch(() => {})
                  }
                >
                  {email.verified ? "Mark unverified" : "Mark verified"}
                </Button>
                {!email.isPrimary ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      apply(() =>
                        setPrimaryEmail.mutateAsync({ userId: user.id, emailId: email.id }),
                      ).catch(() => {})
                    }
                  >
                    <Star /> Make primary
                  </Button>
                ) : null}
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Remove ${email.email}`}
                  className="text-destructive hover:text-destructive"
                  onClick={() => setRemovingEmail(email.id)}
                >
                  <Trash2 />
                </Button>
              </div>
            </div>
          ))
        )}
        <AddAddressForm
          id="ou-add-email"
          label="Add an email address"
          placeholder="name@example.com"
          type="email"
          value={emailForm.values.email}
          error={emailForm.errors.email}
          submitError={emailForm.submitError ?? (addEmail.error ? getErrorMessage(addEmail.error) : null)}
          pending={addEmail.isPending}
          onChange={(value) => emailForm.setValue("email", value)}
          onSubmit={() =>
            emailForm.handleSubmit(async (data) => {
              adopt(
                await addEmail.mutateAsync({ userId: user.id, body: { email: data.email.trim() } }),
              );
              emailForm.setValue("email", "");
            })()
          }
        />
      </section>

      <Separator />

      <section className="space-y-2">
        <p className="text-sm font-medium">Phone numbers</p>
        {(user.phones ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground">No phone number on this account yet.</p>
        ) : (
          (user.phones ?? []).map((phone) => (
            <div
              key={phone.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3"
            >
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                <Phone className="size-4 shrink-0 text-muted-foreground" />
                <span className="truncate text-sm">{phone.phone}</span>
                {phone.isPrimary ? <Badge variant="secondary">Primary</Badge> : null}
                {phone.verified ? (
                  <Badge variant="outline" className="border-emerald-500/40 text-emerald-600 dark:text-emerald-400">
                    <BadgeCheck className="size-3" /> Verified
                  </Badge>
                ) : (
                  <Badge variant="outline" className="text-muted-foreground">Unverified</Badge>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-1">
                {!phone.verified ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={sendVerification.isPending}
                    onClick={() =>
                      sendVerification
                        .mutateAsync({
                          userId: user.id,
                          body: { channel: "SMS", phoneId: phone.id },
                        })
                        .catch(() => {})
                    }
                  >
                    <Send /> Send verification
                  </Button>
                ) : null}
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    apply(() =>
                      setPhoneVerified.mutateAsync({
                        userId: user.id,
                        phoneId: phone.id,
                        verified: !phone.verified,
                      }),
                    ).catch(() => {})
                  }
                >
                  {phone.verified ? "Mark unverified" : "Mark verified"}
                </Button>
                {!phone.isPrimary ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      apply(() =>
                        setPrimaryPhone.mutateAsync({ userId: user.id, phoneId: phone.id }),
                      ).catch(() => {})
                    }
                  >
                    <Star /> Make primary
                  </Button>
                ) : null}
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Remove ${phone.phone}`}
                  className="text-destructive hover:text-destructive"
                  onClick={() => setRemovingPhone(phone.id)}
                >
                  <Trash2 />
                </Button>
              </div>
            </div>
          ))
        )}
        <AddAddressForm
          id="ou-add-phone"
          label="Add a phone number"
          placeholder="+250788000000"
          type="tel"
          value={phoneForm.values.phone}
          error={phoneForm.errors.phone}
          submitError={phoneForm.submitError ?? (addPhone.error ? getErrorMessage(addPhone.error) : null)}
          pending={addPhone.isPending}
          onChange={(value) => phoneForm.setValue("phone", value)}
          onSubmit={() =>
            phoneForm.handleSubmit(async (data) => {
              adopt(
                await addPhone.mutateAsync({ userId: user.id, body: { phone: data.phone.trim() } }),
              );
              phoneForm.setValue("phone", "");
            })()
          }
        />
      </section>

      <ConfirmDialog
        open={removingEmail !== null}
        onOpenChange={(open) => !open && setRemovingEmail(null)}
        title="Remove email address"
        description="The address is detached from this user and any verification for it stops applying."
        confirmLabel="Remove email"
        pending={deleteEmail.isPending}
        onConfirm={async () => {
          if (removingEmail === null) return;
          await apply(() => deleteEmail.mutateAsync({ userId: user.id, emailId: removingEmail }));
          setRemovingEmail(null);
        }}
      />
      <ConfirmDialog
        open={removingPhone !== null}
        onOpenChange={(open) => !open && setRemovingPhone(null)}
        title="Remove phone number"
        description="The number is detached from this user and any verification for it stops applying."
        confirmLabel="Remove phone"
        pending={deletePhone.isPending}
        onConfirm={async () => {
          if (removingPhone === null) return;
          await apply(() => deletePhone.mutateAsync({ userId: user.id, phoneId: removingPhone }));
          setRemovingPhone(null);
        }}
      />
    </div>
  );
}

/** One-line add form for a new address. Presentational: the caller owns the
 * typed form state so each address kind keeps its own schema. */
function AddAddressForm({
  id,
  label,
  placeholder,
  type,
  value,
  error,
  submitError,
  pending,
  onChange,
  onSubmit,
}: {
  id: string;
  label: string;
  placeholder: string;
  type: "email" | "tel";
  value: string;
  error?: string;
  submitError: string | null;
  pending: boolean;
  onChange: (value: string) => void;
  onSubmit: () => void;
}) {
  return (
    <div className="flex items-end gap-2">
      <div className="flex-1">
        <FormField label={label} htmlFor={id} error={error}>
          <Input
            id={id}
            type={type}
            placeholder={placeholder}
            value={value}
            onChange={(e) => onChange(e.target.value)}
          />
        </FormField>
      </div>
      <Button type="button" variant="outline" disabled={pending} className="gap-2" onClick={onSubmit}>
        {pending ? <Loader2 className="animate-spin" /> : <Plus />}
        Add
      </Button>
      {submitError ? <p className="w-full text-xs text-destructive">{submitError}</p> : null}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

function ProfileTab({
  user,
  fields,
  useEmailAsUsername,
  onSave,
  pending,
}: {
  user: OrganisationUserResponse;
  fields: OrganisationUserFieldResponse[];
  useEmailAsUsername: boolean;
  onSave: (body: Parameters<ReturnType<typeof useUpdateOrgUser>["mutateAsync"]>[0]["body"]) => Promise<void>;
  pending: boolean;
}) {
  const [metadata, setMetadata] = useState<Record<string, string>>(user.metadata ?? {});

  const form = useForm(orgUserProfileSchema, {
    firstName: user.firstName,
    lastName: user.lastName ?? "",
    username: user.username ?? "",
  });

  const submit = async () => {
    const data = form.values;
    await onSave({
      firstName: data.firstName.trim(),
      lastName: data.lastName.trim(),
      // "" clears the username; omitted leaves it alone.
      username: data.username.trim(),
      metadata,
    });
  };

  return (
    <form className="max-h-[60vh] space-y-4 overflow-y-auto pr-1" onSubmit={form.handleSubmit(() => submit())}>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="First name" htmlFor="ous-firstName" error={form.errors.firstName}>
          <Input
            id="ous-firstName"
            value={form.values.firstName}
            onChange={(e) => form.setValue("firstName", e.target.value)}
          />
        </FormField>
        <FormField label="Last name" htmlFor="ous-lastName" error={form.errors.lastName} hint="Optional">
          <Input
            id="ous-lastName"
            value={form.values.lastName}
            onChange={(e) => form.setValue("lastName", e.target.value)}
          />
        </FormField>
      </div>

      <FormField
        label="Username"
        htmlFor="ous-username"
        error={form.errors.username}
        hint={
          useEmailAsUsername
            ? "This organisation signs users in with their email, so the username is informational."
            : "Used to sign in. Leave blank to clear it."
        }
      >
        <Input
          id="ous-username"
          value={form.values.username}
          onChange={(e) => form.setValue("username", e.target.value)}
        />
      </FormField>

      {fields.length > 0 ? (
        <div className="space-y-4 rounded-lg border p-3">
          <p className="text-sm font-medium">User fields</p>
          {fields.map((field) => (
            <Input
              key={field.id}
              aria-label={field.key}
              placeholder={field.fieldType === "DATE" ? "yyyy-MM-dd" : field.key}
              type={field.fieldType === "DATE" ? "date" : "text"}
              value={metadata[field.key] ?? ""}
              onChange={(e) => setMetadata((prev) => ({ ...prev, [field.key]: e.target.value }))}
            />
          ))}
          <p className="text-xs text-muted-foreground">Leave a field blank to remove its value.</p>
        </div>
      ) : null}

      {form.submitError ? (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{form.submitError}</p>
      ) : null}

      <div className="flex justify-end">
        <Button type="submit" disabled={pending} className="gap-2">
          {pending ? <Loader2 className="animate-spin" /> : null}
          Save profile
        </Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------------------
// Security: credentials, login method, and account state
// ---------------------------------------------------------------------------

function SecurityTab({
  platformSlug,
  organisationId,
  user,
  onUserChange: adopt,
}: {
  platformSlug: string;
  organisationId: number;
  user: OrganisationUserResponse;
  onUserChange: (user: OrganisationUserResponse) => void;
}) {
  const update = useUpdateOrgUser(platformSlug, organisationId);
  const sendReset = useSendOrgUserPasswordReset(platformSlug, organisationId);
  const removeUser = useDeleteOrgUser(platformSlug, organisationId);

  const [temporaryPassword, setTemporaryPassword] = useState(false);
  const [confirmRemovePassword, setConfirmRemovePassword] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const hasPassword = (user.authTypes ?? []).includes("PASSWORD");
  const hasAddress = (user.emails ?? []).length > 0 || (user.phones ?? []).length > 0;

  const passwordForm = useForm(orgUserPasswordSchema, { password: "" });

  const save = async (body: Parameters<typeof update.mutateAsync>[0]["body"]) => {
    adopt(await update.mutateAsync({ userId: user.id, body }));
  };

  return (
    <div className="max-h-[60vh] space-y-4 overflow-y-auto pr-1">
      <div className="space-y-2 rounded-lg border p-3">
        <div className="flex items-center gap-2">
          <ShieldCheck className="size-4 text-muted-foreground" />
          <p className="text-sm font-medium">How this account signs in</p>
        </div>
        <FormField
          label="Login method"
          htmlFor="ous-loginMethod"
          hint={USER_LOGIN_METHOD_META[user.loginMethod ?? "PASSWORD_OR_OTP"].description}
        >
          <Select
            value={user.loginMethod ?? "PASSWORD_OR_OTP"}
            onValueChange={(value) => save({ loginMethod: value as UserLoginMethod })}
          >
            <SelectTrigger id="ous-loginMethod" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(Object.keys(USER_LOGIN_METHOD_META) as UserLoginMethod[]).map((method) => (
                <SelectItem key={method} value={method}>
                  {USER_LOGIN_METHOD_META[method].label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </FormField>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground">Usable now:</span>
          {(user.authTypes ?? []).length === 0 ? (
            <Badge variant="destructive">No way to sign in</Badge>
          ) : (
            user.authTypes.map((type) => (
              <Badge key={type} variant="secondary">
                {type === "PASSWORD" ? "Password" : "One-time code"}
              </Badge>
            ))
          )}
        </div>
        {(user.authTypes ?? []).length === 0 ? (
          <Alert variant="destructive">
            <AlertTitle>This account cannot sign in</AlertTitle>
            <AlertDescription>
              Set a password below, or switch the login method to a one-time code and add an email or phone
              number on the Main tab.
            </AlertDescription>
          </Alert>
        ) : null}
      </div>

      <div className="space-y-3 rounded-lg border p-3">
        <div className="flex items-center gap-2">
          <KeyRound className="size-4 text-muted-foreground" />
          <p className="text-sm font-medium">Password</p>
        </div>
        <form
          className="space-y-3"
          onSubmit={passwordForm.handleSubmit(async () => {
            await save({
              password: passwordForm.values.password,
              temporaryPassword,
            });
            passwordForm.setValue("password", "");
            setTemporaryPassword(false);
          })}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              label={hasPassword ? "Replace password" : "Set a password"}
              htmlFor="ous-password"
              error={passwordForm.errors.password}
            >
              <Input
                id="ous-password"
                type="password"
                autoComplete="new-password"
                value={passwordForm.values.password}
                onChange={(e) => passwordForm.setValue("password", e.target.value)}
              />
            </FormField>
            <div className="flex h-full items-end pb-1">
              <label className="flex items-center gap-2 text-sm">
                <Checkbox
                  checked={temporaryPassword}
                  onCheckedChange={(checked) => setTemporaryPassword(!!checked)}
                />
                Must change at next sign-in
              </label>
            </div>
          </div>
          {passwordForm.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {passwordForm.submitError}
            </p>
          ) : null}
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="submit" disabled={update.isPending} className="gap-2">
              {update.isPending ? <Loader2 className="animate-spin" /> : null}
              {hasPassword ? "Replace password" : "Set password"}
            </Button>
            {hasPassword ? (
              <Button
                type="button"
                variant="outline"
                onClick={() => setConfirmRemovePassword(true)}
              >
                Remove password
              </Button>
            ) : null}
          </div>
        </form>

        <Separator />

        <div className="space-y-2">
          <p className="text-sm">Prefer the user to choose their own password?</p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="gap-2"
            disabled={sendReset.isPending || !hasAddress}
            onClick={() => sendReset.mutateAsync({ userId: user.id }).catch(() => {})}
          >
            {sendReset.isPending ? <Loader2 className="animate-spin" /> : <Send />}
            Send password reset
          </Button>
          <p className="text-xs text-muted-foreground">
            Sends a code or link to the user&apos;s own address. You never see or choose the new password.
            {!hasAddress ? " Add an email or phone number first." : ""}
          </p>
        </div>
      </div>

      <div className="space-y-3 rounded-lg border p-3">
        <p className="text-sm font-medium">Next sign-in challenges</p>
        <ToggleRow
          id="ous-verify-email"
          label="Require email verification"
          description="Their next password sign-in pauses until the email is proven with a code."
          checked={!!user.requireEmailVerificationAtNextLogin}
          onChange={(checked) => save({ requireEmailVerificationAtNextLogin: checked })}
        />
        <ToggleRow
          id="ous-verify-phone"
          label="Require phone verification"
          description="Their next password sign-in pauses until the phone number is proven with a code."
          checked={!!user.requirePhoneVerificationAtNextLogin}
          onChange={(checked) => save({ requirePhoneVerificationAtNextLogin: checked })}
        />
        <ToggleRow
          id="ous-enabled"
          label="Account enabled"
          description="Disabling blocks sign-in immediately and stops existing sessions working."
          checked={user.enabled}
          onChange={(checked) => save({ enabled: checked })}
        />
      </div>

      <div className="space-y-2 rounded-lg border border-destructive/40 p-3">
        <p className="text-sm font-medium text-destructive">Remove this user</p>
        <p className="text-xs text-muted-foreground">
          Deletes the account from this organisation. This cannot be undone.
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="gap-2 text-destructive hover:text-destructive"
          onClick={() => setConfirmDelete(true)}
        >
          <Trash2 /> Delete user
        </Button>
      </div>

      <ConfirmDialog
        open={confirmRemovePassword}
        onOpenChange={setConfirmRemovePassword}
        title="Remove the password"
        description={
          hasAddress
            ? "The user keeps access through one-time codes sent to their email or phone, so they are not locked out."
            : "This account has no email or phone number, so removing the password leaves the user with no way to sign in."
        }
        confirmLabel="Remove password"
        pending={update.isPending}
        onConfirm={async () => {
          await save({ password: "" });
          setConfirmRemovePassword(false);
        }}
      />
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete this user"
        description={`${user.firstName} ${user.lastName ?? ""}`.trim() + " will be removed from this organisation."}
        confirmLabel="Delete user"
        pending={removeUser.isPending}
        onConfirm={async () => {
          await removeUser.mutateAsync(user.id);
          setConfirmDelete(false);
        }}
      />
    </div>
  );
}

function ToggleRow({
  id,
  label,
  description,
  checked,
  onChange,
}: {
  id: string;
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-4">
      <div>
        <Label htmlFor={id} className="text-sm font-medium">
          {label}
        </Label>
        <p className="text-xs text-muted-foreground">{description}</p>
      </div>
      <Switch id={id} checked={checked} onCheckedChange={onChange} />
    </div>
  );
}
