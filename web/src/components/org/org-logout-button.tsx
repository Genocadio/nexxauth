"use client";

import { LogOut } from "lucide-react";
import { useState } from "react";
import { logoutOrg } from "@/app/org/[platformSlug]/[organisationId]/actions";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { Button } from "@/components/ui/button";

interface OrgLogoutButtonProps {
  platformSlug: string;
  organisationId: number;
}

/** Sign out of the org portal (server action clears the session cookies). */
export function OrgLogoutButton({ platformSlug, organisationId }: OrgLogoutButtonProps) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);

  const confirm = async () => {
    setPending(true);
    try {
      await logoutOrg(platformSlug, organisationId); // redirects to the login
    } finally {
      setPending(false);
    }
  };

  return (
    <>
      <Button
        variant="ghost"
        size="sm"
        className="gap-2 text-muted-foreground"
        onClick={() => setOpen(true)}
      >
        <LogOut /> Sign out
      </Button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title="Sign out?"
        description="You will need your organisation credentials to sign back in."
        confirmLabel="Sign out"
        pending={pending}
        onConfirm={confirm}
      />
    </>
  );
}
