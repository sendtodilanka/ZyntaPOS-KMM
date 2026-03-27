import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Users, Star } from 'lucide-react';
import { useGlobalCustomers } from '@/api/customers';
import { useDebounce } from '@/hooks/use-debounce';

export const Route = createFileRoute('/customers/')({
  component: CustomersPage,
});

const PAGE_SIZE = 50;

function CustomersPage() {
  const [search,  setSearch]  = useState('');
  const [storeId, setStoreId] = useState('');
  const [page,    setPage]    = useState(0);

  const debouncedSearch  = useDebounce(search,  300);
  const debouncedStoreId = useDebounce(storeId, 300);

  const { data, isLoading } = useGlobalCustomers({
    search:  debouncedSearch  || undefined,
    storeId: debouncedStoreId || undefined,
    page,
    size: PAGE_SIZE,
  });

  const totalPages = data ? Math.ceil(data.total / PAGE_SIZE) : 0;

  function handleSearchChange(value: string) {
    setSearch(value);
    setPage(0);
  }

  function handleStoreIdChange(value: string) {
    setStoreId(value);
    setPage(0);
  }

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Customer Directory</h1>
          <p className="panel-subtitle">
            {data?.total ?? 0} active customers across all stores
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          value={search}
          onChange={(e) => handleSearchChange(e.target.value)}
          placeholder="Search name, email, phone…"
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 flex-1 min-w-[200px] max-w-sm"
        />
        <input
          type="text"
          value={storeId}
          onChange={(e) => handleStoreIdChange(e.target.value)}
          placeholder="Filter by Store ID…"
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 flex-1 min-w-[180px] max-w-xs"
        />
      </div>

      {/* Table */}
      {isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading…</div>
      ) : (data?.items ?? []).length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <Users className="w-10 h-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm">No customers found</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-surface-border text-slate-500 text-xs uppercase tracking-wider">
                  <th className="px-4 py-3 text-left">Name</th>
                  <th className="px-4 py-3 text-left">Email</th>
                  <th className="px-4 py-3 text-left">Phone</th>
                  <th className="px-4 py-3 text-left">Store ID</th>
                  <th className="px-4 py-3 text-right">Loyalty Points</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-border">
                {(data?.items ?? []).map((customer) => (
                  <tr
                    key={customer.id}
                    className="hover:bg-surface-elevated/50 transition-colors"
                  >
                    <td className="px-4 py-3 text-slate-200 font-medium">
                      {customer.name}
                    </td>
                    <td className="px-4 py-3 text-slate-400">
                      {customer.email ?? <span className="text-slate-600 italic">—</span>}
                    </td>
                    <td className="px-4 py-3 text-slate-400">
                      {customer.phone ?? <span className="text-slate-600 italic">—</span>}
                    </td>
                    <td className="px-4 py-3 font-mono text-slate-500 text-xs">
                      {customer.storeId ?? <span className="italic">—</span>}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {customer.loyaltyPoints > 0 ? (
                        <span className="inline-flex items-center gap-1 text-amber-400 font-medium">
                          <Star className="w-3 h-3" />
                          {customer.loyaltyPoints.toLocaleString()}
                        </span>
                      ) : (
                        <span className="text-slate-600">0</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="px-4 py-3 border-t border-surface-border flex items-center justify-between text-sm text-slate-400">
              <span>
                Page {page + 1} of {totalPages} · {data?.total} total
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-3 py-1.5 rounded-lg border border-surface-border bg-surface-elevated hover:bg-surface-border disabled:opacity-40 disabled:cursor-not-allowed transition-colors text-slate-300"
                >
                  Previous
                </button>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-3 py-1.5 rounded-lg border border-surface-border bg-surface-elevated hover:bg-surface-border disabled:opacity-40 disabled:cursor-not-allowed transition-colors text-slate-300"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
