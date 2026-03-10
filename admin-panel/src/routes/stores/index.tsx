import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { StoreTable } from '@/components/stores/StoreTable';
import { SearchInput } from '@/components/shared/SearchInput';
import { useStores } from '@/api/stores';
import { useDebounce } from '@/hooks/use-debounce';
import type { StoreStatus } from '@/types/store';

export const Route = createFileRoute('/stores/')({
  component: StoresPage,
});

function StoresPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StoreStatus | ''>('');
  const debouncedSearch = useDebounce(search, 300);

  const { data, isLoading } = useStores({
    page, size: 20,
    search: debouncedSearch || undefined,
    status: statusFilter || undefined,
  });

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Stores</h1>
          <p className="panel-subtitle">{data?.total ?? 0} total deployments</p>
        </div>
      </div>

      <div className="flex flex-wrap gap-3">
        <SearchInput
          value={search}
          onChange={(v) => { setSearch(v); setPage(0); }}
          placeholder="Search stores…"
          className="flex-1 min-w-[200px] max-w-sm"
        />
        <select
          aria-label="Filter by status"
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value as StoreStatus | ''); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[140px]"
        >
          <option value="">All Statuses</option>
          {(['HEALTHY', 'WARNING', 'CRITICAL', 'OFFLINE'] as StoreStatus[]).map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>

      <StoreTable
        data={data?.data ?? []}
        isLoading={isLoading}
        page={page}
        totalPages={data?.totalPages ?? 1}
        total={data?.total ?? 0}
        onPageChange={setPage}
      />
    </div>
  );
}
