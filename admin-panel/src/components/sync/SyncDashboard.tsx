import { useState } from 'react';
import { RefreshCw, ChevronDown, ChevronUp } from 'lucide-react';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { ForceSyncButton } from './ForceSyncButton';
import { SyncQueueView } from './SyncQueueView';
import { useSyncQueue } from '@/api/sync';
import { formatRelativeTime } from '@/lib/utils';
import type { StoreSyncStatus } from '@/types/sync';

function QueueDepthBadge({ depth }: { depth: number }) {
  const color = depth > 50
    ? 'text-red-400 bg-red-400/10'
    : depth > 10
      ? 'text-amber-400 bg-amber-400/10'
      : 'text-emerald-400 bg-emerald-400/10';
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold ${color}`}>
      <RefreshCw className="w-3 h-3" />
      {depth}
    </span>
  );
}

function StoreCard({ store }: { store: StoreSyncStatus }) {
  const [expanded, setExpanded] = useState(false);
  const { data: queue = [], isLoading } = useSyncQueue(expanded ? store.storeId : '');

  return (
    <div className="panel-card">
      <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-slate-100 truncate">{store.storeName}</p>
          <p className="text-xs text-slate-400 mt-0.5">
            {store.lastSyncAt ? `Last sync ${formatRelativeTime(store.lastSyncAt)}` : 'Never synced'}
            {store.lastSyncDurationMs && ` · ${store.lastSyncDurationMs}ms`}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <StatusBadge status={store.status} />
          <QueueDepthBadge depth={store.queueDepth} />
        </div>
      </div>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4 text-xs text-slate-400">
          <span>{store.pendingOperations} pending</span>
          {store.errorCount > 0 && <span className="text-red-400">{store.errorCount} errors</span>}
        </div>
        <div className="flex items-center gap-2">
          <ForceSyncButton storeId={store.storeId} storeName={store.storeName} />
          <button
            onClick={() => setExpanded((e) => !e)}
            aria-label={expanded ? 'Collapse sync details' : 'Expand sync details'}
            className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated transition-colors min-w-[44px] min-h-[44px] flex items-center justify-center"
          >
            {expanded ? <ChevronUp className="w-4 h-4" aria-hidden="true" /> : <ChevronDown className="w-4 h-4" aria-hidden="true" />}
          </button>
        </div>
      </div>

      {expanded && (
        <div className="mt-4 border-t border-surface-border pt-4">
          <h4 className="text-xs font-medium text-slate-400 uppercase mb-3">Pending Queue</h4>
          <SyncQueueView operations={queue} isLoading={isLoading} />
        </div>
      )}
    </div>
  );
}

interface SyncDashboardProps {
  stores: StoreSyncStatus[];
  isLoading: boolean;
}

export function SyncDashboard({ stores, isLoading }: SyncDashboardProps) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="panel-card animate-pulse space-y-3">
            <div className="h-4 bg-surface-elevated rounded w-1/2" />
            <div className="h-3 bg-surface-elevated rounded w-2/3" />
            <div className="h-8 bg-surface-elevated rounded" />
          </div>
        ))}
      </div>
    );
  }

  if (stores.length === 0) {
    return <div className="text-center py-16 text-slate-400">No stores to monitor.</div>;
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {stores.map((store) => <StoreCard key={store.storeId} store={store} />)}
    </div>
  );
}
