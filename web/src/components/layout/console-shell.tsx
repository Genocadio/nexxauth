"use client";

import { useEffect, useState } from "react";
import { Building2, Menu, ShieldCheck } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { ErrorBoundary } from "@/components/shared/error-boundary";
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

export function ConsoleShell({ children }: { children: React.ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { isOrgView, organisationSlug, organisationId } = useOrgView();

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
      } catch {}
      return next;
    });
  };

  const isOrg = isOrgView;

  return (
    <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
      <div
        className={cn(
          "h-dvh lg:grid transition-[grid-template-columns] duration-300 ease-in-out",
          collapsed ? "lg:grid-cols-[68px_1fr]" : "lg:grid-cols-[240px_1fr]",
        )}
      >
        {/* ── Desktop sidebar ────────────────────────────────────────── */}
        <aside className="hidden bg-sidebar lg:flex lg:flex-col">
          {/* Brand / collapse trigger */}
          <div className="flex h-14 items-center px-3">
            <button
              onClick={toggleCollapsed}
              className={cn(
                "flex items-center gap-2.5 rounded-xl transition-all duration-200 hover:bg-muted",
                collapsed ? "h-9 w-9 justify-center" : "h-9 w-full px-2.5",
              )}
              title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            >
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-primary/70 text-primary-foreground shadow-sm shadow-primary/20">
                {isOrg ? (
                  <Building2 className="h-4 w-4" />
                ) : (
                  <ShieldCheck className="h-4 w-4" />
                )}
              </div>
              {!collapsed && (
                <span className="text-sm font-semibold tracking-tight">Nexxauth</span>
              )}
            </button>
          </div>

          {/* Navigation */}
          <div className="flex flex-1 flex-col overflow-y-auto px-3 py-1">
            <NavContent
              mode={isOrg ? "org" : "platform"}
              organisationSlug={organisationSlug}
              organisationId={organisationId}
              collapsed={collapsed}
            />
          </div>
        </aside>

        {/* ── Main column ────────────────────────────────────────────── */}
        <div className="flex min-w-0 flex-col lg:h-dvh lg:overflow-y-auto">
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
          <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6">
              <ErrorBoundary>{children}</ErrorBoundary>
            </main>
        </div>
      </div>

      {/* ── Mobile navigation sheet ──────────────────────────────────── */}
      <SheetContent side="left" className="w-72 p-0 h-dvh flex flex-col">
        <SheetHeader className="shrink-0 border-b px-5 py-4">
          <SheetTitle className="text-base">
            <div className="flex items-center gap-2.5">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-primary/70 text-primary-foreground shadow-sm shadow-primary/20">
                {isOrg ? <Building2 className="h-4 w-4" /> : <ShieldCheck className="h-4 w-4" />}
              </div>
              <span className="text-sm font-semibold">Nexxauth</span>
            </div>
          </SheetTitle>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-3 py-2" style={{ paddingBottom: "max(0.5rem, env(safe-area-inset-bottom))" }}>
          <NavContent
            mode={isOrg ? "org" : "platform"}
            organisationSlug={organisationSlug}
            organisationId={organisationId}
            onNavigate={() => setMobileOpen(false)}
          />
        </div>
      </SheetContent>
    </Sheet>
  );
}
