"use client";

import { Suspense } from "react";
import {
  AppWindow,
  BookOpen,
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
  { tab: "fields", label: "Fields", icon: Layers },
  { tab: "keys", label: "Keys", icon: KeyRound },
  { tab: "clients", label: "Clients", icon: AppWindow },
  { tab: "settings", label: "Settings", icon: Settings },
];

const itemClasses = (active: boolean, collapsed: boolean) =>
  cn(
    "flex h-9 items-center rounded-md text-sm font-medium transition-colors",
    collapsed ? "w-9 justify-center px-0" : "gap-3 px-3",
    active
      ? "bg-sidebar-accent text-sidebar-accent-foreground"
      : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-foreground",
  );

export function NavLink({ item, collapsed = false }: { item: NavItem; collapsed?: boolean }) {
  const pathname = usePathname();
  const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
  const Icon = item.icon;
  return (
    <Link href={item.href} title={collapsed ? item.label : undefined} className={itemClasses(active, collapsed)}>
      <Icon className="h-4 w-4 shrink-0" />
      {!collapsed ? item.label : null}
    </Link>
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
  const Icon = item.icon;
  return (
    <Link href={href} title={collapsed ? item.label : undefined} className={itemClasses(active, collapsed)}>
      <Icon className="h-4 w-4 shrink-0" />
      {!collapsed ? item.label : null}
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
      <nav className="flex flex-col gap-1 px-3 py-2">
        <Suspense fallback={null}>
          {ORG_NAV.map((item) => (
            <span key={item.tab} onClick={onNavigate}>
              <OrgNavLink item={item} organisationSlug={organisationSlug ?? ""} collapsed={collapsed} />
            </span>
          ))}
          {organisationId && (
            <span onClick={onNavigate}>
              <Link
                href={`/docs/${organisationSlug}/${organisationId}`}
                title={collapsed ? "Documentation" : undefined}
                className={itemClasses(false, collapsed)}
              >
                <BookOpen className="h-4 w-4 shrink-0" />
                {!collapsed ? "Documentation" : null}
              </Link>
            </span>
          )}
        </Suspense>
      </nav>
    );
  }

  return (
    <nav className="flex flex-col gap-1 px-3 py-2">
      {CONSOLE_NAV.map((item) => (
        <span key={item.href} onClick={onNavigate}>
          <NavLink item={item} collapsed={collapsed} />
        </span>
      ))}
    </nav>
  );
}
