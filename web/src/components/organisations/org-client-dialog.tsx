"use client";

import { Loader2 } from "lucide-react";
import { FormField } from "@/components/shared/form-field";
import { ToneBadge } from "@/components/shared/status-badge";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useCreateOrgClient, useUpdateOrgClient } from "@/hooks/mutations";
import { useForm } from "@/hooks/use-form";
import { orgClientFormSchema } from "@/lib/validation";
import type { OrganisationClientResponse } from "@/types/api";
import { CLIENT_TYPE_AUTH_MODE, CLIENT_TYPE_META, CLIENT_TYPE_TONE, type ClientType } from "@/types/enums";

interface OrgClientDialogProps {
  platformSlug: string;
  organisationId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  client?: OrganisationClientResponse;
  onCreated?: (client: OrganisationClientResponse) => void;
}

const CLIENT_TYPES: ClientType[] = ["WEB", "ANDROID", "IOS", "SERVER"];

const AUTH_HINTS: Record<ClientType, string> = {
  WEB: "Web clients never authenticate — they sign users in via the login endpoint.",
  ANDROID: "When on, the client authenticates with a static token; when off it can only reach login and register.",
  IOS: "When on, the client authenticates with a static token; when off it can only reach login and register.",
  SERVER: "Server clients always authenticate with a static token.",
};

export function OrgClientDialog({
  platformSlug,
  organisationId,
  open,
  onOpenChange,
  client,
  onCreated,
}: OrgClientDialogProps) {
  const isEdit = !!client;
  const create = useCreateOrgClient(platformSlug, organisationId);
  const update = useUpdateOrgClient(platformSlug, organisationId);
  const pending = create.isPending || update.isPending;

  const form = useForm(orgClientFormSchema, {
    name: client?.name ?? "",
    type: client?.type ?? "WEB",
    requireAuthentication: client?.requireAuthentication ?? false,
    enabled: client?.enabled ?? true,
  });

  const authMode = CLIENT_TYPE_AUTH_MODE[form.values.type];
  const authLocked = authMode !== "optional";

  const selectType = (type: ClientType) => {
    form.setValue("type", type);
    form.setValue("requireAuthentication", CLIENT_TYPE_AUTH_MODE[type] === "auth");
  };

  const submit = async () => {
    const data = form.values;
    const authOptional = CLIENT_TYPE_AUTH_MODE[data.type] === "optional";

    if (isEdit && client) {
      await update.mutateAsync({
        clientKey: client.clientKey,
        body: {
          name: data.name,
          enabled: client.enabled,
          ...(authOptional ? { requireAuthentication: data.requireAuthentication } : {}),
        },
      });
      onOpenChange(false);
    } else {
      const created = await create.mutateAsync({
        name: data.name,
        type: data.type,
        enabled: true,
        ...(authOptional ? { requireAuthentication: data.requireAuthentication } : {}),
      });
      onCreated?.(created);
      onOpenChange(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !pending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit client" : "New client"}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update the name or authentication for this client."
              : "A client is an external app that talks to this organisation's API. Configure access restrictions after creation."}
          </DialogDescription>
        </DialogHeader>
        <form id="org-client-form" onSubmit={form.handleSubmit(() => submit())} className="space-y-4">
          <FormField label="Name" htmlFor="client-name" error={form.errors.name}>
            <Input
              id="client-name"
              placeholder="e.g. Storefront web"
              value={form.values.name}
              onChange={(e) => form.setValue("name", e.target.value)}
            />
          </FormField>

          {isEdit && client ? (
            <FormField label="Type" hint={CLIENT_TYPE_META[client.type].description}>
              <div className="pt-1">
                <ToneBadge tone={CLIENT_TYPE_TONE[client.type]}>{CLIENT_TYPE_META[client.type].label}</ToneBadge>
              </div>
            </FormField>
          ) : (
            <FormField
              label="Type"
              htmlFor="client-type"
              hint={CLIENT_TYPE_META[form.values.type].description}
              error={form.errors.type}
            >
              <Select value={form.values.type} onValueChange={(type) => selectType(type as ClientType)}>
                <SelectTrigger id="client-type" className="w-full">
                  <SelectValue placeholder="Select a type" />
                </SelectTrigger>
                <SelectContent>
                  {CLIENT_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {CLIENT_TYPE_META[type].label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormField>
          )}

          <FormField label="Token authentication" hint={AUTH_HINTS[form.values.type]}>
            <div className="flex items-center justify-between rounded-md border px-3 py-2">
              <span className="text-sm">
                {form.values.requireAuthentication ? "Authenticated client" : "Sign-in only"}
              </span>
              <Switch
                checked={form.values.requireAuthentication}
                disabled={authLocked}
                onCheckedChange={(checked) => form.setValue("requireAuthentication", checked)}
              />
            </div>
          </FormField>

          {form.submitError ? (
            <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {form.submitError}
            </p>
          ) : null}
        </form>
        <DialogFooter>
          <Button type="submit" form="org-client-form" disabled={pending} className="gap-2">
            {pending ? <Loader2 className="animate-spin" /> : null}
            {isEdit ? "Save changes" : "Create client"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
