import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { ShieldAlert } from 'lucide-react';
import { AuditLogTable } from '@/components/audit/AuditLogTable';
import { AuditFilterPanel } from '@/components/audit/AuditFilterPanel';
import { SearchInput } from '@/components/shared/SearchInput';
import { ExportButton } from '@/components/shared/ExportButton';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { useAuditLogs, exportAuditLogs } from '@/api/audit';
import { useAuth } from '@/hooks/use-auth';
import { useDebounce } from '@/hooks/use-debounce';
import { toast } from '@/stores/ui-store';
import type { AuditFilter } from '@/types/audit';

export const Route = createFileRoute('/audit/')({
  component: AuditPage,
});

function AuditPage() {
  const { hasPermission } = useAuth();
  const canRead = hasPermission('audit:read');

  if (!canRead) {
    return <AccessDenied />;
  }
  return <AuditPageContent />;
}

function AccessDenied() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center gap-3">
      <ShieldAlert className="w-10 h-10 text-destructive" />
      <h1 className="text-xl font-semibold text-slate-100">Access denied</h1>
      <p className="text-sm text-slate-400 max-w-md">
        Your role does not include the <code className="px-1.5 py-0.5 rounded bg-surface-elevated text-xs">audit:read</code> permission.
        Contact an administrator if you need access to the audit log.
      </p>
    </div>
  );
}

function AuditPageContent() {
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
