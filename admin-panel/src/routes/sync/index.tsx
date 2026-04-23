import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { RotateCcw, Trash2, Ban } from 'lucide-react';
import { SyncDashboard } from '@/components/sync/SyncDashboard';
import { SyncHealthChart } from '@/components/charts/SyncHealthChart';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { useAuth } from '@/hooks/use-auth';
import {
  useSyncStatus,
  useConflictLog,
  useDeadLetters,
  useRetryDeadLetter,
  useDiscardDeadLetter,
  useRevokePosToken,
} from '@/api/sync';
import { useTimezone } from '@/hooks/use-timezone';
import { cn } from '@/lib/utils';
import type { SyncConflict, DeadLetterOperation } from '@/types/sync';

export const Route = createFileRoute('/sync/')({
  component: SyncPage,
});

type Tab = 'overview' | 'conflicts' | 'deadletters';

function ConflictsTab() {
  const { data: conflicts = [], isLoading } = useConflictLog();
  const { formatDateTime } = useTimezone();

  const columns: Column<SyncConflict>[] = [
    {
      key: 'entity',
      header: 'Entity',
      cell: (row) => (
        <div>
          <p className="text-sm text-slate-300 font-medium">{row.entityType}</p>
          <p className="text-xs text-slate-500 font-mono truncate max-w-[160px]">{row.entityId}</p>
        </div>
      ),
    },
    {
      key: 'store',
      header: 'Store',
      cell: (row) => <span className="text-xs font-mono text-slate-400 truncate max-w-[120px]">{row.storeId}</span>,
    },
    {
      key: 'resolvedBy',
      header: 'Resolution',
      cell: (row) => (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold uppercase border bg-slate-500/15 text-slate-400 border-slate-500/30">
          {row.resolvedBy}
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: 'Detected',
      cell: (row) => <span className="text-xs text-slate-400">{formatDateTime(row.createdAt)}</span>,
    },
    {
      key: 'resolvedAt',
      header: 'Resolved',
      cell: (row) => row.resolvedAt
        ? <span className="text-xs text-slate-400">{formatDateTime(row.resolvedAt)}</span>
        : <span className="text-xs text-slate-500">Pending</span>,
    },
  ];

  return (
    <DataTable
      columns={columns}
      data={conflicts}
      isLoading={isLoading}
      rowKey={(r) => r.id}
      emptyTitle="No conflicts"
      emptyDescription="No sync conflicts have been recorded."
    />
  );
}

function DeadLettersTab() {
  const { data: items = [], isLoading } = useDeadLetters();
  const retryOp = useRetryDeadLetter();
  const discardOp = useDiscardDeadLetter();
  const { formatDateTime } = useTimezone();
  const [discardTarget, setDiscardTarget] = useState<string | null>(null);

  const columns: Column<DeadLetterOperation>[] = [
    {
      key: 'entity',
      header: 'Entity',
      cell: (row) => (
        <div>
          <p className="text-sm text-slate-300 font-medium">{row.entityType}</p>
          <p className="text-xs text-slate-500">{row.operationType}</p>
        </div>
      ),
    },
    {
      key: 'store',
      header: 'Store',
      cell: (row) => <span className="text-xs font-mono text-slate-400 truncate max-w-[120px]">{row.storeId}</span>,
    },
    {
      key: 'reason',
      header: 'Failure Reason',
      cell: (row) => <span className="text-xs text-red-400 truncate max-w-[200px]">{row.failureReason}</span>,
    },
    {
      key: 'retries',
      header: 'Retries',
      cell: (row) => <span className="text-xs text-slate-400">{row.retryCount}</span>,
      headerClassName: 'w-20',
    },
    {
      key: 'lastAttempt',
      header: 'Last Attempt',
      cell: (row) => row.lastAttemptAt
        ? <span className="text-xs text-slate-400">{formatDateTime(row.lastAttemptAt)}</span>
        : <span className="text-xs text-slate-500">Never</span>,
    },
    {
      key: 'actions',
      header: '',
      headerClassName: 'w-20',
      cell: (row) => (
        <div className="flex items-center gap-1">
          <button
            onClick={() => retryOp.mutate(row.id)}
            disabled={retryOp.isPending}
            title="Retry"
            className="p-1.5 rounded-md text-brand-400 hover:bg-brand-500/10 transition-colors min-w-[32px] min-h-[32px] flex items-center justify-center disabled:opacity-40"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setDiscardTarget(row.id)}
            title="Discard"
            className="p-1.5 rounded-md text-red-400 hover:bg-red-500/10 transition-colors min-w-[32px] min-h-[32px] flex items-center justify-center"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      ),
    },
  ];

  return (
    <>
      <DataTable
        columns={columns}
        data={items}
        isLoading={isLoading}
        rowKey={(r) => r.id}
        emptyTitle="No dead letters"
        emptyDescription="All sync operations are processing normally."
      />
      <ConfirmDialog
        open={!!discardTarget}
        onClose={() => setDiscardTarget(null)}
        onConfirm={() => {
          if (discardTarget) discardOp.mutate(discardTarget, { onSettled: () => setDiscardTarget(null) });
        }}
        title="Discard Operation"
        description="Permanently discard this failed operation? It will not be retried."
        confirmLabel="Discard"
        variant="destructive"
        isLoading={discardOp.isPending}
      />
    </>
  );
}

function SyncPage() {
  const { hasPermission } = useAuth();
  const { data: stores = [], isLoading, isError, refetch } = useSyncStatus();
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [revokeOpen, setRevokeOpen] = useState(false);

  const healthyCount = stores.filter((s) => s.status === 'SYNCED').length;
  const warningCount = stores.filter((s) => s.status === 'PENDING' || s.status === 'SYNCING').length;
  const criticalCount = stores.filter((s) => s.status === 'FAILED' || s.status === 'STALE').length;

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'conflicts', label: 'Conflicts' },
    { key: 'deadletters', label: 'Dead Letters' },
  ];

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Sync Monitoring</h1>
          <p className="panel-subtitle">
            {healthyCount} synced · {warningCount} pending · {criticalCount} failed
          </p>
        </div>
        {hasPermission('users:sessions:revoke') && (
          <button
            onClick={() => setRevokeOpen(true)}
            className="flex items-center gap-2 px-3 py-2 bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 text-sm font-medium rounded-lg transition-colors min-h-[40px]"
          >
            <Ban className="w-4 h-4" />
            <span>Revoke POS Token</span>
          </button>
        )}
      </div>

      {/* Tabs */}
      <div className="flex border-b border-surface-border">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={cn(
              'px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px',
              activeTab === tab.key
                ? 'border-brand-500 text-brand-400'
                : 'border-transparent text-slate-400 hover:text-slate-200',
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'overview' && (
        <>
          <div className="panel-card">
            <h2 className="panel-title text-base mb-4">Queue Depth (24h)</h2>
            <SyncHealthChart />
          </div>
          {isError ? (
            <ErrorBanner message="Failed to load sync status — per-store state is unknown." onRetry={() => refetch()} />
          ) : (
            <SyncDashboard stores={stores} isLoading={isLoading} />
          )}
        </>
      )}

      {activeTab === 'conflicts' && <ConflictsTab />}
      {activeTab === 'deadletters' && <DeadLettersTab />}

      {revokeOpen && <RevokePosTokenDialog onClose={() => setRevokeOpen(false)} />}
    </div>
  );
}

// ── Revoke POS Token dialog (A-004) ─────────────────────────────────────────
// Accepts the JTI (JWT ID) printed by the POS app or found in audit logs and
// optional reason text. POSTs to /admin/sync/tokens/revoke. Idempotent — same
// JTI twice returns the same 204.
function RevokePosTokenDialog({ onClose }: { onClose: () => void }) {
  const revoke = useRevokePosToken();
  const [jti, setJti] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!jti.trim()) {
      setError("JTI is required.");
      return;
    }
    revoke.mutate(
      { jti: jti.trim(), reason: reason.trim() || undefined },
      { onSuccess: () => onClose() },
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-md p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-full bg-red-500/10 flex items-center justify-center">
            <Ban className="w-5 h-5 text-red-400" />
          </div>
          <div>
            <h3 className="text-lg font-semibold text-slate-100">Revoke POS Token</h3>
            <p className="text-xs text-slate-500">Blocks a POS JWT from both API and sync services immediately.</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <p className="rounded-lg bg-red-500/10 border border-red-500/20 px-3 py-2 text-sm text-red-400">
              {error}
            </p>
          )}

          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1">
              JTI (JWT ID) *
            </label>
            <input
              type="text"
              value={jti}
              onChange={(e) => setJti(e.target.value)}
              required
              maxLength={256}
              placeholder="e.g. 3b7c2f0e-..."
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 font-mono focus:outline-none focus:ring-1 focus:ring-red-500"
            />
            <p className="mt-1 text-xs text-slate-500">
              Find in audit log or POS app settings → Developer → Active token.
            </p>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1">Reason (optional)</label>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={2}
              placeholder="e.g. Device lost, employee termination, suspected compromise…"
              className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-red-500"
            />
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={revoke.isPending}
              className="px-4 py-2 rounded-lg text-sm font-medium text-slate-300 hover:bg-surface-elevated transition-colors min-h-[40px] disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={revoke.isPending}
              className="px-4 py-2 rounded-lg text-sm font-medium bg-red-500 hover:bg-red-600 text-white transition-colors min-h-[40px] disabled:opacity-50"
            >
              {revoke.isPending ? "Revoking…" : "Revoke"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
