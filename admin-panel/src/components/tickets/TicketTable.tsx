import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { AlertTriangle, Clock, Download, UserPlus, CheckCircle2 } from 'lucide-react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { TicketStatusBadge, TicketPriorityBadge } from './TicketStatusBadge';
import { BulkAssignModal } from './BulkAssignModal';
import { BulkResolveModal } from './BulkResolveModal';
import { useTimezone } from '@/hooks/use-timezone';
import { useAuth } from '@/hooks/use-auth';
import { API_BASE_URL } from '@/lib/constants';
import type { Ticket, TicketFilter } from '@/types/ticket';
import { buildQueryString } from '@/lib/utils';

interface TicketTableProps {
  data: Ticket[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  total: number;
  onPageChange: (page: number) => void;
  filter?: TicketFilter;
}

function SlaIndicator({ slaDueAt, slaBreached, status, now }: { slaDueAt: number | null; slaBreached: boolean; status: string; now: number }) {
  if (status === 'RESOLVED' || status === 'CLOSED') return null;
  if (!slaDueAt) return null;

  if (slaBreached) {
    return (
      <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-red-400">
        <AlertTriangle className="w-3 h-3" /> SLA Breached
      </span>
    );
  }

  const remaining = slaDueAt - now;
  if (remaining <= 0) {
    return (
      <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-red-400">
        <AlertTriangle className="w-3 h-3" /> Overdue
      </span>
    );
  }

  const hours = Math.floor(remaining / 3_600_000);
  const mins = Math.floor((remaining % 3_600_000) / 60_000);
  const label = hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
  const isWarning = remaining < 3_600_000 * 2;

  return (
    <span className={`inline-flex items-center gap-1 text-[11px] font-medium ${isWarning ? 'text-amber-400' : 'text-slate-400'}`}>
      <Clock className="w-3 h-3" /> {label}
    </span>
  );
}

export function TicketTable({ data, isLoading, page, totalPages, total, onPageChange, filter }: TicketTableProps) {
  const navigate = useNavigate();
  const { formatRelative } = useTimezone();
  const { hasPermission } = useAuth();
  const [now, setNow] = useState(Date.now);
  const [selectedMap, setSelectedMap] = useState<Record<string, boolean>>({});
  const [bulkAssignOpen, setBulkAssignOpen] = useState(false);
  const [bulkResolveOpen, setBulkResolveOpen] = useState(false);

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(id);
  }, []);

  // Derive valid selection from current data IDs — automatically clears on page/data change
  const dataIds = useMemo(() => new Set(data.map((t) => t.id)), [data]);
  const selected = useMemo(() => {
    const valid = new Set<string>();
    for (const id of Object.keys(selectedMap)) {
      if (selectedMap[id] && dataIds.has(id)) valid.add(id);
    }
    return valid;
  }, [selectedMap, dataIds]);

  const toggleSelect = (id: string) => {
    setSelectedMap((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const toggleAll = () => {
    if (selected.size === data.length) {
      setSelectedMap({});
    } else {
      const next: Record<string, boolean> = {};
      for (const t of data) next[t.id] = true;
      setSelectedMap(next);
    }
  };

  const selectedIds = Array.from(selected);
  const canAssign = hasPermission('tickets:assign');
  const canResolve = hasPermission('tickets:resolve');

  const handleExport = () => {
    const qs = buildQueryString({
      status: filter?.status,
      priority: filter?.priority,
      category: filter?.category,
      assignedTo: filter?.assignedTo,
      storeId: filter?.storeId,
      search: filter?.search,
    });
    window.open(`${API_BASE_URL}/admin/tickets/export${qs ? `?${qs}` : ''}`, '_blank');
  };

  const columns: Column<Ticket>[] = [
    {
      key: 'select',
      header: () => (
        <input
          type="checkbox"
          checked={data.length > 0 && selected.size === data.length}
          onChange={toggleAll}
          className="rounded border-surface-border bg-surface-elevated"
          aria-label="Select all"
        />
      ),
      cell: (row) => (
        <input
          type="checkbox"
          checked={selected.has(row.id)}
          onChange={(e) => { e.stopPropagation(); toggleSelect(row.id); }}
          onClick={(e) => e.stopPropagation()}
          className="rounded border-surface-border bg-surface-elevated"
          aria-label={`Select ${row.ticketNumber}`}
        />
      ),
      headerClassName: 'w-10',
    },
    {
      key: 'number',
      header: 'Ticket #',
      cell: (row) => (
        <span className="font-mono text-xs text-brand-400 font-semibold">{row.ticketNumber}</span>
      ),
      headerClassName: 'w-36',
    },
    {
      key: 'title',
      header: 'Title',
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-slate-200 text-sm font-medium truncate max-w-xs">{row.title}</p>
          <p className="text-slate-500 text-xs truncate max-w-xs">{row.customerName}</p>
        </div>
      ),
    },
    {
      key: 'priority',
      header: 'Priority',
      cell: (row) => <TicketPriorityBadge priority={row.priority} />,
      headerClassName: 'w-24',
    },
    {
      key: 'status',
      header: 'Status',
      cell: (row) => <TicketStatusBadge status={row.status} />,
      headerClassName: 'w-36',
    },
    {
      key: 'assignedTo',
      header: 'Assigned To',
      cell: (row) => row.assignedToName
        ? <span className="text-slate-300 text-sm">{row.assignedToName}</span>
        : <span className="text-slate-500 text-xs">Unassigned</span>,
    },
    {
      key: 'sla',
      header: 'SLA',
      cell: (row) => (
        <SlaIndicator slaDueAt={row.slaDueAt} slaBreached={row.slaBreached} status={row.status} now={now} />
      ),
      headerClassName: 'w-32',
    },
    {
      key: 'created',
      header: 'Created',
      cell: (row) => <span className="text-slate-400 text-xs">{formatRelative(row.createdAt)}</span>,
      headerClassName: 'w-32',
    },
  ];

  return (
    <div className="space-y-3">
      {/* Bulk action toolbar */}
      {selected.size > 0 && (
        <div className="flex items-center gap-3 px-4 py-2.5 bg-brand-500/10 border border-brand-500/20 rounded-lg">
          <span className="text-sm text-brand-400 font-medium">{selected.size} selected</span>
          <div className="flex items-center gap-2 ml-auto">
            {canAssign && (
              <button
                onClick={() => setBulkAssignOpen(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-300 border border-surface-border rounded-lg hover:bg-surface-elevated transition-colors"
              >
                <UserPlus className="w-3.5 h-3.5" /> Assign
              </button>
            )}
            {canResolve && (
              <button
                onClick={() => setBulkResolveOpen(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-emerald-400 border border-emerald-500/30 rounded-lg hover:bg-emerald-500/10 transition-colors"
              >
                <CheckCircle2 className="w-3.5 h-3.5" /> Resolve
              </button>
            )}
            <button
              onClick={handleExport}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-300 border border-surface-border rounded-lg hover:bg-surface-elevated transition-colors"
            >
              <Download className="w-3.5 h-3.5" /> Export CSV
            </button>
          </div>
        </div>
      )}

      <DataTable
        columns={columns}
        data={data}
        isLoading={isLoading}
        page={page}
        totalPages={totalPages}
        total={total}
        onPageChange={onPageChange}
        rowKey={(r) => r.id}
        onRowClick={(row) => navigate({ to: '/tickets/$ticketId', params: { ticketId: row.id } })}
        emptyTitle="No tickets found"
        emptyDescription="No support tickets match your current filters."
      />

      <BulkAssignModal ticketIds={selectedIds} open={bulkAssignOpen} onClose={() => { setBulkAssignOpen(false); setSelectedMap({}); }} />
      <BulkResolveModal ticketIds={selectedIds} open={bulkResolveOpen} onClose={() => { setBulkResolveOpen(false); setSelectedMap({}); }} />
    </div>
  );
}
