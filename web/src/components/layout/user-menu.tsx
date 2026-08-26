"use client";

import { LogOut, Settings, UserCircle2 } from "lucide-react";
import Link from "next/link";
import { InitialsAvatar } from "@/components/shared/initials-avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { usePlatformLogout } from "@/hooks/use-auth";
import { selectPlatformSession, useAppSelector } from "@/store/store";
import { fullName } from "@/types/api";

export function UserMenu() {
  const user = useAppSelector(selectPlatformSession)?.user ?? null;
  const logout = usePlatformLogout();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="h-auto gap-2 px-2 py-1.5 transition-opacity hover:opacity-80"
        >
          <InitialsAvatar name={user ? fullName(user) : "?"} className="h-8 w-8" />
          <span className="hidden text-sm font-medium sm:block">{user?.firstName ?? "…"}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="flex items-center gap-2">
          <UserCircle2 className="h-4 w-4 shrink-0 text-muted-foreground" />
          <span className="min-w-0">
            <span className="block truncate">{user ? fullName(user) : "…"}</span>
            <span className="block truncate text-xs font-normal text-muted-foreground">
              {user?.email}
            </span>
          </span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href="/console/profile">
            <Settings /> Profile &amp; security
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onClick={() => logout.mutate()}>
          <LogOut /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
