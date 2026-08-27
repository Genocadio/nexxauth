"use client";

import { Suspense } from "react";
import {
  AppWindow,
  BookOpen,
  FileText,
  Fingerprint,
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
  section?: string;
}

export const CONSOLE_NAV: NavItem[] = [
  { href: "/console/overview", label: "Overview", icon: LayoutDashboard },
  { href: "/console/users", label: "Users", icon: Users },
  { href: "/console/logs", label: "Logs", icon: FileText },
  { href: "/console/profile", label: "Profile & security", icon: Settings },
];

export const ORG_NAV: OrgNavItem[] = [
  { tab: "overview", label: "Overview", icon: LayoutDashboard },
  { tab: "users", label: "Users", icon: Users },
  { tab: "roles", label: "Roles", icon: ShieldCheck },
  { tab: "fields", label: "Fields", icon: Layers, section: "Configuration" },
  { tab: "keys", label: "Keys", icon: KeyRound, section: "Security" },
  { tab: "clients", label: "Clients", icon: AppWindow },
  { tab: "sessions", label: "Sessions", icon: Fingerprint },
  { tab: "settings", label: "Settings", icon: Settings },
];

function SectionLabel({ children, collapsed }: { children: React.ReactNode; collapsed?: boolean }) {
  if (collapsed) return null;
  return (
    <p className="mb-1 mt-4 px-3 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/60 first:mt-0">
      {children}
    </p>
  );
}

function NavItemLink({
  active,
  href,
  icon: Icon,
  label,
  collapsed = false,
  onClick,
}: {
  active: boolean;
  href: string;
  icon: LucideIcon;
  label: string;
  collapsed?: boolean;
  onClick?: () => void;
}) {
  return (
    <Link
      href={href}
      onClick={onClick}
      title={collapsed ? label : undefined}
      className={cn(
        "group relative flex items-center rounded-lg text-sm font-medium transition-all duration-150",
        collapsed ? "h-9 justify-center" : "h-9 gap-3 px-3",
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
      {!collapsed && label}
    </Link>
  );
}

export function NavContent({
  mode,
  organisationSlug,
  organisationId,
  collapsed = false,
  onNavigate,
}: {
  mode: "platform" | "org";
  organisationSlug?: string;
  organisationId?: number;
  collapsed?: boolean;
  onNavigate?: () => void;
}) {
  if (mode === "org") {
    return (
      <nav className="flex flex-col gap-0.5">
        <Suspense fallback={null}>
          {ORG_NAV.map((item, idx) => {
            const href = `/console/organisations/${organisationSlug}?tab=${item.tab}`;
            const prevSection = idx > 0 ? ORG_NAV[idx - 1].section : undefined;
            const showSection = item.section && item.section !== prevSection && !collapsed;

            return (
              <div key={item.tab}>
                {showSection && <SectionLabel>{item.section}</SectionLabel>}
                <span onClick={onNavigate}>
                  <OrgNavLink
                    item={item}
                    organisationSlug={organisationSlug ?? ""}
                    collapsed={collapsed}
                  />
                </span>
              </div>
            );
          })}

          {organisationId && (
            <div className={collapsed ? "" : "mt-2"}>
              {!collapsed && <SectionLabel>Resources</SectionLabel>}
              <span onClick={onNavigate}>
                <NavItemLink
                  active={false}
                  href={`/docs/${organisationSlug}/${organisationId}`}
                  icon={BookOpen}
                  label="Documentation"
                  collapsed={collapsed}
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
          <PlatformNavLink item={item} collapsed={collapsed} />
        </span>
      ))}
    </nav>
  );
}

function OrgNavLink({
  item,
  organisationSlug,
  collapsed = false,
}: {
  item: OrgNavItem;
  organisationSlug: string;
  collapsed?: boolean;
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
      collapsed={collapsed}
    />
  );
}

function PlatformNavLink({ item, collapsed = false }: { item: NavItem; collapsed?: boolean }) {
  const pathname = usePathname();
  const active = pathname === item.href || pathname.startsWith(`${item.href}/`);

  return (
    <NavItemLink
      active={active}
      href={item.href}
      icon={item.icon}
      label={item.label}
      collapsed={collapsed}
    />
  );
}
