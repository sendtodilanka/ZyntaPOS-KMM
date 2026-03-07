import { useState } from 'react';
import { CheckCircle, XCircle, Eye } from 'lucide-react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { AuditDetailModal } from './AuditDetailModal';
import { formatDateTime } from '@/lib/utils';
import type { AuditEntry } from '@/types/audit';

const CATEGORY_COLORS: Record<string, string> = {
  AUTH: 'text-purple-400 bg-purple-400/10',
  LICENSE: 'text-brand-400 bg-brand-400/10',
  INVENTORY: 'text-emerald-400 bg-emerald-400/10',
  ORDER: 'text-amber-400 bg-amber-400/10',
  PAYMENT: 'text-green-400 bg-green-400/10',
  USER: 'text-red-400 bg-red-400/10',
  SETTINGS: 'text-slate-400 bg-slate-400/10',
  SYNC: 'text-blue-400 bg-blue-400/10',
  SYSTEM: 'text-orange-400 bg-orange-400/10',
};

interface AuditLogTableProps {
  data: AuditEntry[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  total: number;
  onPageChange: (page: number) => void;
}

export function AuditLogTable({ data, isLoading, page, totalPages, total, onPageChange }: AuditLogTableProps) {
  const [selectedEntry, setSelectedEntry] = useState<AuditEntry | null>(null);

  const columns: Column<AuditEntry>[] = [
    {
      key: 'timestamp',
      header: 'Timestamp',
      cell: (row) => <span className="text-xs text-slate-400 whitespace-nowrap">{formatDateTime(row.createdAt)}</span>,
    },
    {
      key: 'event',
      header: 'Event',
      cell: (row) => (
        <div>
          <p className="text-xs font-medium text-slate-200">{row.eventType}</p>
          <span className={`inline-block mt-0.5 px-1.5 py-0.5 rounded text-[10px] font-semibold ${CATEGORY_COLORS[row.category] ?? 'text-slate-400 bg-slate-400/10'}`}>
            {row.category}
          </span>
        </div>
      ),
    },
    {
      key: 'user',
      header: 'User',
      cell: (row) => <span className="text-xs text-slate-300">{row.userName ?? '—'}</span>,
    },
    {
      key: 'store',
      header: 'Store',
      cell: (row) => <span className="text-xs text-slate-400">{row.storeName ?? '—'}</span>,
    },
    {
      key: 'entity',
      header: 'Entity',
      cell: (row) => row.entityType
        ? <span className="text-xs text-slate-400">{row.entityType}</span>
        : <span className="text-slate-600 text-xs">—</span>,
    },
    {
      key: 'result',
      header: 'Result',
      cell: (row) => row.success
        ? <CheckCircle className="w-4 h-4 text-emerald-400" />
        : <XCircle className="w-4 h-4 text-red-400" />,
    },
    {
      key: 'actions',
      header: '',
      headerClassName: 'w-12',
      className: 'w-12',
      cell: (row) => (
        <button
          onClick={() => setSelectedEntry(row)}
          className="p-1.5 rounded-md text-slate-400 hover:text-brand-400 hover:bg-brand-400/10 transition-colors min-w-[36px] min-h-[36px] flex items-center justify-center"
        >
          <Eye className="w-4 h-4" />
        </button>
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
        onRowClick={(r) => setSelectedEntry(r)}
        emptyTitle="No audit entries"
        emptyDescription="No audit logs match your current filters."
      />
      <AuditDetailModal entry={selectedEntry} onClose={() => setSelectedEntry(null)} />
    </>
  );
}
