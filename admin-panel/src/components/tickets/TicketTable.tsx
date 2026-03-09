import { useState, useEffect } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { AlertTriangle, Clock } from 'lucide-react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { TicketStatusBadge, TicketPriorityBadge } from './TicketStatusBadge';
import { useTimezone } from '@/hooks/use-timezone';
import type { Ticket } from '@/types/ticket';

interface TicketTableProps {
  data: Ticket[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  total: number;
  onPageChange: (page: number) => void;
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

export function TicketTable({ data, isLoading, page, totalPages, total, onPageChange }: TicketTableProps) {
  const navigate = useNavigate();
  const { formatRelative } = useTimezone();
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(id);
  }, []);

  const columns: Column<Ticket>[] = [
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
  );
}
