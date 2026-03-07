import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowLeft, CheckCircle, AlertTriangle, XCircle, HelpCircle } from 'lucide-react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import { useStoreHealthDetail } from '@/api/health';
import type { ServiceStatus } from '@/types/health';
import { cn, formatRelativeTime } from '@/lib/utils';

export const Route = createFileRoute('/health/$storeId')({
  component: StoreHealthDetailPage,
});

function statusIcon(status: ServiceStatus) {
  switch (status) {
    case 'healthy': return <CheckCircle className="w-5 h-5 text-green-400" />;
    case 'degraded': return <AlertTriangle className="w-5 h-5 text-amber-400" />;
    case 'unhealthy': return <XCircle className="w-5 h-5 text-red-400" />;
    default: return <HelpCircle className="w-5 h-5 text-slate-400" />;
  }
}

function severityColor(s: 'error' | 'warn' | 'info') {
  return s === 'error' ? 'text-red-400' : s === 'warn' ? 'text-amber-400' : 'text-slate-400';
}

function StoreHealthDetailPage() {
  const { storeId } = Route.useParams();
  const { data, isLoading, error } = useStoreHealthDetail(storeId);

  if (isLoading) {
    return (
      <div className="p-4 md:p-6 space-y-4">
        <div className="h-8 w-48 bg-surface-elevated rounded animate-pulse" />
        <div className="h-32 bg-surface-elevated rounded-xl animate-pulse" />
        <div className="h-48 bg-surface-elevated rounded-xl animate-pulse" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="p-4 md:p-6">
        <Link to="/health" className="flex items-center gap-2 text-slate-400 hover:text-slate-100 text-sm mb-4">
          <ArrowLeft className="w-4 h-4" /> Back to Health
        </Link>
        <div className="text-center py-12 text-slate-400">Store health data not found</div>
      </div>
    );
  }

  const latencyData = (data.recentActivity ?? []).map(a => ({
    time: new Date(a.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    Latency: a.latencyMs,
  }));

  return (
    <div className="p-4 md:p-6 space-y-6">
      {/* Back + Header */}
      <div>
        <Link to="/health" className="flex items-center gap-2 text-slate-400 hover:text-slate-100 text-sm mb-3 w-fit min-h-[44px]">
          <ArrowLeft className="w-4 h-4" /> Back to Health
        </Link>
        <div className="flex items-center gap-3">
          {statusIcon(data.status)}
          <div>
            <h1 className="text-2xl font-bold text-slate-100">{data.storeName}</h1>
            <p className="text-xs text-slate-500 font-mono">{data.storeId}</p>
          </div>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {[
          { label: 'App Version', value: `v${data.appVersion}` },
          { label: 'Android', value: data.androidVersion },
          { label: 'Uptime', value: `${data.uptimePercent.toFixed(2)}%` },
          { label: 'Pending Ops', value: data.pendingOperations.toString() },
        ].map(stat => (
          <div key={stat.label} className="bg-surface-card border border-surface-border rounded-xl p-4">
            <p className="text-xs text-slate-500">{stat.label}</p>
            <p className="text-lg font-bold text-slate-100 mt-1">{stat.value}</p>
          </div>
        ))}
      </div>

      {/* Last Sync */}
      <div className="bg-surface-card border border-surface-border rounded-xl p-4">
        <p className="text-xs text-slate-500 mb-1">Last Sync</p>
        <p className="text-sm text-slate-100">{new Date(data.lastSync).toLocaleString()} <span className="text-slate-500">({formatRelativeTime(data.lastSync)})</span></p>
      </div>

      {/* Latency Chart */}
      {latencyData.length > 0 && (
        <div className="bg-surface-card border border-surface-border rounded-xl p-4">
          <h2 className="text-sm font-semibold text-slate-100 mb-4">Sync Latency (Last 24h)</h2>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={latencyData} margin={{ top: 4, right: 4, bottom: 4, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.1)" />
              <XAxis dataKey="time" tick={{ fill: '#94a3b8', fontSize: 11 }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} tickLine={false} axisLine={false} width={40} tickFormatter={v => `${v}ms`} />
              <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: 8, fontSize: 12, color: '#f1f5f9' }} />
              <Line type="monotone" dataKey="Latency" stroke="#38bdf8" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Error Log */}
      {(data.errorLog ?? []).length > 0 && (
        <div className="bg-surface-card border border-surface-border rounded-xl overflow-hidden">
          <div className="px-4 py-3 border-b border-surface-border">
            <h2 className="text-sm font-semibold text-slate-100">Recent Log</h2>
          </div>
          <div className="divide-y divide-surface-border max-h-64 overflow-y-auto">
            {data.errorLog.map((entry, i) => (
              <div key={i} className="px-4 py-3 flex items-start gap-3">
                <span className={cn('text-xs font-mono pt-0.5 flex-shrink-0', severityColor(entry.severity))}>
                  [{entry.severity.toUpperCase()}]
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-xs text-slate-300 break-words">{entry.message}</p>
                  <p className="text-xs text-slate-600 mt-0.5">{formatRelativeTime(entry.timestamp)}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
