import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  ROLE_META,
  ROLE_TONE,
  USER_FIELD_TYPE_META,
  USER_FIELD_TYPE_TONE,
  type BadgeTone,
  type Role,
  type UserFieldType,
} from "@/types/enums";

const TONE_CLASSES: Record<BadgeTone, string> = {
  default: "",
  secondary: "",
  success: "border-transparent bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400",
  destructive: "",
  warning: "border-transparent bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400",
  outline: "",
};

interface ToneBadgeProps extends Omit<React.ComponentProps<"span">, "variant"> {
  tone: BadgeTone;
}

/** Badge with an app-level tone (success/warning/etc). */
export function ToneBadge({ tone, className, ...props }: ToneBadgeProps) {
  return <Badge variant="outline" className={cn(TONE_CLASSES[tone], className)} {...props} />;
}

export function EnabledBadge({ enabled }: { enabled: boolean }) {
  return enabled ? (
    <ToneBadge tone="success">Active</ToneBadge>
  ) : (
    <ToneBadge tone="secondary">Disabled</ToneBadge>
  );
}

export function RoleBadge({ role }: { role: Role }) {
  return <ToneBadge tone={ROLE_TONE[role]}>{ROLE_META[role].label}</ToneBadge>;
}

export function UserFieldTypeBadge({ fieldType }: { fieldType: UserFieldType }) {
  return <ToneBadge tone={USER_FIELD_TYPE_TONE[fieldType]}>{USER_FIELD_TYPE_META[fieldType].label}</ToneBadge>;
}

/** Comma-joined names of a user's roles. */
export function UserRoles({ roles }: { roles: { name: string }[] }) {
  if (roles.length === 0) return <span className="text-muted-foreground">No roles</span>;
  return <span>{roles.map((r) => r.name).join(", ")}</span>;
}
