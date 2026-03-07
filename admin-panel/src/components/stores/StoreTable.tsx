import { useNavigate } from '@tanstack/react-router';
import { Eye } from 'lucide-react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { formatRelativeTime } from '@/lib/utils';
import type { Store } from '@/types/store';

interface StoreTableProps {
  data: Store[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  total: number;
  onPageChange: (page: number) => void;
}

export function StoreTable({ data, isLoading, page, totalPages, total, onPageChange }: StoreTableProps) {
  const navigate = useNavigate();

  const columns: Column<Store>[] = [
    {
      key: 'name',
      header: 'Store',
      cell: (row) => (
        <div>
          <p className="text-slate-100 font-medium text-sm">{row.name}</p>
          <p className="text-slate-500 text-xs">{row.location}</p>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (row) => <StatusBadge status={row.status} />,
    },
    {
      key: 'license',
      header: 'License',
      cell: (row) => (
        <span className="font-mono text-xs text-slate-400">{row.licenseKey.slice(0, 8)}…</span>
      ),
    },
    {
      key: 'edition',
      header: 'Edition',
      cell: (row) => (
        <span className="text-xs font-medium text-slate-400 uppercase">{row.edition}</span>
      ),
    },
    {
      key: 'users',
      header: 'Active Users',
      cell: (row) => <span className="text-slate-300">{row.activeUsers}</span>,
    },
    {
      key: 'lastSync',
      header: 'Last Sync',
      cell: (row) => row.lastSyncAt ? (
        <span className="text-slate-400 text-xs">{formatRelativeTime(row.lastSyncAt)}</span>
      ) : (
        <span className="text-slate-500 text-xs">Never</span>
      ),
    },
    {
      key: 'version',
      header: 'App Version',
      cell: (row) => <span className="text-slate-400 text-xs">{row.appVersion}</span>,
    },
    {
      key: 'actions',
      header: '',
      headerClassName: 'w-12',
      className: 'w-12',
      cell: (row) => (
        <button
          onClick={() => void navigate({ to: '/stores/$storeId', params: { storeId: row.id } })}
          className="p-1.5 rounded-md text-slate-400 hover:text-brand-400 hover:bg-brand-400/10 transition-colors min-w-[36px] min-h-[36px] flex items-center justify-center"
          aria-label="View store"
        >
          <Eye className="w-4 h-4" />
        </button>
      ),
    },
  ];

  return (
    <DataTable
      columns={columns}
      data={data}
      isLoading={isLoading}
      page={page}
      totalPages={totalPages}
      total={total}
      onPageChange={onPageChange}
      rowKey={(r) => r.id}
      onRowClick={(r) => void navigate({ to: '/stores/$storeId', params: { storeId: r.id } })}
      emptyTitle="No stores found"
      emptyDescription="No stores match your current filters."
    />
  );
}
