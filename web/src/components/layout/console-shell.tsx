"use client";

import { useEffect, useState } from "react";
import { Building2, Menu, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { NavContent } from "@/components/layout/nav-items";
import { OrgSwitcher } from "@/components/layout/org-switcher";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { UserMenu } from "@/components/layout/user-menu";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { useIsClient } from "@/hooks/use-is-client";
import { useOrganisations } from "@/hooks/queries";
import { selectPlatformSession, useAppSelector } from "@/store/store";

/** Redirects to /login when no platform session exists. */
export function RequirePlatformAuth({ children }: { children: React.ReactNode }) {
  const session = useAppSelector(selectPlatformSession);
  const router = useRouter();
  const mounted = useIsClient();

  useEffect(() => {
    if (mounted && !session) router.replace("/login");
  }, [mounted, session, router]);

  if (!mounted || !session) return null;
  return <>{children}</>;
}

function useOrgView() {
  const pathname = usePathname();
  const segments = pathname.split("/");
  const organisationSlug = segments[2] === "organisations" ? segments[3] : undefined;
  const organisations = useOrganisations();
  const org = organisationSlug
    ? organisations.data?.find((o) => o.slug === organisationSlug)
    : undefined;
  return { isOrgView: !!organisationSlug, organisationSlug, organisationId: org?.id };
}

function Brand() {
  const session = useAppSelector(selectPlatformSession);
  const { isOrgView, organisationSlug } = useOrgView();
  const organisations = useOrganisations();
  const org = isOrgView
    ? organisations.data?.find((o) => o.slug === organisationSlug)
    : undefined;

  if (isOrgView) {
    return (
      <div className="flex items-center gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary/70 text-primary-foreground shadow-md shadow-primary/20">
          <Building2 className="h-4.5 w-4.5" />
        </div>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold leading-tight">{org?.name ?? organisationSlug}</p>
          <p className="truncate text-[11px] text-muted-foreground">{org?.slug ?? "organisation"}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary/70 text-primary-foreground shadow-md shadow-primary/20">
        <ShieldCheck className="h-4.5 w-4.5" />
      </div>
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold leading-tight">
          {session?.user.platform.name ?? "Nexxauth"}
        </p>
        <p className="truncate text-[11px] text-muted-foreground">
          {session?.user.platform.slug}
        </p>
      </div>
    </div>
  );
}

export function ConsoleShell({ children }: { children: React.ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { isOrgView, organisationSlug, organisationId } = useOrgView();

  return (
    <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
      <div className="min-h-dvh lg:grid lg:grid-cols-[260px_1fr]">
        {/* ── Desktop sidebar ────────────────────────────────────────── */}
        <aside className="hidden border-r bg-sidebar lg:flex lg:flex-col">
          {/* Brand */}
          <div className="flex h-16 items-center gap-3 border-b px-5">
            <Brand />
          </div>

          {/* Navigation */}
          <div className="flex flex-1 flex-col overflow-y-auto px-3 py-4">
            <div className="flex-1">
              <NavContent
                mode={isOrgView ? "org" : "platform"}
                organisationSlug={organisationSlug}
                organisationId={organisationId}
              />
            </div>
          </div>

          {/* Bottom section: theme toggle */}
          <div className="border-t px-4 py-3">
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">Appearance</span>
              <ThemeToggle />
            </div>
          </div>
        </aside>

        {/* ── Main column ────────────────────────────────────────────── */}
        <div className="flex min-w-0 flex-col">
          <header className="sticky top-0 z-30 flex h-14 items-center justify-between gap-2 border-b bg-background/80 px-4 backdrop-blur-xl supports-[backdrop-filter]:bg-background/60">
            <div className="flex min-w-0 items-center gap-2">
              <SheetTrigger asChild className="lg:hidden">
                <Button variant="ghost" size="icon" aria-label="Open menu">
                  <Menu />
                </Button>
              </SheetTrigger>
              <OrgSwitcher />
            </div>
            <div className="flex items-center gap-1">
              <ThemeToggle />
              <UserMenu />
            </div>
          </header>
          <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6">{children}</main>
        </div>
      </div>

      {/* ── Mobile navigation sheet ──────────────────────────────────── */}
      <SheetContent side="left" className="w-72 p-0">
        <SheetHeader className="border-b px-5 py-4">
          <SheetTitle className="text-base">
            <Brand />
          </SheetTitle>
        </SheetHeader>
        <div className="flex flex-1 flex-col overflow-y-auto px-3 py-4">
          <NavContent
            mode={isOrgView ? "org" : "platform"}
            organisationSlug={organisationSlug}
            organisationId={organisationId}
            onNavigate={() => setMobileOpen(false)}
          />
        </div>
      </SheetContent>
    </Sheet>
  );
}
