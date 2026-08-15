import { ConsoleShell, RequirePlatformAuth } from "@/components/layout/console-shell";

export default function ConsoleLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequirePlatformAuth>
      <ConsoleShell>{children}</ConsoleShell>
    </RequirePlatformAuth>
  );
}
