import { useNavigate } from '@tanstack/react-router';
import { MoreHorizontal, Eye, Edit, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { formatDateTime, formatRelativeTime, maskLicenseKey } from '@/lib/utils';
import { useRevokeLicense } from '@/api/licenses';
import type { License } from '@/types/license';

interface LicenseTableProps {
  data: License[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  total: number;
  onPageChange: (page: number) => void;
  onEdit: (license: License) => void;
}

export function LicenseTable({ data, isLoading, page, totalPages, total, onPageChange, onEdit }: LicenseTableProps) {
  const navigate = useNavigate();
  const revoke = useRevokeLicense();
  const [revokeTarget, setRevokeTarget] = useState<License | null>(null);
  const [openMenu, setOpenMenu] = useState<string | null>(null);

  const columns: Column<License>[] = [
    {
      key: 'key',
      header: 'License Key',
      cell: (row) => (
        <span className="font-mono text-xs text-brand-400">{maskLicenseKey(row.key)}</span>
      ),
    },
    {
      key: 'customer',
      header: 'Customer',
      cell: (row) => <span className="text-slate-200">{row.customerName}</span>,
    },
    {
      key: 'edition',
      header: 'Edition',
      cell: (row) => (
        <span className="text-xs font-medium text-slate-300 uppercase">{row.edition}</span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      key: 'devices',
      header: 'Devices',
      cell: (row) => (
        <span className="text-slate-300">
          {row.activeDevices}/{row.maxDevices}
        </span>
      ),
    },
    {
      key: 'expires',
      header: 'Expires',
      cell: (row) => row.expiresAt ? (
        <span className="text-slate-400 text-xs">{formatDateTime(row.expiresAt)}</span>
      ) : (
        <span className="text-slate-500 text-xs">Never</span>
      ),
    },
    {
      key: 'heartbeat',
      header: 'Last Seen',
      cell: (row) => row.lastHeartbeatAt ? (
        <span className="text-slate-400 text-xs">{formatRelativeTime(row.lastHeartbeatAt)}</span>
      ) : (
        <span className="text-slate-500 text-xs">—</span>
      ),
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
            aria-label="Actions"
          >
            <MoreHorizontal className="w-4 h-4" />
          </button>
          {openMenu === row.id && (
            <div className="absolute right-0 top-full mt-1 w-44 bg-surface-card border border-surface-border rounded-lg shadow-xl z-20 py-1">
              <button
                onClick={() => { setOpenMenu(null); void navigate({ to: '/licenses/$licenseKey', params: { licenseKey: row.key } }); }}
                className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-slate-300 hover:bg-surface-elevated transition-colors min-h-[44px]"
              >
                <Eye className="w-4 h-4" /> View Detail
              </button>
              <button
                onClick={() => { setOpenMenu(null); onEdit(row); }}
                className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-slate-300 hover:bg-surface-elevated transition-colors min-h-[44px]"
              >
                <Edit className="w-4 h-4" /> Extend / Edit
              </button>
              <button
                onClick={() => { setOpenMenu(null); setRevokeTarget(row); }}
                className="flex items-center gap-2.5 w-full px-3 py-2.5 text-sm text-red-400 hover:bg-red-400/10 transition-colors min-h-[44px]"
              >
                <Trash2 className="w-4 h-4" /> Revoke
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
        onRowClick={(r) => void navigate({ to: '/licenses/$licenseKey', params: { licenseKey: r.key } })}
        emptyTitle="No licenses found"
        emptyDescription="No licenses match your current filters."
      />
      <ConfirmDialog
        open={!!revokeTarget}
        onClose={() => setRevokeTarget(null)}
        onConfirm={() => {
          if (revokeTarget) revoke.mutate(revokeTarget.key, { onSettled: () => setRevokeTarget(null) });
        }}
        title="Revoke License"
        description={`Are you sure you want to revoke license for "${revokeTarget?.customerName}"? All connected devices will lose access immediately.`}
        confirmLabel="Revoke License"
        variant="destructive"
        isLoading={revoke.isPending}
      />
    </>
  );
}
