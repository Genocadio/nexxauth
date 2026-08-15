"use client";

import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import type { OrganisationRoleResponse } from "@/types/api";

interface RoleCheckboxesProps {
  roles: OrganisationRoleResponse[];
  selected: number[];
  onChange: (roleIds: number[]) => void;
  disabled?: boolean;
}

export function RoleCheckboxes({ roles, selected, onChange, disabled }: RoleCheckboxesProps) {
  if (roles.length === 0) {
    return <p className="text-xs text-muted-foreground">No roles defined yet — create roles first.</p>;
  }
  const toggle = (roleId: number) => {
    onChange(selected.includes(roleId) ? selected.filter((id) => id !== roleId) : [...selected, roleId]);
  };
  return (
    <div className="space-y-2">
      {roles.map((role) => {
        const checked = selected.includes(role.id);
        return (
          <div key={role.id} className="flex items-center gap-2">
            <Checkbox
              id={`role-${role.id}`}
              checked={checked}
              disabled={disabled}
              onCheckedChange={() => toggle(role.id)}
            />
            <Label htmlFor={`role-${role.id}`} className="text-sm font-normal">
              {role.name}
            </Label>
          </div>
        );
      })}
    </div>
  );
}
