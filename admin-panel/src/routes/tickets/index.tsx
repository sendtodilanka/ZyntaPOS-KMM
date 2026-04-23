import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useState, useEffect } from 'react';
import { Plus } from 'lucide-react';
import { TicketTable } from '@/components/tickets/TicketTable';
import { TicketCreateModal } from '@/components/tickets/TicketCreateModal';
import { SearchInput } from '@/components/shared/SearchInput';
import { ExportButton } from '@/components/shared/ExportButton';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { useTickets, useTicketMetrics, exportTickets } from '@/api/tickets';
import { toast } from '@/stores/ui-store';
import { useAuth } from '@/hooks/use-auth';
import { useDebounce } from '@/hooks/use-debounce';
import { TICKET_CATEGORY_TREE } from '@/types/ticket';
import type { TicketStatus, TicketPriority, TicketCategory } from '@/types/ticket';

// H-002: persist all ticket filters in the URL. Defaults are `undefined`
// so TanStack Router omits them from the serialized URL.
interface TicketsSearch {
  page?: number;
  q?: string;
  status?: TicketStatus;
  priority?: TicketPriority;
  category?: TicketCategory;
  body?: boolean;  // searchBody toggle
  from?: string;   // createdAfter — YYYY-MM-DD
  to?: string;     // createdBefore — YYYY-MM-DD
}

const VALID_STATUSES: TicketStatus[] = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_CUSTOMER', 'RESOLVED', 'CLOSED'];
const VALID_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

function isValidCategory(value: string): value is TicketCategory {
  return Object.keys(TICKET_CATEGORY_TREE).includes(value);
}

export const Route = createFileRoute('/tickets/')({
  component: TicketsPage,
  validateSearch: (raw: Record<string, unknown>): TicketsSearch => {
    const page = typeof raw.page === 'number' && raw.page >= 0 ? raw.page : undefined;
    const q = typeof raw.q === 'string' && raw.q.length ? raw.q : undefined;
    const body = raw.body === true ? true : undefined;
    const statusRaw = typeof raw.status === 'string' ? raw.status : '';
    const priorityRaw = typeof raw.priority === 'string' ? raw.priority : '';
    const categoryRaw = typeof raw.category === 'string' ? raw.category : '';
    const from = typeof raw.from === 'string' && DATE_REGEX.test(raw.from) ? raw.from : undefined;
    const to = typeof raw.to === 'string' && DATE_REGEX.test(raw.to) ? raw.to : undefined;
    return {
      page,
      q,
      body,
      status: (VALID_STATUSES as string[]).includes(statusRaw) ? (statusRaw as TicketStatus) : undefined,
      priority: (VALID_PRIORITIES as string[]).includes(priorityRaw) ? (priorityRaw as TicketPriority) : undefined,
      category: isValidCategory(categoryRaw) ? categoryRaw : undefined,
      from,
      to,
    };
  },
});

function TicketsPage() {
  const { hasPermission } = useAuth();
  const navigate = useNavigate({ from: Route.fullPath });
  const search = Route.useSearch();

  const page = search.page ?? 0;
  const searchText = search.q ?? '';
  const statusFilter = search.status ?? '';
  const priorityFilter = search.priority ?? '';
  const categoryFilter = search.category ?? '';
  const searchBody = search.body ?? false;
  const createdAfter = search.from ?? '';
  const createdBefore = search.to ?? '';

  const [searchInput, setSearchInput] = useState(searchText);
  const debouncedSearch = useDebounce(searchInput, 300);
  const [createOpen, setCreateOpen] = useState(false);

  const { data: metrics } = useTicketMetrics();

  useEffect(() => {
    if (debouncedSearch !== searchText) {
      void navigate({
        search: (prev) => ({ ...prev, q: debouncedSearch || undefined, page: undefined }),
        replace: true,
      });
    }
  }, [debouncedSearch, searchText, navigate]);

  const updateSearch = (patch: Partial<TicketsSearch>) => {
    void navigate({
      search: (prev) => ({ ...prev, ...patch, page: undefined }),
      replace: true,
    });
  };

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
        <div className="flex items-center gap-2 flex-wrap">
          <ExportButton
            onExportCsv={async () => {
              try {
                await exportTickets({
                  status: statusFilter || undefined,
                  priority: priorityFilter || undefined,
                  category: categoryFilter || undefined,
                  search: debouncedSearch || undefined,
                });
                toast.success('Export complete', 'Tickets downloaded as CSV.');
              } catch {
                toast.error('Export failed');
              }
            }}
          />
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
            value={searchInput}
            onChange={(v) => setSearchInput(v)}
            placeholder="Search tickets…"
            className="w-full"
          />
          <label className="flex items-center gap-1.5 text-xs text-slate-400 cursor-pointer">
            <input
              type="checkbox"
              checked={searchBody}
              onChange={(e) => updateSearch({ body: e.target.checked || undefined })}
              className="rounded border-surface-border bg-surface-elevated"
            />
            Search description
          </label>
        </div>
        <select
          aria-label="Filter by status"
          value={statusFilter}
          onChange={(e) => updateSearch({ status: (e.target.value as TicketStatus) || undefined })}
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
          onChange={(e) => updateSearch({ priority: (e.target.value as TicketPriority) || undefined })}
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
          onChange={(e) => updateSearch({ category: (e.target.value as TicketCategory) || undefined })}
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
          onChange={(e) => updateSearch({ from: e.target.value || undefined })}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500"
          placeholder="From date"
        />
        <input
          type="date"
          aria-label="Created before"
          value={createdBefore}
          onChange={(e) => updateSearch({ to: e.target.value || undefined })}
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
          onPageChange={(p) => void navigate({ search: (prev) => ({ ...prev, page: p > 0 ? p : undefined }), replace: true })}
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
