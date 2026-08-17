"use client";

import { useEffect, useState } from "react";
import { Building2, Menu, PanelLeftClose, PanelLeftOpen, ShieldCheck } from "lucide-react";
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
import { cn } from "@/lib/utils";

const SIDEBAR_STORAGE_KEY = "console-sidebar-collapsed";

/** Redirects to /login when no platform session exists. */
export function RequirePlatformAuth({ children }: { children: React.ReactNode }) {
  const session = useAppSelector(selectPlatformSession);
  const router = useRouter();
  const mounted = useIsClient();

  useEffect(() => {
    if (mounted && !session) router.replace("/login");
  }, [mounted, session, router]);

  // Server and first client render agree (nothing) so hydration never mismatches.
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

function Brand({ collapsed = false }: { collapsed?: boolean }) {
  const session = useAppSelector(selectPlatformSession);
  const { isOrgView, organisationSlug } = useOrgView();
  const organisations = useOrganisations();
  const org = isOrgView
    ? organisations.data?.find((o) => o.slug === organisationSlug)
    : undefined;

  if (isOrgView) {
    return (
      <>
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <Building2 className="h-4 w-4" />
        </div>
        {!collapsed ? (
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold leading-tight">{org?.name ?? organisationSlug}</p>
            <p className="truncate text-xs text-muted-foreground">{org?.slug ?? "organisation"}</p>
          </div>
        ) : null}
      </>
    );
  }

  return (
    <>
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <ShieldCheck className="h-4 w-4" />
      </div>
      {!collapsed ? (
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold leading-tight">
            {session?.user.platform.name ?? "Nexxauth"}
          </p>
          <p className="truncate text-xs text-muted-foreground">{session?.user.platform.slug}</p>
        </div>
      ) : null}
    </>
  );
}

export function ConsoleShell({ children }: { children: React.ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { isOrgView, organisationSlug, organisationId } = useOrgView();

  // The shell only mounts client-side (behind RequirePlatformAuth), so reading
  // localStorage during initial render can't cause a hydration mismatch.
  const [collapsed, setCollapsed] = useState(() => {
    if (typeof window === "undefined") return false;
    try {
      return window.localStorage.getItem(SIDEBAR_STORAGE_KEY) === "true";
    } catch {
      return false;
    }
  });

  const toggleCollapsed = () => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
      } catch {
        // Ignore — collapsing still works for this session.
      }
      return next;
    });
  };

  return (
    <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
      <div className={cn("min-h-dvh lg:grid", collapsed ? "lg:grid-cols-[72px_1fr]" : "lg:grid-cols-[240px_1fr]")}>
        {/* Desktop sidebar */}
        <aside className="hidden border-r bg-sidebar lg:flex lg:flex-col">
          <div className={cn("flex h-14 items-center border-b", collapsed ? "justify-center px-0" : "gap-2 px-4")}>
            <Brand collapsed={collapsed} />
          </div>
          <div className="flex flex-1 flex-col overflow-y-auto">
            <div className="flex-1 py-2">
              <NavContent
                mode={isOrgView ? "org" : "platform"}
                organisationSlug={organisationSlug}
                organisationId={organisationId}
                collapsed={collapsed}
              />
            </div>
            {isOrgView ? (
              <div className="border-t p-3">
                <Link
                  href="/console/organisations"
                  title="Platform management"
                  className={cn(
                    "flex h-9 items-center rounded-md text-sm font-medium text-muted-foreground transition-colors hover:bg-sidebar-accent/60 hover:text-foreground",
                    collapsed ? "w-9 justify-center px-0" : "gap-3 px-3",
                  )}
                >
                  <ShieldCheck className="h-4 w-4 shrink-0" />
                  {!collapsed ? "Platform management" : null}
                </Link>
              </div>
            ) : null}
            <div className="border-t p-2">
              <Button
                variant="ghost"
                size="icon"
                className="mx-auto flex"
                aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
                onClick={toggleCollapsed}
              >
                {collapsed ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
              </Button>
            </div>
          </div>
        </aside>

        {/* Main column */}
        <div className="flex min-w-0 flex-col">
          <header className="sticky top-0 z-30 flex h-14 items-center justify-between gap-2 border-b bg-background/80 px-4 backdrop-blur">
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

      {/* Mobile navigation sheet */}
      <SheetContent side="left" className="w-64 p-0">
        <SheetHeader className="border-b px-4 py-3">
          <SheetTitle className="flex items-center gap-2 text-base">
            <Brand />
          </SheetTitle>
        </SheetHeader>
        <div className="flex flex-col overflow-y-auto">
          <div className="flex-1">
            <NavContent
              mode={isOrgView ? "org" : "platform"}
              organisationSlug={organisationSlug}
              organisationId={organisationId}
              onNavigate={() => setMobileOpen(false)}
            />
          </div>
          {isOrgView ? (
            <div className="border-t p-3">
              <Link
                href="/console/organisations"
                onClick={() => setMobileOpen(false)}
                className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-sidebar-accent/60 hover:text-foreground"
              >
                <ShieldCheck className="h-4 w-4 shrink-0" />
                Platform management
              </Link>
            </div>
          ) : null}
        </div>
      </SheetContent>
    </Sheet>
  );
}
