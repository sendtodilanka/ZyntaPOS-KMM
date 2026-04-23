import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Plus } from 'lucide-react';
import { LicenseTable } from '@/components/licenses/LicenseTable';
import { LicenseCreateForm } from '@/components/licenses/LicenseCreateForm';
import { LicenseExtendDialog } from '@/components/licenses/LicenseExtendDialog';
import { SearchInput } from '@/components/shared/SearchInput';
import { ExportButton } from '@/components/shared/ExportButton';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { useLicenses, useLicenseStats } from '@/api/licenses';
import { useDebounce } from '@/hooks/use-debounce';
import { exportToCsv } from '@/lib/export';
import type { License, LicenseStatus, LicenseEdition } from '@/types/license';

export const Route = createFileRoute('/licenses/')({
  component: LicensesPage,
});

function LicensesPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<LicenseStatus | ''>('');
  const [editionFilter, setEditionFilter] = useState<LicenseEdition | ''>('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<License | null>(null);

  const debouncedSearch = useDebounce(search, 300);
  const { data, isLoading, isError, refetch } = useLicenses({
    page, size: 20,
    search: debouncedSearch || undefined,
    status: statusFilter || undefined,
    edition: editionFilter || undefined,
  });
  const { data: stats } = useLicenseStats();

  const handleExportCsv = () => {
    if (!data?.data) return;
    exportToCsv(data.data, 'licenses', [
      { key: 'key', header: 'License Key' },
      { key: 'customerName', header: 'Customer' },
      { key: 'edition', header: 'Edition' },
      { key: 'status', header: 'Status' },
      { key: 'activeDevices', header: 'Active Devices' },
      { key: 'maxDevices', header: 'Max Devices' },
      { key: 'expiresAt', header: 'Expires At' },
    ]);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Licenses</h1>
          <p className="panel-subtitle">
            {stats ? `${stats.active} active · ${stats.expired} expired · ${stats.expiringSoon} expiring soon` : 'Manage all licenses'}
          </p>
        </div>
        <button
          onClick={() => setCreateOpen(true)}
          className="flex items-center gap-2 px-4 py-2 bg-brand-700 hover:bg-brand-800 text-white text-sm font-medium rounded-lg transition-colors min-h-[44px]"
        >
          <Plus className="w-4 h-4" />
          <span>New License</span>
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <SearchInput
          value={search}
          onChange={(v) => { setSearch(v); setPage(0); }}
          placeholder="Search by key or customer…"
          className="flex-1 min-w-[200px] max-w-sm"
        />
        <select
          aria-label="Filter by status"
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value as LicenseStatus | ''); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[140px]"
        >
          <option value="">All Statuses</option>
          {(['ACTIVE', 'EXPIRED', 'REVOKED', 'SUSPENDED', 'EXPIRING_SOON'] as LicenseStatus[]).map((s) => (
            <option key={s} value={s}>{s.replace('_', ' ')}</option>
          ))}
        </select>
        <select
          aria-label="Filter by edition"
          value={editionFilter}
          onChange={(e) => { setEditionFilter(e.target.value as LicenseEdition | ''); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[140px]"
        >
          <option value="">All Editions</option>
          {(['STARTER', 'PROFESSIONAL', 'ENTERPRISE'] as LicenseEdition[]).map((e) => (
            <option key={e} value={e}>{e}</option>
          ))}
        </select>
        <ExportButton onExportCsv={handleExportCsv} />
      </div>

      {/* Table */}
      {isError ? (
        <ErrorBanner message="Failed to load licenses." onRetry={() => refetch()} />
      ) : (
        <LicenseTable
          data={data?.data ?? []}
          isLoading={isLoading}
          page={page}
          totalPages={data?.totalPages ?? 1}
          total={data?.total ?? 0}
          onPageChange={setPage}
          onEdit={setEditTarget}
        />
      )}

      <LicenseCreateForm open={createOpen} onClose={() => setCreateOpen(false)} />
      <LicenseExtendDialog license={editTarget} onClose={() => setEditTarget(null)} />
    </div>
  );
}
