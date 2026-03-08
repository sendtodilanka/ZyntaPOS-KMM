import { Shield } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AdminRole } from '@/types/user';

const ROLES: { value: AdminRole; label: string; description: string; color: string }[] = [
  { value: 'ADMIN',    label: 'Admin',    description: 'Full access to all panel features',              color: 'border-red-500/40 bg-red-500/10 text-red-400' },
  { value: 'OPERATOR', label: 'Operator', description: 'Store ops, sync, tickets, diagnostics',          color: 'border-yellow-500/40 bg-yellow-500/10 text-yellow-400' },
  { value: 'FINANCE',  label: 'Finance',  description: 'Financial reports and license exports',          color: 'border-green-500/40 bg-green-500/10 text-green-400' },
  { value: 'AUDITOR',  label: 'Auditor',  description: 'Read-only audit log and reports',                color: 'border-blue-500/40 bg-blue-500/10 text-blue-400' },
  { value: 'HELPDESK', label: 'Helpdesk', description: 'Support tickets and store diagnostics',          color: 'border-slate-500/40 bg-slate-500/10 text-slate-400' },
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
