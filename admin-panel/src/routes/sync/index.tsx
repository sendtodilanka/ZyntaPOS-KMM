import { createFileRoute } from '@tanstack/react-router';
import { SyncDashboard } from '@/components/sync/SyncDashboard';
import { SyncHealthChart } from '@/components/charts/SyncHealthChart';
import { useSyncStatus } from '@/api/sync';

export const Route = createFileRoute('/sync/')({
  component: SyncPage,
});

function SyncPage() {
  const { data: stores = [], isLoading } = useSyncStatus();

  const healthyCount = stores.filter((s) => s.status === 'SYNCED').length;
  const warningCount = stores.filter((s) => s.status === 'PENDING' || s.status === 'SYNCING').length;
  const criticalCount = stores.filter((s) => s.status === 'FAILED' || s.status === 'STALE').length;

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Sync Monitoring</h1>
          <p className="panel-subtitle">
            {healthyCount} synced · {warningCount} pending · {criticalCount} failed
          </p>
        </div>
      </div>

      {/* Summary chart */}
      <div className="panel-card">
        <h2 className="panel-title text-base mb-4">Queue Depth (24h)</h2>
        <SyncHealthChart />
      </div>

      {/* Per-store grid */}
      <SyncDashboard stores={stores} isLoading={isLoading} />
    </div>
  );
}
