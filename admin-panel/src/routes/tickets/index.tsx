import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Plus } from 'lucide-react';
import { TicketTable } from '@/components/tickets/TicketTable';
import { TicketCreateModal } from '@/components/tickets/TicketCreateModal';
import { SearchInput } from '@/components/shared/SearchInput';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { useTickets, useTicketMetrics } from '@/api/tickets';
import { useAuth } from '@/hooks/use-auth';
import { useDebounce } from '@/hooks/use-debounce';
import { TICKET_CATEGORY_TREE } from '@/types/ticket';
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
  const [searchBody, setSearchBody] = useState(false);
  const [createdAfter, setCreatedAfter] = useState('');
  const [createdBefore, setCreatedBefore] = useState('');

  const { data: metrics } = useTicketMetrics();
  const debouncedSearch = useDebounce(search, 300);

  const { data, isLoading, isError, refetch } = useTickets({
    page,
    size: 20,
    search: debouncedSearch || undefined,
    searchBody: searchBody || undefined,
    status: statusFilter || undefined,
    priority: priorityFilter || undefined,
    category: categoryFilter || undefined,
    createdAfter: createdAfter ? new Date(createdAfter).getTime() : undefined,
    createdBefore: createdBefore ? new Date(createdBefore + 'T23:59:59').getTime() : undefined,
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
            className="flex items-center gap-2 px-4 py-2 bg-brand-700 hover:bg-brand-800 text-white text-sm font-medium rounded-lg transition-colors min-h-[44px]"
          >
            <Plus className="w-4 h-4" />
            <span>New Ticket</span>
          </button>
        )}
      </div>

      {/* Metrics cards */}
      {metrics && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div className="bg-surface-card border border-surface-border rounded-xl p-4">
            <p className="text-xs text-slate-500 font-medium">Open</p>
            <p className="text-2xl font-bold text-slate-200 mt-1">{metrics.totalOpen}</p>
          </div>
          <div className="bg-surface-card border border-surface-border rounded-xl p-4">
            <p className="text-xs text-slate-500 font-medium">SLA Breached</p>
            <p className={`text-2xl font-bold mt-1 ${metrics.slaBreached > 0 ? 'text-red-400' : 'text-slate-200'}`}>
              {metrics.slaBreached}
            </p>
          </div>
          <div className="bg-surface-card border border-surface-border rounded-xl p-4">
            <p className="text-xs text-slate-500 font-medium">Avg Resolution</p>
            <p className="text-2xl font-bold text-slate-200 mt-1">
              {metrics.avgResolutionTimeMin > 0 ? `${metrics.avgResolutionTimeMin}m` : '—'}
            </p>
          </div>
          <div className="bg-surface-card border border-surface-border rounded-xl p-4">
            <p className="text-xs text-slate-500 font-medium">Resolved</p>
            <p className="text-2xl font-bold text-emerald-400 mt-1">{metrics.totalResolved}</p>
          </div>
        </div>
      )}

      <div className="flex flex-wrap gap-3 items-end">
        <div className="flex-1 min-w-[200px] max-w-sm space-y-1">
          <SearchInput
            value={search}
            onChange={(v) => { setSearch(v); setPage(0); }}
            placeholder="Search tickets…"
            className="w-full"
          />
          <label className="flex items-center gap-1.5 text-xs text-slate-400 cursor-pointer">
            <input
              type="checkbox"
              checked={searchBody}
              onChange={(e) => { setSearchBody(e.target.checked); setPage(0); }}
              className="rounded border-surface-border bg-surface-elevated"
            />
            Search description
          </label>
        </div>
        <select
          aria-label="Filter by status"
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
          aria-label="Filter by priority"
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
          aria-label="Filter by category"
          value={categoryFilter}
          onChange={(e) => { setCategoryFilter(e.target.value as TicketCategory | ''); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[180px]"
        >
          <option value="">All Categories</option>
          {(Object.keys(TICKET_CATEGORY_TREE) as TicketCategory[]).map((cat) => (
            <option key={cat} value={cat}>{TICKET_CATEGORY_TREE[cat].label}</option>
          ))}
        </select>
        <input
          type="date"
          aria-label="Created after"
          value={createdAfter}
          onChange={(e) => { setCreatedAfter(e.target.value); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500"
          placeholder="From date"
        />
        <input
          type="date"
          aria-label="Created before"
          value={createdBefore}
          onChange={(e) => { setCreatedBefore(e.target.value); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500"
          placeholder="To date"
        />
      </div>

      {isError ? (
        <ErrorBanner message="Failed to load tickets." onRetry={() => refetch()} />
      ) : (
        <TicketTable
          data={data?.items ?? []}
          isLoading={isLoading}
          page={page}
          totalPages={totalPages}
          total={data?.total ?? 0}
          onPageChange={setPage}
          filter={{
            status: statusFilter || undefined,
            priority: priorityFilter || undefined,
            category: categoryFilter || undefined,
            search: debouncedSearch || undefined,
          }}
        />
      )}

      <TicketCreateModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  );
}
