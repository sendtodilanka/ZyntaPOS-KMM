import { MoreHorizontal, Edit, UserX, ShieldCheck, ShieldOff, LogOut } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { useDeactivateUser, useRevokeSessions } from '@/api/users';
import { useTimezone } from '@/hooks/use-timezone';
import type { AdminUser, AdminRole } from '@/types/user';

const ROLE_LABELS: Record<AdminRole, string> = {
  ADMIN:    'Admin',
  OPERATOR: 'Operator',
  FINANCE:  'Finance',
  AUDITOR:  'Auditor',
  HELPDESK: 'Helpdesk',
};
const ROLE_COLORS: Record<AdminRole, string> = {
  ADMIN:    'text-red-400 bg-red-400/10 border border-red-400/20',
  OPERATOR: 'text-yellow-400 bg-yellow-400/10 border border-yellow-400/20',
  FINANCE:  'text-green-400 bg-green-400/10 border border-green-400/20',
  AUDITOR:  'text-blue-400 bg-blue-400/10 border border-blue-400/20',
  HELPDESK: 'text-slate-400 bg-slate-400/10 border border-slate-400/20',
};


interface UserTableProps {
  data: AdminUser[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  total: number;
  onPageChange: (page: number) => void;
  onEdit: (user: AdminUser) => void;
}

export function UserTable({ data, isLoading, page, totalPages, total, onPageChange, onEdit }: UserTableProps) {
  const deactivate = useDeactivateUser();
  const revokeSessions = useRevokeSessions();
  const { formatRelative } = useTimezone();
  const [deactivateTarget, setDeactivateTarget] = useState<AdminUser | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<AdminUser | null>(null);
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpenMenu(null);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const columns: Column<AdminUser>[] = [
    {
      key: 'user',
      header: 'User',
      cell: (row) => (
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-brand-500/20 flex items-center justify-center text-xs font-semibold text-brand-400 flex-shrink-0">
            {row.name.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)}
          </div>
          <div className="min-w-0">
            <p className="text-slate-200 font-medium text-sm truncate">{row.name}</p>
            <p className="text-slate-500 text-xs truncate">{row.email}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      cell: (row) => (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold uppercase ${ROLE_COLORS[row.role]}`}>
          {ROLE_LABELS[row.role]}
        </span>
      ),
    },
    {
      key: 'mfa',
      header: 'MFA',
      cell: (row) => row.mfaEnabled
        ? <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-green-400"><ShieldCheck className="w-3.5 h-3.5" /> On</span>
        : <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-slate-500"><ShieldOff className="w-3.5 h-3.5" /> Off</span>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (row) => (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${row.isActive ? 'text-green-400 bg-green-400/10' : 'text-slate-500 bg-slate-500/10'}`}>
          {row.isActive ? 'Active' : 'Inactive'}
        </span>
      ),
    },
    {
      key: 'lastLogin',
      header: 'Last Login',
      cell: (row) => row.lastLoginAt
        ? <span className="text-slate-400 text-xs">{formatRelative(row.lastLoginAt)}</span>
        : <span className="text-slate-500 text-xs">Never</span>,
    },
    {
      key: 'actions',
      header: '',
      headerClassName: 'w-12',
      className: 'w-12',
      cell: (row) => (
        <div className="relative">
          <button
            onClick={() => setOpenMenu(openMenu === row.id ? null : row.id)}
            className="p-1.5 rounded-md text-slate-400 hover:text-slate-100 hover:bg-surface-elevated transition-colors min-w-[36px] min-h-[36px] flex items-center justify-center"
          >
            <MoreHorizontal className="w-4 h-4" />
          </button>
          {openMenu === row.id && (
            <div ref={menuRef} className="absolute right-0 top-full mt-1 w-48 bg-surface-card border border-surface-border rounded-lg shadow-xl z-20 py-1">
              <button
                onClick={() => { setOpenMenu(null); onEdit(row); }}
                className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-slate-300 hover:bg-surface-elevated transition-colors min-h-[44px]"
              >
                <Edit className="w-4 h-4" /> Edit Role
              </button>
              <button
                onClick={() => { setOpenMenu(null); setRevokeTarget(row); }}
                className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-yellow-400 hover:bg-yellow-400/10 transition-colors min-h-[44px]"
              >
                <LogOut className="w-4 h-4" /> Revoke Sessions
              </button>
              <button
                onClick={() => { setOpenMenu(null); setDeactivateTarget(row); }}
                className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-red-400 hover:bg-red-400/10 transition-colors min-h-[44px]"
              >
                <UserX className="w-4 h-4" /> Deactivate
              </button>
            </div>
          )}
        </div>
      ),
    },
  ];

  return (
    <>
      <DataTable
        columns={columns}
        data={data}
        isLoading={isLoading}
        page={page}
        totalPages={totalPages}
        total={total}
        onPageChange={onPageChange}
        rowKey={(r) => r.id}
        emptyTitle="No users found"
        emptyDescription="No admin users match your filters."
      />
      <ConfirmDialog
        open={!!revokeTarget}
        onClose={() => setRevokeTarget(null)}
        onConfirm={() => {
          if (revokeTarget) revokeSessions.mutate(revokeTarget.id, { onSettled: () => setRevokeTarget(null) });
        }}
        title="Revoke All Sessions"
        description={`Force "${revokeTarget?.name}" to log in again by revoking all their active sessions?`}
        confirmLabel="Revoke Sessions"
        variant="destructive"
        isLoading={revokeSessions.isPending}
      />
      <ConfirmDialog
        open={!!deactivateTarget}
        onClose={() => setDeactivateTarget(null)}
        onConfirm={() => {
          if (deactivateTarget) deactivate.mutate(deactivateTarget.id, { onSettled: () => setDeactivateTarget(null) });
        }}
        title="Deactivate User"
        description={`Deactivate "${deactivateTarget?.name}"? They will lose access to the admin panel immediately.`}
        confirmLabel="Deactivate"
        variant="destructive"
        isLoading={deactivate.isPending}
      />
    </>
  );
}
