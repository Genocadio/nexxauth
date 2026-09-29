"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Bell,
  Check,
  Copy,
  Eye,
  KeyRound,
  Loader2,
  Mail,
  MessageSquare,
  Pencil,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Timer,
  Trash2,
  X,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { ErrorState } from "@/components/shared/error-state";
import { FormField } from "@/components/shared/form-field";
import { TableSkeleton } from "@/components/shared/loading";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import {
  useDeleteOrganisation,
  useUpdateOrgAuthConfig,
  useUpdateOrganisation,
  useUpdateOrgNotificationTemplates,
  useUpdateOrgSessionSettings,
} from "@/hooks/mutations";
import {
  useOrganisation,
  useOrgAuthConfig,
  useOrgNotificationTemplates,
  useOrgSessionSettings,
} from "@/hooks/queries";
import { useForm } from "@/hooks/use-form";
import { authConfigSchema, sessionSettingsSchema } from "@/lib/validation";
import { z } from "zod";
import { DURATION_UNITS, decomposeDuration, formatDuration, toSeconds, type DurationUnit } from "@/lib/constants";
import { toast } from "sonner";

interface OrgSettingsTabProps {
  platformSlug: string;
  organisationId: number;
}

export function OrgSettingsTab({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const [section, setSection] = useState("auth");

  return (
    <Tabs value={section} onValueChange={setSection}>
      <TabsList>
        <TabsTrigger value="auth" className="gap-2">
          <KeyRound className="h-4 w-4" /> Auth & Security
        </TabsTrigger>
        <TabsTrigger value="notifications" className="gap-2">
          <Bell className="h-4 w-4" /> Notifications & Templates
        </TabsTrigger>
        <TabsTrigger value="session" className="gap-2">
          <Timer className="h-4 w-4" /> Session
        </TabsTrigger>
        <TabsTrigger value="misc" className="gap-2">
          <Trash2 className="h-4 w-4" /> Miscellaneous
        </TabsTrigger>
      </TabsList>

      <TabsContent value="auth" className="mt-6 space-y-6">
        <IdentifiersCard platformSlug={platformSlug} organisationId={organisationId} />
        <VerificationFeaturesCard
          platformSlug={platformSlug}
          organisationId={organisationId}
          onNavigateToNotifications={() => setSection("notifications")}
        />
        <AuthConfigCard platformSlug={platformSlug} organisationId={organisationId} />
      </TabsContent>

      <TabsContent value="notifications" className="mt-6 space-y-6">
        <NotificationTemplatesCard platformSlug={platformSlug} organisationId={organisationId} />
      </TabsContent>

      <TabsContent value="session" className="mt-6">
        <SessionSettingsCard platformSlug={platformSlug} organisationId={organisationId} />
      </TabsContent>

      <TabsContent value="misc" className="mt-6">
        <DangerZoneCard platformSlug={platformSlug} organisationId={organisationId} />
      </TabsContent>
    </Tabs>
  );
}

// ---------------------------------------------------------------------------
// Auth: Identifiers
// ---------------------------------------------------------------------------

function IdentifiersCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const org = useOrganisation(organisationId);
  const update = useUpdateOrganisation(platformSlug, organisationId);

  if (org.isLoading) return <TableSkeleton rows={3} columns={3} />;
  if (org.isError || !org.data)
    return <ErrorState error={org.error ?? new Error("Organisation not found")} onRetry={() => org.refetch()} />;

  const data = org.data;

  const setFlag = (
    key: "emailRequired" | "usernameRequired" | "phoneRequired" | "emailCanLogin" | "usernameCanLogin" | "phoneCanLogin",
    value: boolean
  ) => {
    update
      .mutateAsync({
        emailRequired: key === "emailRequired" ? value : data.emailRequired,
        usernameRequired: key === "usernameRequired" ? value : data.usernameRequired,
        phoneRequired: key === "phoneRequired" ? value : data.phoneRequired,
        emailCanLogin: key === "emailCanLogin" ? value : data.emailCanLogin,
        usernameCanLogin: key === "usernameCanLogin" ? value : data.usernameCanLogin,
        phoneCanLogin: key === "phoneCanLogin" ? value : data.phoneCanLogin,
      })
      .catch(() => undefined);
  };

  const identifiers: { key: "email" | "username" | "phone"; label: string; hint: string }[] = [
    { key: "email", label: "Email", hint: "Normalized and unique per organisation" },
    { key: "username", label: "Username", hint: "Lowercased and unique per organisation" },
    { key: "phone", label: "Phone number", hint: "Normalized and unique per organisation" },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Mail className="h-4 w-4 text-primary" /> Sign-in identifiers
        </CardTitle>
        <CardDescription>
          Which identifiers your users sign in with. Each can be required on new users and/or
          usable for login — at least one must be usable for login.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {identifiers.map(({ key, label, hint }) => (
          <div key={key} className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div>
              <Label className="text-sm font-medium">{label}</Label>
              <p className="text-xs text-muted-foreground">{hint}</p>
            </div>
            <div className="flex items-center gap-4">
              <label className="flex items-center gap-2 text-sm">
                <Switch
                  checked={data[`${key}Required`]}
                  disabled={update.isPending}
                  onCheckedChange={(checked) => setFlag(`${key}Required` as const, checked)}
                />
                Required
              </label>
              <label className="flex items-center gap-2 text-sm">
                <Switch
                  checked={data[`${key}CanLogin`]}
                  disabled={update.isPending}
                  onCheckedChange={(checked) => setFlag(`${key}CanLogin` as const, checked)}
                />
                Can log in
              </label>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Auth: Verification & Multi-Factor Features
// ---------------------------------------------------------------------------

interface VerificationFeaturesCardProps extends OrgSettingsTabProps {
  onNavigateToNotifications: () => void;
}

function VerificationFeaturesCard({
  platformSlug,
  organisationId,
  onNavigateToNotifications,
}: VerificationFeaturesCardProps) {
  const config = useOrgAuthConfig(organisationId);
  const update = useUpdateOrgAuthConfig(platformSlug, organisationId);

  if (config.isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (config.isError || !config.data)
    return <ErrorState error={config.error ?? new Error("Auth config not found")} onRetry={() => config.refetch()} />;

  const data = config.data;
  const locked = !data.verificationServiceAvailable;

  const toggleFlag = (key: string, value: unknown) => {
    update.mutateAsync({ [key]: value }).catch(() => undefined);
  };

  const hasActiveNotifications =
    data.emailVerificationEnabled ||
    data.phoneVerificationEnabled ||
    data.passwordResetEnabled ||
    data.otpLoginEnabled ||
    data.twoFactorEnabled ||
    data.requireEmailVerificationOnRegister ||
    data.requirePhoneVerificationOnRegister;

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <ShieldCheck className="h-4 w-4 text-primary" /> Verification & Security Features
            </CardTitle>
            <CardDescription>
              Control email/SMS verification, one-time passwords (OTP), password resets, and 2FA for this organisation.
            </CardDescription>
          </div>
          {data.verificationServiceAvailable ? (
            <Badge variant="outline" className="border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              Notification Service Active
            </Badge>
          ) : (
            <Badge variant="outline" className="border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-400">
              Notifier Not Configured
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {locked ? (
          <div className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm">
            <p className="font-medium text-amber-600 dark:text-amber-400">
              Verification features are currently unavailable
            </p>
            <p className="text-xs text-muted-foreground">
              Configure <code className="font-mono text-xs">NEXXNOTIFY_URL</code> on the server to deliver verification OTPs and magic links.
            </p>
          </div>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Email Verification */}
          <div className="flex items-start justify-between gap-4 rounded-lg border p-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium flex items-center gap-1.5">
                <Mail className="h-3.5 w-3.5 text-muted-foreground" /> Email Verification
              </Label>
              <p className="text-xs text-muted-foreground">
                Allows users to verify their email address via OTP or link.
              </p>
            </div>
            <Switch
              checked={data.emailVerificationEnabled}
              disabled={locked || update.isPending}
              onCheckedChange={(v) => toggleFlag("emailVerificationEnabled", v)}
            />
          </div>

          {/* Require Email on Register */}
          <div className="flex items-start justify-between gap-4 rounded-lg border p-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium">Verify Email on Register</Label>
              <p className="text-xs text-muted-foreground">
                Requires newly registered users to verify email before accessing the platform.
              </p>
            </div>
            <Switch
              checked={data.requireEmailVerificationOnRegister}
              disabled={locked || update.isPending}
              onCheckedChange={(v) => toggleFlag("requireEmailVerificationOnRegister", v)}
            />
          </div>

          {/* Phone Verification */}
          <div className="flex items-start justify-between gap-4 rounded-lg border p-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium flex items-center gap-1.5">
                <Smartphone className="h-3.5 w-3.5 text-muted-foreground" /> Phone Verification
              </Label>
              <p className="text-xs text-muted-foreground">
                Allows users to verify their mobile phone number via SMS OTP.
              </p>
            </div>
            <Switch
              checked={data.phoneVerificationEnabled}
              disabled={locked || update.isPending}
              onCheckedChange={(v) => toggleFlag("phoneVerificationEnabled", v)}
            />
          </div>

          {/* Require Phone on Register */}
          <div className="flex items-start justify-between gap-4 rounded-lg border p-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium">Verify Phone on Register</Label>
              <p className="text-xs text-muted-foreground">
                Requires newly registered users to verify phone number via SMS code.
              </p>
            </div>
            <Switch
              checked={data.requirePhoneVerificationOnRegister}
              disabled={locked || update.isPending}
              onCheckedChange={(v) => toggleFlag("requirePhoneVerificationOnRegister", v)}
            />
          </div>

          {/* Password Reset */}
          <div className="flex items-start justify-between gap-4 rounded-lg border p-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium flex items-center gap-1.5">
                <KeyRound className="h-3.5 w-3.5 text-muted-foreground" /> Password Reset
              </Label>
              <p className="text-xs text-muted-foreground">
                Allows users to self-serve reset forgotten passwords using OTP/link delivery.
              </p>
            </div>
            <Switch
              checked={data.passwordResetEnabled}
              disabled={locked || update.isPending}
              onCheckedChange={(v) => toggleFlag("passwordResetEnabled", v)}
            />
          </div>

          {/* OTP Login */}
          <div className="flex items-start justify-between gap-4 rounded-lg border p-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium flex items-center gap-1.5">
                <MessageSquare className="h-3.5 w-3.5 text-muted-foreground" /> OTP Login
              </Label>
              <p className="text-xs text-muted-foreground">
                Allows passwordless sign-in using one-time verification codes sent over email/SMS.
              </p>
            </div>
            <Switch
              checked={data.otpLoginEnabled}
              disabled={locked || update.isPending}
              onCheckedChange={(v) => toggleFlag("otpLoginEnabled", v)}
            />
          </div>

          {/* Two-Factor Auth (2FA) */}
          <div className="flex items-start justify-between gap-4 rounded-lg border p-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium flex items-center gap-1.5">
                <ShieldCheck className="h-3.5 w-3.5 text-muted-foreground" /> Two-Factor Authentication (2FA)
              </Label>
              <p className="text-xs text-muted-foreground">
                Enforces a secondary OTP confirmation challenge on every password login.
              </p>
            </div>
            <Switch
              checked={data.twoFactorEnabled}
              disabled={locked || update.isPending}
              onCheckedChange={(v) => toggleFlag("twoFactorEnabled", v)}
            />
          </div>

          {/* Default Delivery Mode */}
          <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div className="space-y-1">
              <Label className="text-sm font-medium">Default Verification Mode</Label>
              <p className="text-xs text-muted-foreground">
                Deliver OTP code to type, or clickable magic link.
              </p>
            </div>
            <Select
              value={data.verificationMode || "OTP"}
              disabled={locked || update.isPending}
              onValueChange={(val) => toggleFlag("verificationMode", val)}
            >
              <SelectTrigger className="w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="OTP">Numeric OTP</SelectItem>
                <SelectItem value="LINK">Magic Link</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        {hasActiveNotifications && (
          <div className="flex items-center justify-between rounded-lg border border-primary/20 bg-primary/5 p-4">
            <div className="flex items-center gap-3">
              <div className="rounded-full bg-primary/10 p-2 text-primary">
                <Bell className="h-5 w-5" />
              </div>
              <div>
                <p className="text-sm font-medium text-foreground">Customise Notification Templates</p>
                <p className="text-xs text-muted-foreground">
                  You have active verification workflows. You can preview and customize the email &amp; SMS messages sent to your users.
                </p>
              </div>
            </div>
            <Button variant="outline" size="sm" onClick={onNavigateToNotifications} className="gap-1.5 shrink-0">
              <Eye className="h-3.5 w-3.5" /> Manage Templates
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Notification Settings & Live Preview Template Editor
// ---------------------------------------------------------------------------

function NotificationTemplatesCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const org = useOrganisation(organisationId);
  const templates = useOrgNotificationTemplates(organisationId);
  const update = useUpdateOrgNotificationTemplates(platformSlug, organisationId);

  const orgName = org.data?.name || "Organisation";

  const [isEditing, setIsEditing] = useState(false);
  const [emailSubject, setEmailSubject] = useState("");
  const [emailBody, setEmailBody] = useState("");
  const [emailHtml, setEmailHtml] = useState("");
  const [smsBody, setSmsBody] = useState("");
  const [copiedTag, setCopiedTag] = useState<string | null>(null);
  const [channelTab, setChannelTab] = useState("email");

  const hydrate = (data: typeof templates.data) => {
    setEmailSubject(data?.email.subject || "");
    setEmailBody(data?.email.body || "");
    setEmailHtml(data?.email.html || "");
    setSmsBody(data?.sms.body || "");
  };

  /* Hydrate state from fetched templates. Adjusting state during render (React's
     "derive state from props" pattern) rather than in an effect: the effect
     version rendered once with empty inputs, then again with server values,
     which react-hooks/set-state-in-effect flags as a cascading render. */
  const [hydratedFrom, setHydratedFrom] = useState(templates.data);
  if (templates.data !== hydratedFrom) {
    setHydratedFrom(templates.data);
    hydrate(templates.data);
  }

  const defaultTemplates = useMemo(() => {
    const defaultSubject = `Your ${orgName} verification code`;
    const defaultBody = `Your ${orgName} verification code is {{code}}. This code will expire shortly. If you did not request this, please ignore this email.`;
    const defaultHtml = `<div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 32px 24px; background-color: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0;">
  <h2 style="color: #0f172a; margin-top: 0; font-size: 20px; font-weight: 600;">${orgName} Verification</h2>
  <p style="color: #475569; font-size: 15px; line-height: 1.5; margin-bottom: 24px;">Use the verification code below to complete your sign-in or verification request:</p>
  <div style="background-color: #f1f5f9; border-radius: 6px; padding: 16px 24px; text-align: center; margin: 24px 0;">
    <span style="font-family: monospace; font-size: 32px; font-weight: 700; letter-spacing: 6px; color: #0f172a;">{{code}}</span>
  </div>
  <p style="color: #64748b; font-size: 13px; line-height: 1.5;">This code will expire in 10 minutes. If you did not make this request, you can safely ignore this email.</p>
</div>`;
    const defaultSms = `Your ${orgName} verification code is: {{code}}. Valid for 10 minutes.`;

    return {
      subject: defaultSubject,
      body: defaultBody,
      html: defaultHtml,
      sms: defaultSms,
    };
  }, [orgName]);

  const handleResetToDefault = () => {
    setEmailSubject(defaultTemplates.subject);
    setEmailBody(defaultTemplates.body);
    setEmailHtml(defaultTemplates.html);
    setSmsBody(defaultTemplates.sms);
    toast.info("Reset to default template. Click 'Save changes' to apply.");
  };

  const handleCancel = () => {
    hydrate(templates.data);
    setIsEditing(false);
  };

  const copyTag = (tag: string) => {
    navigator.clipboard.writeText(tag);
    setCopiedTag(tag);
    toast.success(`Copied ${tag} to clipboard`);
    setTimeout(() => setCopiedTag(null), 2000);
  };

  const handleSave = async () => {
    try {
      await update.mutateAsync({
        emailSubject: emailSubject.trim() || undefined,
        emailBody: emailBody.trim() || undefined,
        emailHtml: emailHtml.trim() || undefined,
        smsBody: smsBody.trim() || undefined,
      });
      setIsEditing(false);
    } catch {
      // update mutation handles toast
    }
  };

  // Render mock preview strings by substituting sample values
  const previewSubject = (emailSubject || defaultTemplates.subject)
    .replaceAll("{{code}}", "849201")
    .replaceAll("{{organisationName}}", orgName)
    .replaceAll("{{link}}", "https://auth.med.rw/verify?token=sample_token");

  const previewHtml = (emailHtml || defaultTemplates.html)
    .replaceAll("{{code}}", "849201")
    .replaceAll("{{organisationName}}", orgName)
    .replaceAll("{{link}}", "https://auth.med.rw/verify?token=sample_token");

  const previewSms = (smsBody || defaultTemplates.sms)
    .replaceAll("{{code}}", "849201")
    .replaceAll("{{organisationName}}", orgName)
    .replaceAll("{{link}}", "https://auth.med.rw/verify?token=sample_token");

  if (templates.isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (templates.isError)
    return <ErrorState error={templates.error} onRetry={() => templates.refetch()} />;

  const flowId = templates.data?.flowId || `org_${organisationId}_auth`;

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <Bell className="h-4 w-4 text-primary" />
              {isEditing ? "Edit Notification Templates" : "Notification Settings & Templates"}
            </CardTitle>
            <CardDescription>
              {isEditing
                ? "Customize email and SMS templates. You can use dynamic placeholder variables."
                : "Preview email & SMS verification notices sent to your organisation's users."}
            </CardDescription>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="outline" className="font-mono text-xs">
              Flow: {flowId}
            </Badge>
            {isEditing ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleResetToDefault}
                className="gap-1 text-xs"
              >
                <RefreshCw className="h-3 w-3" /> Reset default
              </Button>
            ) : (
              <Button
                type="button"
                size="sm"
                onClick={() => setIsEditing(true)}
                className="gap-1.5 text-xs"
              >
                <Pencil className="h-3.5 w-3.5" /> Edit Template
              </Button>
            )}
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Placeholder tags toolbar (shown in edit mode) */}
        {isEditing && (
          <div className="rounded-lg border bg-muted/40 p-3 space-y-2">
            <p className="text-xs font-medium text-foreground">Available Placeholder Variables (Click to copy):</p>
            <div className="flex flex-wrap gap-2">
              {["{{code}}", "{{link}}", "{{organisationName}}"].map((tag) => (
                <Badge
                  key={tag}
                  variant="secondary"
                  onClick={() => copyTag(tag)}
                  className="cursor-pointer font-mono text-xs transition-colors hover:bg-primary/20 hover:text-primary gap-1"
                >
                  {copiedTag === tag ? <Check className="h-3 w-3 text-emerald-500" /> : <Copy className="h-3 w-3" />}
                  {tag}
                </Badge>
              ))}
            </div>
          </div>
        )}

        {/* Channel tabs: Email vs SMS */}
        <Tabs value={channelTab} onValueChange={setChannelTab}>
          <TabsList className="grid w-full grid-cols-2 max-w-sm">
            <TabsTrigger value="email" className="gap-2">
              <Mail className="h-4 w-4" /> Email Template
            </TabsTrigger>
            <TabsTrigger value="sms" className="gap-2">
              <Smartphone className="h-4 w-4" /> SMS Template
            </TabsTrigger>
          </TabsList>

          {/* Email Tab */}
          <TabsContent value="email" className="mt-4 space-y-6">
            {isEditing ? (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Form Editor */}
                <div className="space-y-4">
                  <FormField
                    label="Email Subject"
                    htmlFor="email-subject"
                    hint="Supports {{code}} and {{organisationName}}"
                  >
                    <Input
                      id="email-subject"
                      value={emailSubject}
                      onChange={(e) => setEmailSubject(e.target.value)}
                      placeholder={defaultTemplates.subject}
                    />
                  </FormField>

                  <FormField
                    label="Email HTML Body"
                    htmlFor="email-html"
                    hint="Custom HTML template with embedded styling"
                  >
                    <Textarea
                      id="email-html"
                      rows={8}
                      value={emailHtml}
                      onChange={(e) => setEmailHtml(e.target.value)}
                      placeholder={defaultTemplates.html}
                      className="font-mono text-xs"
                    />
                  </FormField>

                  <FormField
                    label="Email Plain-Text Fallback"
                    htmlFor="email-body"
                    hint="Displayed by non-HTML mail clients"
                  >
                    <Textarea
                      id="email-body"
                      rows={3}
                      value={emailBody}
                      onChange={(e) => setEmailBody(e.target.value)}
                      placeholder={defaultTemplates.body}
                    />
                  </FormField>
                </div>

                {/* Live Email Preview */}
                <div className="space-y-2">
                  <Label className="text-xs font-medium text-muted-foreground flex items-center gap-1.5">
                    <Eye className="h-3.5 w-3.5" /> Live Email Preview
                  </Label>
                  <div className="rounded-lg border bg-background shadow-xs overflow-hidden">
                    {/* Mock Email Client Header */}
                    <div className="border-b bg-muted/30 p-3 space-y-1.5 text-xs">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-muted-foreground">From:</span>
                        <span className="text-foreground">{orgName} &lt;noreply@med.rw&gt;</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-muted-foreground">Subject:</span>
                        <span className="font-medium text-foreground">{previewSubject}</span>
                      </div>
                    </div>
                    {/* Mock Email Body */}
                    <div className="p-4 min-h-[220px] bg-slate-50/50 dark:bg-slate-950/50">
                      <div
                        className="text-sm max-w-full overflow-auto"
                        dangerouslySetInnerHTML={{ __html: previewHtml }}
                      />
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              /* View Mode Preview */
              <div className="space-y-4">
                <div className="rounded-lg border bg-background shadow-xs overflow-hidden">
                  <div className="border-b bg-muted/30 p-3.5 space-y-1.5 text-sm">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-muted-foreground w-16 text-xs">From:</span>
                      <span className="text-foreground text-xs">{orgName} &lt;noreply@med.rw&gt;</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-muted-foreground w-16 text-xs">Subject:</span>
                      <span className="font-semibold text-foreground text-xs">{previewSubject}</span>
                    </div>
                  </div>
                  <div className="p-6 bg-slate-50/50 dark:bg-slate-950/50 min-h-[240px]">
                    <div
                      className="text-sm max-w-full overflow-auto"
                      dangerouslySetInnerHTML={{ __html: previewHtml }}
                    />
                  </div>
                </div>

                {emailBody && (
                  <div className="rounded-lg border p-3 bg-muted/20">
                    <Label className="text-xs font-medium text-muted-foreground">Plain-Text Fallback:</Label>
                    <p className="mt-1 text-xs text-foreground font-mono whitespace-pre-wrap">{emailBody}</p>
                  </div>
                )}
              </div>
            )}
          </TabsContent>

          {/* SMS Tab */}
          <TabsContent value="sms" className="mt-4 space-y-6">
            {isEditing ? (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* SMS Editor */}
                <div className="space-y-4">
                  <FormField
                    label="SMS Message Body"
                    htmlFor="sms-body"
                    hint="Keep concise (160 characters per standard SMS part)"
                  >
                    <Textarea
                      id="sms-body"
                      rows={4}
                      value={smsBody}
                      onChange={(e) => setSmsBody(e.target.value)}
                      placeholder={defaultTemplates.sms}
                    />
                  </FormField>

                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>
                      Length: {smsBody.length} characters (
                      {Math.max(1, Math.ceil((smsBody.length || 1) / 160))} SMS segment
                      {Math.ceil((smsBody.length || 1) / 160) > 1 ? "s" : ""})
                    </span>
                  </div>
                </div>

                {/* Live SMS Chat Preview */}
                <div className="space-y-2">
                  <Label className="text-xs font-medium text-muted-foreground flex items-center gap-1.5">
                    <Eye className="h-3.5 w-3.5" /> Mobile SMS Preview
                  </Label>
                  <div className="mx-auto max-w-[280px] rounded-2xl border-2 border-muted bg-muted/20 p-3 shadow-sm">
                    <div className="mx-auto mb-3 h-1 w-12 rounded-full bg-muted-foreground/30" />
                    <div className="rounded-xl bg-background p-3 min-h-[160px] flex flex-col justify-end">
                      <div className="self-end rounded-2xl rounded-br-xs bg-primary px-3 py-2 text-xs text-primary-foreground shadow-xs max-w-[90%]">
                        <p className="whitespace-pre-wrap">{previewSms}</p>
                        <span className="mt-1 block text-[10px] text-primary-foreground/70 text-right">
                          Just now
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              /* SMS View Mode Preview */
              <div className="space-y-4">
                <div className="flex flex-col md:flex-row gap-6 items-center justify-center p-6 border rounded-lg bg-muted/10">
                  <div className="w-full max-w-[300px] rounded-2xl border-2 border-muted bg-muted/20 p-3 shadow-sm">
                    <div className="mx-auto mb-3 h-1 w-12 rounded-full bg-muted-foreground/30" />
                    <div className="rounded-xl bg-background p-3 min-h-[160px] flex flex-col justify-end">
                      <div className="self-end rounded-2xl rounded-br-xs bg-primary px-3 py-2 text-xs text-primary-foreground shadow-xs max-w-[90%]">
                        <p className="whitespace-pre-wrap">{previewSms}</p>
                        <span className="mt-1 block text-[10px] text-primary-foreground/70 text-right">
                          Just now
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="space-y-2 max-w-sm text-sm">
                    <p className="font-medium text-foreground">SMS Notification Preview</p>
                    <p className="text-xs text-muted-foreground">
                      This is the actual SMS copy delivered when sending one-time verification codes or password reset notices to mobile numbers.
                    </p>
                    <div className="pt-2 text-xs text-muted-foreground">
                      <span>Length: {smsBody.length || defaultTemplates.sms.length} characters</span>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </TabsContent>
        </Tabs>

        {/* Action Buttons (Edit mode) */}
        {isEditing && (
          <div className="flex items-center justify-end gap-3 pt-4 border-t">
            <Button
              type="button"
              variant="outline"
              disabled={update.isPending}
              onClick={handleCancel}
              className="gap-1.5"
            >
              <X className="h-3.5 w-3.5" /> Cancel
            </Button>
            <Button
              type="button"
              disabled={update.isPending}
              onClick={handleSave}
              className="gap-2"
            >
              {update.isPending ? <Loader2 className="animate-spin" /> : <Check className="h-3.5 w-3.5" />}
              Save changes
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Auth: Password Policy
// ---------------------------------------------------------------------------

function AuthConfigCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const config = useOrgAuthConfig(organisationId);
  const update = useUpdateOrgAuthConfig(platformSlug, organisationId);

  const form = useForm(authConfigSchema, {
    authType: "PASSWORD",
    passwordEnabled: true,
    passwordMinLength: 8,
    passwordMaxLength: 72,
    passwordExpirationDays: 0,
    passwordHistoryCount: 0,
  } satisfies z.input<typeof authConfigSchema>);

  useEffect(() => {
    if (config.data) {
      form.setValues({
        authType: config.data.authType,
        passwordEnabled: config.data.passwordEnabled,
        passwordMinLength: config.data.passwordMinLength,
        passwordMaxLength: config.data.passwordMaxLength,
        passwordExpirationDays: config.data.passwordExpirationDays,
        passwordHistoryCount: config.data.passwordHistoryCount,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config.data]);

  if (config.isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (config.isError) return <ErrorState error={config.error} onRetry={() => config.refetch()} />;

  const submit = async () => {
    const data = form.values;
    await update.mutateAsync({
      authType: data.authType,
      passwordEnabled: data.passwordEnabled,
      passwordMinLength: data.passwordMinLength,
      passwordMaxLength: data.passwordMaxLength,
      passwordExpirationDays: data.passwordExpirationDays,
      passwordHistoryCount: data.passwordHistoryCount,
    });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <KeyRound className="h-4 w-4 text-primary" /> Password &amp; Authentication Policy
        </CardTitle>
        <CardDescription>
          Configure sign-in credentials, passwordless authentication, and password security rules.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
          <div className="space-y-3">
            {/* Password Enabled Switch */}
            <div className="flex items-center justify-between gap-4 rounded-lg border p-3.5 bg-muted/20">
              <div className="space-y-0.5">
                <Label className="text-sm font-medium">Password Authentication</Label>
                <p className="text-xs text-muted-foreground">
                  Allow users to sign in with a password. When disabled, sign-in is exclusively passwordless via OTP.
                </p>
              </div>
              <Switch
                checked={form.values.passwordEnabled}
                onCheckedChange={(checked) => {
                  form.setValue("passwordEnabled", checked);
                  if (!checked && form.values.authType === "PASSWORD") {
                    form.setValue("authType", "OTP");
                  }
                }}
              />
            </div>

            {/* Default Auth Method */}
            <div className="flex items-center justify-between gap-4 rounded-lg border p-3.5">
              <div className="space-y-0.5">
                <Label className="text-sm font-medium">Default Authentication Method</Label>
                <p className="text-xs text-muted-foreground">
                  Primary authentication flow assigned to new users in this organisation.
                </p>
              </div>
              <Select
                value={form.values.authType}
                onValueChange={(val) => form.setValue("authType", val as "PASSWORD" | "OTP")}
              >
                <SelectTrigger className="w-56">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="PASSWORD" disabled={!form.values.passwordEnabled}>
                    Password (Standard)
                  </SelectItem>
                  <SelectItem value="OTP">One-Time Password (OTP)</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {!form.values.passwordEnabled ? (
            <div className="rounded-lg border border-primary/20 bg-primary/5 p-4 text-sm text-foreground">
              <p className="font-medium text-sm">Passwordless Mode Active</p>
              <p className="text-xs text-muted-foreground mt-1">
                Password credentials are disabled. Users sign in using one-time verification codes (OTP) sent to their registered email address or mobile phone number.
              </p>
            </div>
          ) : (
            <div className="space-y-4 pt-2">
              <div className="border-t pt-3">
                <Label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Password Strength &amp; Expiration Rules
                </Label>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  label="Minimum length"
                  htmlFor="ac-min"
                  error={form.errors.passwordMinLength}
                >
                  <Input
                    id="ac-min"
                    type="number"
                    min={1}
                    max={72}
                    value={form.values.passwordMinLength}
                    onChange={(e) => form.setValue("passwordMinLength", Number(e.target.value))}
                  />
                </FormField>
                <FormField
                  label="Maximum length"
                  htmlFor="ac-max"
                  error={form.errors.passwordMaxLength}
                >
                  <Input
                    id="ac-max"
                    type="number"
                    min={1}
                    max={72}
                    value={form.values.passwordMaxLength}
                    onChange={(e) => form.setValue("passwordMaxLength", Number(e.target.value))}
                  />
                </FormField>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  label="Expire after (days)"
                  htmlFor="ac-expiry"
                  error={form.errors.passwordExpirationDays}
                  hint="0 = passwords never expire"
                >
                  <Input
                    id="ac-expiry"
                    type="number"
                    min={0}
                    max={3650}
                    value={form.values.passwordExpirationDays}
                    onChange={(e) => form.setValue("passwordExpirationDays", Number(e.target.value))}
                  />
                </FormField>
                <FormField
                  label="Reuse history (count)"
                  htmlFor="ac-history"
                  error={form.errors.passwordHistoryCount}
                  hint="0 = reuse allowed"
                >
                  <Input
                    id="ac-history"
                    type="number"
                    min={0}
                    max={50}
                    value={form.values.passwordHistoryCount}
                    onChange={(e) => form.setValue("passwordHistoryCount", Number(e.target.value))}
                  />
                </FormField>
              </div>
            </div>
          )}

          {form.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {form.submitError}
            </p>
          ) : null}
          <div className="flex justify-end pt-2">
            <Button type="submit" disabled={update.isPending} className="gap-2">
              {update.isPending ? <Loader2 className="animate-spin" /> : null}
              Save policy
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Session: token lifetimes
// ---------------------------------------------------------------------------

function SessionSettingsCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const settings = useOrgSessionSettings(organisationId);
  const update = useUpdateOrgSessionSettings(platformSlug, organisationId);

  const [maxSessions, setMaxSessions] = useState(5);
  const [access, setAccess] = useState({ value: 15, unit: "minutes" as DurationUnit });
  const [refresh, setRefresh] = useState({ value: 7, unit: "days" as DurationUnit });
  const [errors, setErrors] = useState<{ access?: string; refresh?: string; sessions?: string }>({});
  const [submitError, setSubmitError] = useState<string | null>(null);

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (settings.data) {
      setMaxSessions(settings.data.maxSessionsPerUser);
      setAccess(decomposeDuration(settings.data.accessTokenTtlSeconds));
      setRefresh(decomposeDuration(settings.data.refreshTokenTtlSeconds));
      setErrors({});
      setSubmitError(null);
    }
  }, [settings.data]);
  /* eslint-enable react-hooks/set-state-in-effect */

  if (settings.isLoading) return <TableSkeleton rows={4} columns={2} />;
  if (settings.isError) return <ErrorState error={settings.error} onRetry={() => settings.refetch()} />;

  const submit = async () => {
    const payload = {
      accessTokenTtlSeconds: toSeconds(access.value, access.unit),
      refreshTokenTtlSeconds: toSeconds(refresh.value, refresh.unit),
      maxSessionsPerUser: maxSessions,
    };
    if (access.value < 1) {
      setErrors({ access: "Value must be at least 1" });
      return;
    }
    if (refresh.value < 1) {
      setErrors({ refresh: "Value must be at least 1" });
      return;
    }
    const parsed = sessionSettingsSchema.safeParse(payload);
    if (!parsed.success) {
      const next: { access?: string; refresh?: string } = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0];
        if (key === "accessTokenTtlSeconds") {
          next.access = issue.message;
        } else if (key === "refreshTokenTtlSeconds") {
          next.refresh = issue.message;
        }
      }
      setErrors(next);
      return;
    }
    setErrors({});
    setSubmitError(null);
    await update.mutateAsync(payload);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Timer className="h-4 w-4 text-primary" /> Session settings
        </CardTitle>
        <CardDescription>
          Token lifetimes and concurrent sessions for this organisation&apos;s users.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
          className="space-y-4"
        >
          <DurationInput
            label="Access token lifetime"
            htmlFor="ss-access"
            value={access.value}
            unit={access.unit}
            onValueChange={(value) => setAccess((p) => ({ ...p, value }))}
            onUnitChange={(unit) => setAccess((p) => ({ ...p, unit }))}
            error={errors.access}
            hint={`${formatDuration(toSeconds(access.value, access.unit))} (max 24 hrs)`}
          />
          <DurationInput
            label="Refresh token lifetime"
            htmlFor="ss-refresh"
            value={refresh.value}
            unit={refresh.unit}
            onValueChange={(value) => setRefresh((p) => ({ ...p, value }))}
            onUnitChange={(unit) => setRefresh((p) => ({ ...p, unit }))}
            error={errors.refresh}
            hint={`${formatDuration(toSeconds(refresh.value, refresh.unit))} (max 365 days)`}
          />
          <FormField
            label="Max sessions per user"
            htmlFor="ss-sessions"
            error={errors.sessions}
            hint="Oldest sessions are evicted when the limit is reached"
          >
            <Input
              id="ss-sessions"
              type="number"
              min={1}
              max={100}
              value={maxSessions}
              onChange={(e) => setMaxSessions(Number(e.target.value))}
            />
          </FormField>
          {submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {submitError}
            </p>
          ) : null}
          <div className="flex justify-end">
            <Button type="submit" disabled={update.isPending} className="gap-2">
              {update.isPending ? <Loader2 className="animate-spin" /> : null}
              Save settings
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Duration value + unit picker helper
// ---------------------------------------------------------------------------

interface DurationInputProps {
  label: string;
  htmlFor: string;
  value: number;
  unit: DurationUnit;
  onValueChange: (value: number) => void;
  onUnitChange: (unit: DurationUnit) => void;
  error?: string;
  hint?: string;
}

function DurationInput({
  label,
  htmlFor,
  value,
  unit,
  onValueChange,
  onUnitChange,
  error,
  hint,
}: DurationInputProps) {
  return (
    <FormField label={label} htmlFor={htmlFor} error={error} hint={hint}>
      <div className="flex items-center gap-2">
        <Input
          id={htmlFor}
          type="number"
          min={1}
          value={value}
          onChange={(e) => onValueChange(Number(e.target.value))}
          className="w-24"
        />
        <Select value={unit} onValueChange={(v) => onUnitChange(v as DurationUnit)}>
          <SelectTrigger aria-label={`${label} unit`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {DURATION_UNITS.map((u) => (
              <SelectItem key={u.value} value={u.value}>
                {u.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </FormField>
  );
}

// ---------------------------------------------------------------------------
// Miscellaneous: Danger Zone
// ---------------------------------------------------------------------------

function DangerZoneCard({ platformSlug, organisationId }: OrgSettingsTabProps) {
  const org = useOrganisation(organisationId);
  const router = useRouter();
  const remove = useDeleteOrganisation(platformSlug, organisationId);

  const [open, setOpen] = useState(false);
  const [typedName, setTypedName] = useState("");

  if (org.isLoading) return <TableSkeleton rows={2} columns={2} />;
  if (org.isError || !org.data)
    return <ErrorState error={org.error ?? new Error("Organisation not found")} onRetry={() => org.refetch()} />;

  const data = org.data;

  return (
    <>
      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle className="text-base text-destructive">Danger zone</CardTitle>
          <CardDescription>
            Deleting an organisation removes its users, roles, signing keys and sessions permanently.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="destructive"
            className="gap-2"
            onClick={() => {
              setTypedName("");
              setOpen(true);
            }}
          >
            <Trash2 /> Delete organisation
          </Button>
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={(next) => !remove.isPending && setOpen(next)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete this organisation?</DialogTitle>
            <DialogDescription>
              This permanently removes the organisation and all of its users, roles,
              signing keys and sessions. It cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (typedName.trim() !== data.name || remove.isPending) return;
              void (async () => {
                await remove.mutateAsync();
                setOpen(false);
                router.replace("/console/organisations");
              })();
            }}
            className="space-y-4"
          >
            <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 font-mono text-sm">
              {data.name}
            </div>
            <FormField label="Type the organisation name to confirm" htmlFor="delete-confirm">
              <Input
                id="delete-confirm"
                value={typedName}
                onChange={(e) => setTypedName(e.target.value)}
                autoFocus
                autoComplete="off"
                aria-invalid={typedName.trim() !== "" && typedName.trim() !== data.name}
              />
            </FormField>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
                disabled={remove.isPending}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="destructive"
                className="gap-2"
                disabled={remove.isPending || typedName.trim() !== data.name}
              >
                {remove.isPending ? <Loader2 className="animate-spin" /> : <Trash2 />}
                Delete organisation
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
