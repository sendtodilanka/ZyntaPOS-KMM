import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { AuditLogTable } from '@/components/audit/AuditLogTable';
import { AuditFilterPanel } from '@/components/audit/AuditFilterPanel';
import { SearchInput } from '@/components/shared/SearchInput';
import { ExportButton } from '@/components/shared/ExportButton';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { useAuditLogs, exportAuditLogs } from '@/api/audit';
import { useDebounce } from '@/hooks/use-debounce';
import { toast } from '@/stores/ui-store';
import type { AuditFilter } from '@/types/audit';

export const Route = createFileRoute('/audit/')({
  component: AuditPage,
});

function AuditPage() {
  const [filters, setFilters] = useState<AuditFilter>({ page: 0, size: 50 });
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 300);

  const effectiveFilters = { ...filters, search: debouncedSearch || undefined };
  const { data, isLoading, isError, refetch } = useAuditLogs(effectiveFilters);

  const handleExport = async () => {
    try {
      await exportAuditLogs(effectiveFilters);
      toast.success('Export complete', 'Audit log downloaded as CSV.');
    } catch {
      toast.error('Export failed');
    }
  };

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Audit Log</h1>
          <p className="panel-subtitle">{data?.total ?? 0} total entries · immutable hash chain</p>
        </div>
        <ExportButton onExportCsv={() => void handleExport()} />
      </div>

      <div className="space-y-3">
        <SearchInput
          value={search}
          onChange={(v) => { setSearch(v); setFilters((f) => ({ ...f, page: 0 })); }}
          placeholder="Search events, users, entities…"
          className="max-w-sm"
        />
        <AuditFilterPanel
          filters={filters}
          onChange={(f) => setFilters(f)}
        />
      </div>

      {isError ? (
        <ErrorBanner message="Failed to load audit log — compliance events may be missing from this view." onRetry={() => refetch()} />
      ) : (
        <AuditLogTable
          data={data?.data ?? []}
          isLoading={isLoading}
          page={filters.page ?? 0}
          totalPages={data?.totalPages ?? 1}
          total={data?.total ?? 0}
          onPageChange={(p) => setFilters((f) => ({ ...f, page: p }))}
        />
      )}
    </div>
  );
}
