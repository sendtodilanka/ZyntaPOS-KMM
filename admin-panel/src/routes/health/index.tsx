import { createFileRoute, Link } from '@tanstack/react-router';
import { CheckCircle, AlertTriangle, XCircle, HelpCircle, RefreshCw } from 'lucide-react';
import { useSystemHealth, useAllStoreHealth } from '@/api/health';
import type { ServiceStatus, ServiceHealth, StoreHealthSummary } from '@/types/health';
import { cn, formatRelativeTime } from '@/lib/utils';

export const Route = createFileRoute('/health/')({
  component: HealthPage,
});

function statusIcon(status: ServiceStatus, size = 'w-4 h-4') {
  switch (status) {
    case 'healthy': return <CheckCircle className={cn(size, 'text-green-400')} />;
    case 'degraded': return <AlertTriangle className={cn(size, 'text-amber-400')} />;
    case 'unhealthy': return <XCircle className={cn(size, 'text-red-400')} />;
    default: return <HelpCircle className={cn(size, 'text-slate-400')} />;
  }
}

function statusColor(status: ServiceStatus) {
  switch (status) {
    case 'healthy': return 'bg-green-500/10 text-green-400 border-green-500/20';
    case 'degraded': return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
    case 'unhealthy': return 'bg-red-500/10 text-red-400 border-red-500/20';
    default: return 'bg-slate-700 text-slate-400 border-slate-600';
  }
}

function ServiceCard({ service }: { service: ServiceHealth }) {
  return (
    <div className="bg-surface-card border border-surface-border rounded-xl p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            {statusIcon(service.status)}
            <span className="text-sm font-medium text-slate-100">{service.name}</span>
          </div>
          {service.version && (
            <p className="text-xs text-slate-500 mt-1 font-mono">v{service.version}</p>
          )}
        </div>
        <span className={cn('px-2 py-0.5 text-xs rounded-full border', statusColor(service.status))}>
          {service.status}
        </span>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs text-slate-500">Latency</p>
          <p className={cn('text-sm font-medium', service.latencyMs > 500 ? 'text-amber-400' : service.latencyMs > 1000 ? 'text-red-400' : 'text-slate-100')}>
            {service.latencyMs}ms
          </p>
        </div>
        <div>
          <p className="text-xs text-slate-500">Uptime</p>
          <p className={cn('text-sm font-medium', service.uptime < 95 ? 'text-amber-400' : service.uptime < 90 ? 'text-red-400' : 'text-green-400')}>
            {service.uptime.toFixed(2)}%
          </p>
        </div>
      </div>
      <p className="text-xs text-slate-600 mt-2">
        Checked {formatRelativeTime(service.lastChecked)}
      </p>
    </div>
  );
}

function StoreRow({ store }: { store: StoreHealthSummary }) {
  return (
    <Link
      to="/health/$storeId"
      params={{ storeId: store.storeId }}
      className="flex items-center gap-3 py-3 border-b border-surface-border last:border-0 hover:bg-surface-elevated px-2 -mx-2 rounded-lg transition-colors"
    >
      <div className="flex-shrink-0">{statusIcon(store.status)}</div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-slate-100 truncate">{store.storeName}</p>
        <p className="text-xs text-slate-500">v{store.appVersion} · Android {store.androidVersion}</p>
      </div>
      <div className="text-right flex-shrink-0">
        <p className="text-xs text-slate-400">{store.pendingOperations > 0 ? <span className="text-amber-400">{store.pendingOperations} pending</span> : 'Synced'}</p>
        <p className="text-xs text-slate-600">{formatRelativeTime(store.lastSync)}</p>
      </div>
      <span className={cn('px-2 py-0.5 text-xs rounded-full border hidden sm:inline-flex', statusColor(store.status))}>
        {store.status}
      </span>
    </Link>
  );
}

function HealthPage() {
  const { data: system, isLoading: sysLoading, refetch: refetchSystem, isFetching: sysFetching } = useSystemHealth();
  const { data: stores, isLoading: storesLoading } = useAllStoreHealth();

  const healthyCount = (stores ?? []).filter(s => s.status === 'healthy').length;
  const degradedCount = (stores ?? []).filter(s => s.status === 'degraded').length;
  const unhealthyCount = (stores ?? []).filter(s => s.status === 'unhealthy').length;

  return (
    <div className="p-4 md:p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">System Health</h1>
          <p className="text-sm text-slate-400 mt-1">Live service status and store connectivity</p>
        </div>
        <button
          onClick={() => refetchSystem()}
          disabled={sysFetching}
          className="flex items-center gap-2 px-3 py-2 bg-surface-elevated hover:bg-surface-card text-slate-300 text-sm rounded-lg min-h-[44px] border border-surface-border transition-colors"
        >
          <RefreshCw className={cn('w-4 h-4', sysFetching && 'animate-spin')} />
          Refresh
        </button>
      </div>

      {/* Overall Status Banner */}
      {!sysLoading && system && (
        <div className={cn('flex items-center gap-3 p-4 rounded-xl border', statusColor(system.overall))}>
          {statusIcon(system.overall, 'w-5 h-5')}
          <div>
            <p className="text-sm font-semibold capitalize">System {system.overall}</p>
            <p className="text-xs opacity-70">Last checked {formatRelativeTime(system.checkedAt)}</p>
          </div>
        </div>
      )}

      {/* Backend Services Grid */}
      <div>
        <h2 className="text-base font-semibold text-slate-100 mb-3">Backend Services</h2>
        {sysLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {[1,2,3,4,5,6].map(i => <div key={i} className="h-32 bg-surface-elevated rounded-xl animate-pulse" />)}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {(system?.services ?? []).map(s => <ServiceCard key={s.name} service={s} />)}
          </div>
        )}
      </div>

      {/* Store Health Summary */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-semibold text-slate-100">Store Health</h2>
          <div className="flex items-center gap-3 text-xs">
            <span className="text-green-400">{healthyCount} healthy</span>
            {degradedCount > 0 && <span className="text-amber-400">{degradedCount} degraded</span>}
            {unhealthyCount > 0 && <span className="text-red-400">{unhealthyCount} unhealthy</span>}
          </div>
        </div>
        <div className="bg-surface-card border border-surface-border rounded-xl p-4">
          {storesLoading ? (
            <div className="space-y-3">{[1,2,3,4,5].map(i=><div key={i} className="h-14 bg-surface-elevated rounded animate-pulse"/>)}</div>
          ) : (stores ?? []).length === 0 ? (
            <p className="text-center py-6 text-slate-400">No stores registered</p>
          ) : (
            <div>{(stores ?? []).map(s => <StoreRow key={s.storeId} store={s} />)}</div>
          )}
        </div>
      </div>
    </div>
  );
}
