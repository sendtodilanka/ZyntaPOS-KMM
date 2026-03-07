import { Shield } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AdminRole } from '@/types/user';

const ROLES: { value: AdminRole; label: string; description: string; color: string }[] = [
  { value: 'SUPER_ADMIN', label: 'Super Admin', description: 'Full access to all panel features', color: 'border-red-500/40 bg-red-500/10 text-red-400' },
  { value: 'SUPPORT', label: 'Support', description: 'Read-only + force sync + audit logs', color: 'border-amber-500/40 bg-amber-500/10 text-amber-400' },
  { value: 'VIEWER', label: 'Viewer', description: 'Read-only access to dashboards', color: 'border-slate-500/40 bg-slate-500/10 text-slate-400' },
];

interface RoleAssignmentProps {
  value: AdminRole;
  onChange: (role: AdminRole) => void;
}

export function RoleAssignment({ value, onChange }: RoleAssignmentProps) {
  return (
    <div className="space-y-2">
      {ROLES.map((role) => (
        <button
          key={role.value}
          type="button"
          onClick={() => onChange(role.value)}
          className={cn(
            'w-full flex items-center gap-3 p-3 rounded-lg border text-left transition-colors min-h-[56px]',
            value === role.value
              ? role.color
              : 'border-surface-border bg-surface-elevated text-slate-400 hover:border-slate-500',
          )}
        >
          <Shield className="w-4 h-4 flex-shrink-0" />
          <div className="min-w-0">
            <p className="text-sm font-medium">{role.label}</p>
            <p className="text-xs opacity-70">{role.description}</p>
          </div>
        </button>
      ))}
    </div>
  );
}
