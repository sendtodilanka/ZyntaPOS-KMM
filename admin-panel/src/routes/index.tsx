import { createFileRoute } from '@tanstack/react-router';
import { useState, useCallback } from 'react';
import {
  Store, Key, DollarSign, RefreshCw, TrendingUp, AlertTriangle, RotateCw,
} from 'lucide-react';
import { subDays, format, formatDistanceToNow } from 'date-fns';
import { KpiCard } from '@/components/shared/KpiCard';
import { SalesChart } from '@/components/charts/SalesChart';
import { StoreComparisonChart } from '@/components/charts/StoreComparisonChart';
import { LicenseDistribution } from '@/components/charts/LicenseDistribution';
import { UptimeChart } from '@/components/charts/UptimeChart';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { useDashboardKPIs, useSalesChart, useStoreComparison } from '@/api/metrics';
import { useAlerts } from '@/api/alerts';
import { useSystemHealth } from '@/api/health';
import { formatCurrency, formatRelativeTime } from '@/lib/utils';
import type { TimePeriod } from '@/types/metrics';

export const Route = createFileRoute('/')({
  component: DashboardPage,
});

const PERIOD_OPTIONS: { label: string; value: TimePeriod }[] = [
  { label: 'Today', value: 'today' },
  { label: 'This Week', value: 'week' },
  { label: 'This Month', value: 'month' },
];

function DashboardPage() {
  const [period, setPeriod] = useState<TimePeriod>('today');
  const [storeFilter, setStoreFilter] = useState('');

  const { data: kpis, isLoading: kpisLoading, refetch: refetchKpis, dataUpdatedAt } = useDashboardKPIs(period);
  const { data: salesData, isLoading: salesLoading, refetch: refetchSales } = useSalesChart({
    from: format(subDays(new Date(), period === 'today' ? 1 : period === 'week' ? 7 : 30), 'yyyy-MM-dd'),
    to: format(new Date(), 'yyyy-MM-dd'),
    granularity: period === 'today' ? 'hour' : period === 'week' ? 'day' : 'day',
  });
  const { data: storeData, isLoading: storeLoading, refetch: refetchStores } = useStoreComparison(period);
  const { data: alertsPage, isLoading: alertsLoading } = useAlerts({ status: 'active', pageSize: 5 });
  const { data: healthData, isLoading: healthLoading } = useSystemHealth();

  const recentAlerts = alertsPage?.items ?? [];
  const uptimeData = healthData?.services.map((s) => ({ service: s.name, uptimePercent: s.uptime })) ?? [];

  // Client-side store filter for comparison chart
  const filteredStoreData = storeFilter.trim()
    ? (storeData ?? []).filter((s) =>
        s.storeName.toLowerCase().includes(storeFilter.toLowerCase()) ||
        s.storeId.toLowerCase().includes(storeFilter.toLowerCase())
      )
    : storeData;

  const handleRefresh = useCallback(() => {
    void refetchKpis();
    void refetchSales();
    void refetchStores();
  }, [refetchKpis, refetchSales, refetchStores]);

  const lastUpdated = dataUpdatedAt ? formatDistanceToNow(new Date(dataUpdatedAt), { addSuffix: true }) : null;

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Dashboard</h1>
          <p className="panel-subtitle">
            Overview of all ZyntaPOS deployments
            {lastUpdated && <span className="ml-2 text-slate-500">· updated {lastUpdated}</span>}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* Live indicator — auto-refreshes every 30s via React Query */}
          <div className="flex items-center gap-1.5 h-9 px-3 text-xs text-emerald-400 border border-emerald-500/30 bg-emerald-500/10 rounded-lg">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500" />
            </span>
            <span className="hidden sm:inline">Live</span>
          </div>
          {/* Refresh button */}
          <button
            onClick={handleRefresh}
            aria-label="Refresh dashboard"
            className="flex items-center gap-1.5 h-9 px-3 text-xs text-slate-400 hover:text-slate-100 border border-surface-border rounded-lg hover:bg-surface-elevated transition-colors"
          >
            <RotateCw className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Refresh</span>
          </button>
          {/* Period selector */}
          <div className="flex items-center gap-1 bg-surface-elevated rounded-lg p-1">
            {PERIOD_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                onClick={() => setPeriod(opt.value)}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors min-h-[36px] ${
                  period === opt.value
                    ? 'bg-brand-700 text-white'
                    : 'text-slate-400 hover:text-slate-100'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* KPI Cards — responsive grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          title="Total Stores"
          value={kpis?.totalStores ?? 0}
          trend={kpis?.totalStoresTrend}
          subtitle="active deployments"
          icon={Store}
          iconColor="text-brand-400"
          isLoading={kpisLoading}
        />
        <KpiCard
          title="Active Licenses"
          value={kpis?.activeLicenses ?? 0}
          trend={kpis?.activeLicensesTrend}
          subtitle="of total issued"
          icon={Key}
          iconColor="text-purple-400"
          isLoading={kpisLoading}
        />
        <KpiCard
          title="Revenue Today"
          value={kpis ? formatCurrency(kpis.revenueToday, kpis.currency) : '—'}
          trend={kpis?.revenueTodayTrend}
          subtitle="across all stores"
          icon={DollarSign}
          iconColor="text-emerald-400"
          isLoading={kpisLoading}
        />
        <KpiCard
          title="Sync Health"
          value={kpis ? `${kpis.syncHealthPercent.toFixed(1)}%` : '—'}
          trend={kpis?.syncHealthTrend}
          subtitle="stores in sync"
          icon={RefreshCw}
          iconColor="text-amber-400"
          isLoading={kpisLoading}
        />
      </div>

      {/* Charts — responsive 2-column */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Sales chart */}
        <div className="panel-card">
          <div className="panel-header mb-4">
            <div>
              <h2 className="panel-title text-base">Revenue Trend</h2>
              <p className="panel-subtitle text-xs">Aggregated across all stores</p>
            </div>
            <TrendingUp className="w-4 h-4 text-slate-500" />
          </div>
          <SalesChart data={salesData} isLoading={salesLoading} granularity={period === 'today' ? 'hour' : 'day'} />
        </div>

        {/* Store comparison */}
        <div className="panel-card">
          <div className="panel-header mb-3">
            <div>
              <h2 className="panel-title text-base">Store Comparison</h2>
              <p className="panel-subtitle text-xs">Revenue by store</p>
            </div>
            <Store className="w-4 h-4 text-slate-500" />
          </div>
          <input
            type="text"
            value={storeFilter}
            onChange={(e) => setStoreFilter(e.target.value)}
            placeholder="Filter by store name or ID…"
            className="w-full h-8 mb-3 bg-surface-elevated border border-surface-border rounded-lg px-3 text-xs text-slate-300 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
          />
          <StoreComparisonChart data={filteredStoreData} isLoading={storeLoading} />
        </div>
      </div>

      {/* Bottom row — responsive 3-column */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {/* License distribution */}
        <div className="panel-card">
          <h2 className="panel-title text-base mb-4">License Editions</h2>
          <LicenseDistribution isLoading={kpisLoading} />
        </div>

        {/* Uptime */}
        <div className="panel-card">
          <h2 className="panel-title text-base mb-4">Service Uptime</h2>
          <UptimeChart data={uptimeData} isLoading={healthLoading} />
        </div>

        {/* Quick alerts */}
        <div className="panel-card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="panel-title text-base">Recent Alerts</h2>
            <AlertTriangle className="w-4 h-4 text-amber-400" />
          </div>
          <div className="space-y-2">
            {alertsLoading && (
              <div className="space-y-2">
                {[0, 1, 2].map((i) => <div key={i} className="h-14 rounded-lg bg-surface-elevated animate-pulse" />)}
              </div>
            )}
            {!alertsLoading && recentAlerts.length === 0 && (
              <p className="text-xs text-slate-500 py-4 text-center">No active alerts.</p>
            )}
            {recentAlerts.map((alert) => (
              <div key={alert.id} className="flex items-start gap-3 p-2.5 rounded-lg bg-surface-elevated">
                <StatusBadge status={alert.severity} className="flex-shrink-0 mt-0.5" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium text-slate-200 truncate">{alert.storeName ?? alert.category}</p>
                  <p className="text-xs text-slate-400 truncate">{alert.message}</p>
                  <p className="text-[10px] text-slate-500 mt-0.5">{formatRelativeTime(alert.createdAt)}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
