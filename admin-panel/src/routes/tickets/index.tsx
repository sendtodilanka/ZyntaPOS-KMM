import { createFileRoute, redirect } from '@tanstack/react-router';
import { useState } from 'react';
import { Plus } from 'lucide-react';
import { TicketTable } from '@/components/tickets/TicketTable';
import { TicketCreateModal } from '@/components/tickets/TicketCreateModal';
import { SearchInput } from '@/components/shared/SearchInput';
import { useTickets } from '@/api/tickets';
import { useAuth } from '@/hooks/use-auth';
import { useDebounce } from '@/hooks/use-debounce';
import type { TicketStatus, TicketPriority, TicketCategory } from '@/types/ticket';

export const Route = createFileRoute('/tickets/')({
  component: TicketsPage,
});

function TicketsPage() {
  const { hasPermission } = useAuth();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<TicketStatus | ''>('');
  const [priorityFilter, setPriorityFilter] = useState<TicketPriority | ''>('');
  const [categoryFilter, setCategoryFilter] = useState<TicketCategory | ''>('');
  const [createOpen, setCreateOpen] = useState(false);

  const debouncedSearch = useDebounce(search, 300);

  const { data, isLoading } = useTickets({
    page,
    size: 20,
    search: debouncedSearch || undefined,
    status: statusFilter || undefined,
    priority: priorityFilter || undefined,
    category: categoryFilter || undefined,
  });

  const totalPages = data ? Math.ceil(data.total / 20) : 1;

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Support Tickets</h1>
          <p className="panel-subtitle">{data?.total ?? 0} tickets total</p>
        </div>
        {hasPermission('tickets:create') && (
          <button
            onClick={() => setCreateOpen(true)}
            className="flex items-center gap-2 px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white text-sm font-medium rounded-lg transition-colors min-h-[44px]"
          >
            <Plus className="w-4 h-4" />
            <span>New Ticket</span>
          </button>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <SearchInput
          value={search}
          onChange={(v) => { setSearch(v); setPage(0); }}
          placeholder="Search tickets…"
          className="flex-1 min-w-[200px] max-w-sm"
        />
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value as TicketStatus | ''); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[140px]"
        >
          <option value="">All Statuses</option>
          <option value="OPEN">Open</option>
          <option value="ASSIGNED">Assigned</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="PENDING_CUSTOMER">Pending Customer</option>
          <option value="RESOLVED">Resolved</option>
          <option value="CLOSED">Closed</option>
        </select>
        <select
          value={priorityFilter}
          onChange={(e) => { setPriorityFilter(e.target.value as TicketPriority | ''); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[130px]"
        >
          <option value="">All Priorities</option>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
          <option value="CRITICAL">Critical</option>
        </select>
        <select
          value={categoryFilter}
          onChange={(e) => { setCategoryFilter(e.target.value as TicketCategory | ''); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[130px]"
        >
          <option value="">All Categories</option>
          <option value="HARDWARE">Hardware</option>
          <option value="SOFTWARE">Software</option>
          <option value="SYNC">Sync</option>
          <option value="BILLING">Billing</option>
          <option value="OTHER">Other</option>
        </select>
      </div>

      <TicketTable
        data={data?.items ?? []}
        isLoading={isLoading}
        page={page}
        totalPages={totalPages}
        total={data?.total ?? 0}
        onPageChange={setPage}
      />

      <TicketCreateModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  );
}
