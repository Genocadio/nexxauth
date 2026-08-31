"use client";

import { Loader2, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
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
import { Switch } from "@/components/ui/switch";
import {
  useCreateOrgClientLink,
  useDeleteOrgClientLink,
  useUpdateOrgClientLink,
} from "@/hooks/mutations";
import { useOrgClientLinks } from "@/hooks/queries";
import type { OrganisationClientLinkResponse } from "@/types/api";

interface OrgClientLinksDialogProps {
  platformSlug: string;
  organisationId: number;
  clientKey: string;
  clientName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function OrgClientLinksDialog({
  platformSlug,
  organisationId,
  clientKey,
  clientName,
  open,
  onOpenChange,
}: OrgClientLinksDialogProps) {
  const links = useOrgClientLinks(organisationId, clientKey);
  const createLink = useCreateOrgClientLink(platformSlug, organisationId, clientKey);
  const updateLink = useUpdateOrgClientLink(platformSlug, organisationId, clientKey);
  const deleteLink = useDeleteOrgClientLink(platformSlug, organisationId, clientKey);

  const [newOrigin, setNewOrigin] = useState("");
  const [newAllowCors, setNewAllowCors] = useState(true);
  const [newLimitSource, setNewLimitSource] = useState(false);

  const handleAdd = async () => {
    if (!newOrigin.trim()) return;
    await createLink.mutateAsync({
      origin: newOrigin.trim(),
      allowCors: newAllowCors,
      limitSource: newLimitSource,
    });
    setNewOrigin("");
    setNewAllowCors(true);
    setNewLimitSource(false);
  };

  const handleToggleCors = async (link: OrganisationClientLinkResponse, allowCors: boolean) => {
    // Turning CORS off also disables limit source (it requires CORS to be meaningful)
    const body: { allowCors: boolean; limitSource?: boolean } = { allowCors };
    if (!allowCors && link.limitSource) {
      body.limitSource = false;
    }
    await updateLink.mutateAsync({ linkId: link.id, body });
  };

  const handleToggleLimitSource = async (link: OrganisationClientLinkResponse, limitSource: boolean) => {
    await updateLink.mutateAsync({ linkId: link.id, body: { limitSource } });
  };

  const handleDelete = async (linkId: number) => {
    await deleteLink.mutateAsync(linkId);
  };

  const data = links.data ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Link management</DialogTitle>
          <DialogDescription>
            Configure allowed origins and CORS settings for <strong>{clientName}</strong>.
            Each link controls which origins can access this client.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Existing links */}
          {data.length > 0 && (
            <div className="space-y-2">
              {data.map((link) => (
                <div
                  key={link.id}
                  className="rounded-lg border p-3 space-y-2"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-mono truncate flex-1 mr-2">
                      {link.origin}
                    </span>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8 text-destructive hover:text-destructive"
                      onClick={() => handleDelete(link.id)}
                      disabled={deleteLink.isPending}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>

                  <div className="flex items-center gap-4">
                    <div className="flex items-center gap-2">
                      <Switch
                        checked={link.allowCors}
                        onCheckedChange={(checked) => handleToggleCors(link, checked)}
                        disabled={updateLink.isPending}
                      />
                      <span className="text-xs text-muted-foreground">Allow CORS</span>
                    </div>

                    <div className="flex items-center gap-2">
                      <Switch
                        checked={link.limitSource}
                        onCheckedChange={(checked) => handleToggleLimitSource(link, checked)}
                        disabled={updateLink.isPending || !link.allowCors}
                      />
                      <span className={`text-xs ${!link.allowCors ? "text-muted-foreground/50" : "text-muted-foreground"}`}>Limit source</span>
                    </div>
                  </div>

                  {!link.allowCors && (
                    <p className="text-[11px] text-muted-foreground/60">
                      Enable Allow CORS before using Limit source.
                    </p>
                  )}

                  {link.limitSource && link.allowCors && (
                    <p className="text-[11px] text-amber-600 dark:text-amber-400">
                      Only requests from this origin will be allowed.
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}

          {data.length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-4">
              No links configured. Add a link below to allow CORS access from an origin.
            </p>
          )}

          {/* Add new link */}
          <div className="rounded-lg border p-3 space-y-3">
            <p className="text-sm font-medium">Add link</p>

            <Input
              placeholder="https://app.example.com"
              value={newOrigin}
              onChange={(e) => setNewOrigin(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && newOrigin.trim()) {
                  e.preventDefault();
                  handleAdd();
                }
              }}
            />              <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <Switch checked={newAllowCors} onCheckedChange={setNewAllowCors} />
                <span className="text-xs text-muted-foreground">Allow CORS</span>
              </div>

              <div className="flex items-center gap-2">
                <Switch
                  checked={newLimitSource}
                  onCheckedChange={setNewLimitSource}
                  disabled={!newAllowCors}
                />
                <span className={`text-xs ${!newAllowCors ? "text-muted-foreground/50" : "text-muted-foreground"}`}>Limit source</span>
              </div>
            </div>

            {!newAllowCors && (
              <p className="text-[11px] text-muted-foreground/60">
                Enable Allow CORS before using Limit source.
              </p>
            )}

            {newLimitSource && newAllowCors && (
              <p className="text-[11px] text-amber-600 dark:text-amber-400">
                When enabled, only requests from this specific origin will be allowed.
              </p>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button
            onClick={handleAdd}
            disabled={!newOrigin.trim() || createLink.isPending}
            className="gap-2"
          >
            {createLink.isPending ? <Loader2 className="animate-spin" /> : <Plus className="h-4 w-4" />}
            Add link
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
