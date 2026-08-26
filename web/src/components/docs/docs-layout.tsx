"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { useOrganisations } from "@/hooks/queries";
import {
  BookOpen,
  Key,
  Users,
  Shield,
  Code,
  Zap,
  Settings,
  ArrowLeft,
} from "lucide-react";

interface DocsLayoutProps {
  children: React.ReactNode;
  platformSlug: string;
  organisationId: string;
}

const NAV_SECTIONS = [
  {
    title: "Getting Started",
    items: [
      {
        label: "Overview",
        href: "/docs",
        icon: BookOpen,
      },
      {
        label: "Quick Start",
        href: "/docs/quickstart",
        icon: Zap,
      },
    ],
  },
  {
    title: "Authentication",
    items: [
      {
        label: "Register",
        href: "/docs/auth/register",
        icon: Key,
      },
      {
        label: "Login",
        href: "/docs/auth/login",
        icon: Key,
      },
      {
        label: "Refresh Token",
        href: "/docs/auth/refresh",
        icon: Key,
      },
      {
        label: "Logout",
        href: "/docs/auth/logout",
        icon: Key,
      },
    ],
  },
  {
    title: "Client Types",
    items: [
      {
        label: "Overview",
        href: "/docs/clients",
        icon: Settings,
      },
      {
        label: "WEB Client",
        href: "/docs/clients/web",
        icon: Settings,
      },
      {
        label: "SERVER Client",
        href: "/docs/clients/server",
        icon: Settings,
      },
      {
        label: "ANDROID / IOS",
        href: "/docs/clients/mobile",
        icon: Settings,
      },
    ],
  },
  {
    title: "User Management",
    items: [
      {
        label: "Users",
        href: "/docs/users",
        icon: Users,
      },
      {
        label: "Roles",
        href: "/docs/users/roles",
        icon: Shield,
      },
      {
        label: "Custom Fields",
        href: "/docs/users/fields",
        icon: Shield,
      },
    ],
  },
  {
    title: "Tokens",
    items: [
      {
        label: "Verification",
        href: "/docs/tokens/verification",
        icon: Shield,
      },
      {
        label: "Anatomy",
        href: "/docs/tokens/anatomy",
        icon: Shield,
      },
    ],
  },
  {
    title: "API Reference",
    items: [
      {
        label: "All Endpoints",
        href: "/docs/api",
        icon: Code,
      },
    ],
  },
];

export function DocsLayout({ children, platformSlug, organisationId }: DocsLayoutProps) {
  const pathname = usePathname();
  const basePath = `/docs/${platformSlug}/${organisationId}`;
  const organisations = useOrganisations();
  const org = organisations.data?.find((o) => o.id === Number(organisationId));
  const consoleHref = org
    ? `/console/organisations/${org.slug}`
    : "/console/organisations";

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4">
          <div className="flex items-center gap-4">
            <Link
              href={consoleHref}
              className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" />
              {org?.name ?? "Console"}
            </Link>
            <span className="text-muted-foreground">/</span>
            <span className="text-sm font-medium">Documentation</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground">
              {platformSlug} / {organisationId}
            </span>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-7xl">
        <div className="flex">
          <aside className="hidden w-64 shrink-0 border-r lg:block lg:sticky lg:top-14 lg:h-[calc(100vh-3.5rem)] lg:overflow-y-auto lg:py-6">
            <nav className="space-y-6 px-4">
              {NAV_SECTIONS.map((section) => (
                <div key={section.title}>
                  <h4 className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    {section.title}
                  </h4>
                  <ul className="space-y-1">
                    {section.items.map((item) => {
                      // NAV_SECTION hrefs keep their full /docs prefix; strip it
                      // so they join the basePath (/docs/{platform}/{org}) once.
                      const href = `${basePath}${item.href.replace(/^\/docs/, "")}`;
                      const isActive = pathname === href;
                      return (
                        <li key={item.href}>
                          <Link
                            href={href}
                            className={cn(
                              "flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors",
                              isActive
                                ? "bg-primary/10 text-primary font-medium"
                                : "text-muted-foreground hover:text-foreground hover:bg-muted"
                            )}
                          >
                            <item.icon className="h-4 w-4" />
                            {item.label}
                          </Link>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              ))}
            </nav>
          </aside>

          <main className="flex-1 min-w-0 py-8 px-6 lg:px-10">
            {children}
          </main>
        </div>
      </div>
    </div>
  );
}
