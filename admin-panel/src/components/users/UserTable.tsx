import { MoreHorizontal, Edit, UserX } from 'lucide-react';
import { useState } from 'react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { formatRelativeTime } from '@/lib/utils';
import { useDeactivateUser } from '@/api/users';
import type { AdminUser } from '@/types/user';

const ROLE_LABELS = { ADMIN: 'Admin', SUPPORT: 'Support', VIEWER: 'Viewer' };
const ROLE_COLORS = {
  ADMIN: 'text-red-400 bg-red-400/10 border border-red-400/20',
  SUPPORT: 'text-amber-400 bg-amber-400/10 border border-amber-400/20',
  VIEWER: 'text-slate-400 bg-slate-400/10 border border-slate-400/20',
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
  const [deactivateTarget, setDeactivateTarget] = useState<AdminUser | null>(null);
  const [openMenu, setOpenMenu] = useState<string | null>(null);

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
      key: 'store',
      header: 'Store',
      cell: (row) => <span className="text-slate-400 text-xs">{row.storeName ?? '—'}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      key: 'lastLogin',
      header: 'Last Login',
      cell: (row) => row.lastLoginAt
        ? <span className="text-slate-400 text-xs">{formatRelativeTime(row.lastLoginAt)}</span>
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
            <div className="absolute right-0 top-full mt-1 w-40 bg-surface-card border border-surface-border rounded-lg shadow-xl z-20 py-1">
              <button
                onClick={() => { setOpenMenu(null); onEdit(row); }}
                className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-slate-300 hover:bg-surface-elevated transition-colors min-h-[44px]"
              >
                <Edit className="w-4 h-4" /> Edit Role
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
