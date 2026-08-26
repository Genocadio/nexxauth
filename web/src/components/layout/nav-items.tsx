"use client";

import { Suspense } from "react";
import {
  AppWindow,
  BookOpen,
  CreditCard,
  KeyRound,
  LayoutDashboard,
  Layers,
  Settings,
  ShieldCheck,
  Users,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { cn } from "@/lib/utils";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
}

export interface OrgNavItem {
  tab: string;
  label: string;
  icon: LucideIcon;
  /** Optional section group label */
  section?: string;
}

export const CONSOLE_NAV: NavItem[] = [
  { href: "/console/overview", label: "Overview", icon: LayoutDashboard },
  { href: "/console/users", label: "Users", icon: Users },
  { href: "/console/profile", label: "Profile & security", icon: Settings },
];

export const ORG_NAV: OrgNavItem[] = [
  { tab: "overview", label: "Overview", icon: LayoutDashboard },
  { tab: "users", label: "Users", icon: Users },
  { tab: "roles", label: "Roles", icon: ShieldCheck },
  { tab: "fields", label: "Fields", icon: Layers, section: "Configuration" },
  { tab: "keys", label: "Keys", icon: KeyRound, section: "Security" },
  { tab: "clients", label: "Clients", icon: AppWindow },
  { tab: "settings", label: "Settings", icon: Settings },
];

function NavItemLink({
  active,
  href,
  icon: Icon,
  label,
  onClick,
}: {
  active: boolean;
  href: string;
  icon: LucideIcon;
  label: string;
  onClick?: () => void;
}) {
  return (
    <Link
      href={href}
      onClick={onClick}
      className={cn(
        "group relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-150",
        active
          ? "bg-primary/10 text-primary"
          : "text-muted-foreground hover:bg-muted hover:text-foreground",
      )}
    >
      {/* Active indicator bar */}
      {active && (
        <span className="absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-full bg-primary" />
      )}
      <Icon
        className={cn(
          "h-4 w-4 shrink-0 transition-colors duration-150",
          active ? "text-primary" : "text-muted-foreground group-hover:text-foreground",
        )}
      />
      {label}
    </Link>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <p className="mb-1 mt-4 px-3 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70 first:mt-0">
      {children}
    </p>
  );
}

export function NavContent({
  mode,
  organisationSlug,
  organisationId,
  onNavigate,
}: {
  mode: "platform" | "org";
  organisationSlug?: string;
  organisationId?: number;
  onNavigate?: () => void;
}) {
  if (mode === "org") {
    // Group nav items by section
    let lastSection: string | undefined;

    return (
      <nav className="flex flex-col gap-0.5">
        <Suspense fallback={null}>
          {ORG_NAV.map((item) => {
            const href = `/console/organisations/${organisationSlug}?tab=${item.tab}`;
            const showSection = item.section && item.section !== lastSection;
            if (item.section) lastSection = item.section;

            return (
              <div key={item.tab}>
                {showSection && <SectionLabel>{item.section}</SectionLabel>}
                <span onClick={onNavigate}>
                  <OrgNavLink item={item} organisationSlug={organisationSlug ?? ""} />
                </span>
              </div>
            );
          })}

          {organisationId && (
            <div className="mt-2">
              <SectionLabel>Resources</SectionLabel>
              <span onClick={onNavigate}>
                <NavItemLink
                  active={false}
                  href={`/docs/${organisationSlug}/${organisationId}`}
                  icon={BookOpen}
                  label="Documentation"
                />
              </span>
            </div>
          )}
        </Suspense>
      </nav>
    );
  }

  return (
    <nav className="flex flex-col gap-0.5">
      {CONSOLE_NAV.map((item) => (
        <span key={item.href} onClick={onNavigate}>
          <NavItemLink
            active={useIsPathActive(item.href)}
            href={item.href}
            icon={item.icon}
            label={item.label}
          />
        </span>
      ))}
    </nav>
  );
}

function OrgNavLink({
  item,
  organisationSlug,
}: {
  item: OrgNavItem;
  organisationSlug: string;
}) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const href = `/console/organisations/${organisationSlug}?tab=${item.tab}`;
  const tab = searchParams.get("tab") ?? "overview";
  const active = pathname === `/console/organisations/${organisationSlug}` && tab === item.tab;

  return (
    <NavItemLink
      active={active}
      href={href}
      icon={item.icon}
      label={item.label}
    />
  );
}

function useIsPathActive(href: string): boolean {
  const pathname = usePathname();
  return pathname === href || pathname.startsWith(`${href}/`);
}
