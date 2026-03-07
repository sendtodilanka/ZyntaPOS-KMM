import { Database, RefreshCw, AlertTriangle, Clock, Wifi } from 'lucide-react';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { formatBytes, formatRelativeTime } from '@/lib/utils';
import { Skeleton } from '@/components/shared/LoadingState';
import type { StoreHealth } from '@/types/store';

interface StoreHealthCardProps {
  health?: StoreHealth;
  isLoading?: boolean;
}

export function StoreHealthCard({ health, isLoading = false }: StoreHealthCardProps) {
  if (isLoading) {
    return (
      <div className="panel-card space-y-4">
        <Skeleton className="h-5 w-32" />
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-16 rounded-lg" />
          ))}
        </div>
      </div>
    );
  }

  if (!health) return null;

  const metrics = [
    { icon: Database, label: 'DB Size', value: formatBytes(health.dbSizeBytes) },
    { icon: RefreshCw, label: 'Sync Queue', value: health.syncQueueDepth.toString(), highlight: health.syncQueueDepth > 50 ? 'text-red-400' : health.syncQueueDepth > 10 ? 'text-amber-400' : 'text-emerald-400' },
    { icon: AlertTriangle, label: 'Errors (24h)', value: health.errorCount24h.toString(), highlight: health.errorCount24h > 0 ? 'text-red-400' : 'text-emerald-400' },
    { icon: Clock, label: 'Uptime', value: `${health.uptimeHours.toFixed(0)}h` },
    { icon: Wifi, label: 'Response', value: `${health.responseTimeMs}ms`, highlight: health.responseTimeMs > 1000 ? 'text-red-400' : health.responseTimeMs > 500 ? 'text-amber-400' : 'text-emerald-400' },
    { icon: Clock, label: 'Last Heartbeat', value: health.lastHeartbeatAt ? formatRelativeTime(health.lastHeartbeatAt) : '—' },
  ];

  return (
    <div className="panel-card">
      <div className="flex items-center justify-between mb-4">
        <h3 className="panel-title text-base">Health Status</h3>
        <StatusBadge status={health.status} />
      </div>
      <div className="mb-4">
        <div className="flex items-center justify-between mb-1">
          <span className="text-xs text-slate-400">Health Score</span>
          <span className="text-xs font-semibold text-slate-200">{health.healthScore}%</span>
        </div>
        <div className="w-full h-2 bg-surface-elevated rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all ${health.healthScore >= 90 ? 'bg-emerald-400' : health.healthScore >= 70 ? 'bg-amber-400' : 'bg-red-400'}`}
            style={{ width: `${health.healthScore}%` }}
          />
        </div>
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
        {metrics.map(({ icon: Icon, label, value, highlight }) => (
          <div key={label} className="bg-surface-elevated rounded-lg p-3">
            <div className="flex items-center gap-1.5 mb-1">
              <Icon className="w-3 h-3 text-slate-500" />
              <span className="text-[10px] text-slate-400 uppercase font-medium">{label}</span>
            </div>
            <span className={`text-sm font-semibold ${highlight ?? 'text-slate-100'}`}>{value}</span>
          </div>
        ))}
      </div>
      <p className="text-[10px] text-slate-500 mt-3">{health.appVersion} · {health.osInfo}</p>
    </div>
  );
}
