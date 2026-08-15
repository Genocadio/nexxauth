"use client";

import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { ALL_PERMISSIONS, PERMISSION_META, type Permission } from "@/types/enums";

interface PermissionCheckboxesProps {
  selected: Permission[];
  onChange: (permissions: Permission[]) => void;
}

export function PermissionCheckboxes({ selected, onChange }: PermissionCheckboxesProps) {
  const toggle = (permission: Permission) => {
    onChange(
      selected.includes(permission)
        ? selected.filter((p) => p !== permission)
        : [...selected, permission],
    );
  };
  return (
    <div className="space-y-2">
      {ALL_PERMISSIONS.map((permission) => {
        const meta = PERMISSION_META[permission];
        const checked = selected.includes(permission);
        return (
          <div key={permission} className="flex items-start gap-2 rounded-md px-1 py-0.5">
            <Checkbox
              id={`perm-${permission}`}
              checked={checked}
              onCheckedChange={() => toggle(permission)}
              className="mt-0.5"
            />
            <Label htmlFor={`perm-${permission}`} className="text-sm font-normal">
              <span className="block font-medium">{meta.label}</span>
              <span className="block text-xs text-muted-foreground">{meta.description}</span>
            </Label>
          </div>
        );
      })}
    </div>
  );
}
