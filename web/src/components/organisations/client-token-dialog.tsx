"use client";

import { AlertTriangle } from "lucide-react";
import { CopyButton } from "@/components/shared/copy-button";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface ClientTokenDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  token: string;
  title?: string;
}

/**
 * Displays a freshly created/rotated client token. The server only stores a
 * hash, so this is the one and only time the token can be shown or copied.
 */
export function ClientTokenDialog({ open, onOpenChange, token, title = "Client token" }: ClientTokenDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            Copy this token now — it is shown exactly once. The server stores only a hash of it, so
            it can never be displayed again.
          </DialogDescription>
        </DialogHeader>
        <div className="relative">
          <pre className="break-all rounded-md bg-muted/60 p-3 pr-12 font-mono text-xs text-foreground">
            {token}
          </pre>
          <CopyButton value={token} label="Copy token" className="absolute right-2 top-2" />
        </div>
        <p className="flex items-start gap-2 text-xs text-muted-foreground">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          Send it as <span className="font-mono">Authorization: Bearer &lt;token&gt;</span> together with the{" "}
          <span className="font-mono">X-Client-Id</span> header on every request.
        </p>
        <DialogFooter>
          <Button onClick={() => onOpenChange(false)}>Done</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
