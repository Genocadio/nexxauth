"use client";

import { useState } from "react";
import { KeyRound, RefreshCw } from "lucide-react";
import { CopyButton } from "@/components/shared/copy-button";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { ErrorState } from "@/components/shared/error-state";
import { TableSkeleton } from "@/components/shared/loading";
import { ToneBadge } from "@/components/shared/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useRotateOrgKey } from "@/hooks/mutations";
import { useOrgKeys } from "@/hooks/queries";

interface OrgKeysTabProps {
  platformSlug: string;
  organisationSlug: string;
}

export function OrgKeysTab({ platformSlug, organisationSlug }: OrgKeysTabProps) {
  const keys = useOrgKeys(organisationSlug);
  const rotate = useRotateOrgKey(platformSlug, organisationSlug);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const activeKey = keys.data?.find((key) => key.active);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <KeyRound className="h-4 w-4 text-primary" /> Signing key
        </CardTitle>
        <CardDescription>
          This organisation&apos;s access tokens are signed with its own RSA keypair. Only the active
          public key is shown here — the private key never leaves the server. Retired keys stay valid
          for verification until the tokens they signed expire.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {keys.isLoading ? (
          <TableSkeleton rows={3} columns={2} />
        ) : keys.isError ? (
          <ErrorState error={keys.error} onRetry={() => keys.refetch()} />
        ) : (
          <>
            {activeKey ? (
              <div className="rounded-lg border p-4">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm font-medium">Active public key</p>
                  <ToneBadge tone="success">Active</ToneBadge>
                </div>
                <div className="relative mt-3">
                  <pre className="max-h-40 overflow-auto rounded-md bg-muted/60 p-3 pr-12 font-mono text-[10px] leading-relaxed text-muted-foreground">
                    {activeKey.publicKey}
                  </pre>
                  <CopyButton
                    value={activeKey.publicKey}
                    label="Copy public key"
                    className="absolute right-3 top-3"
                  />
                </div>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                No signing key yet — one is created on the first login.
              </p>
            )}
            <div className="flex flex-col gap-2 rounded-lg border border-dashed p-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-medium">Rotate the active key</p>
                <p className="text-xs text-muted-foreground">
                  Retires the current key and generates a fresh one. Tokens signed with the old key keep verifying until they expire.
                </p>
              </div>
              <Button
                variant="outline"
                size="sm"
                className="gap-2"
                disabled={rotate.isPending}
                onClick={() => setConfirmOpen(true)}
              >
                <RefreshCw className={rotate.isPending ? "animate-spin" : ""} />
                Rotate key
              </Button>
            </div>
          </>
        )}
      </CardContent>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Rotate signing key?"
        description="A new keypair is generated and becomes active immediately. Existing access tokens keep working until they expire."
        confirmLabel="Rotate key"
        pending={rotate.isPending}
        onConfirm={async () => {
          await rotate.mutateAsync();
          setConfirmOpen(false);
        }}
      />
    </Card>
  );
}
