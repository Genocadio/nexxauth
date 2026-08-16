import { ConsoleShell, RequirePlatformAuth } from "@/components/layout/console-shell";
import { BackendOriginProvider } from "@/lib/backend-url";

// Read BACKEND_PUBLIC_URL at runtime (not baked at build time) so the
// dashboards can always derive the clean platform/project URL.
export const dynamic = "force-dynamic";

export default function ConsoleLayout({ children }: { children: React.ReactNode }) {
  return (
    <BackendOriginProvider value={process.env.BACKEND_PUBLIC_URL ?? ""}>
      <RequirePlatformAuth>
        <ConsoleShell>{children}</ConsoleShell>
      </RequirePlatformAuth>
    </BackendOriginProvider>
  );
}