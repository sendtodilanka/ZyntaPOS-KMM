import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Bell, CheckCheck, Check, AlertTriangle, AlertCircle, Info, Zap } from 'lucide-react';
import { useAlerts, useAlertCounts, useAlertRules, useAcknowledgeAlert, useResolveAlert, useToggleAlertRule } from '@/api/alerts';
import type { Alert, AlertFilter, AlertSeverity, AlertStatus, AlertCategory, AlertRule } from '@/types/alert';
import { cn, formatRelativeTime } from '@/lib/utils';

export const Route = createFileRoute('/alerts/')({
  component: AlertsPage,
});

function severityIcon(severity: AlertSeverity) {
  const cls = 'w-4 h-4';
  switch (severity) {
    case 'critical': return <Zap className={cn(cls, 'text-red-500')} />;
    case 'high': return <AlertCircle className={cn(cls, 'text-red-400')} />;
    case 'medium': return <AlertTriangle className={cn(cls, 'text-amber-400')} />;
    case 'low': return <AlertTriangle className={cn(cls, 'text-yellow-400')} />;
    default: return <Info className={cn(cls, 'text-blue-400')} />;
  }
}

function severityBadge(severity: AlertSeverity) {
  const colors: Record<AlertSeverity, string> = {
    critical: 'bg-red-500/20 text-red-400 border-red-500/30',
    high: 'bg-red-500/10 text-red-400 border-red-500/20',
    medium: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    low: 'bg-yellow-500/10 text-yellow-400 border-yellow-500/20',
    info: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  };
  return colors[severity] ?? 'bg-slate-700 text-slate-400 border-slate-600';
}

function statusBadge(status: AlertStatus) {
  const colors: Record<AlertStatus, string> = {
    active: 'bg-red-500/10 text-red-400',
    acknowledged: 'bg-amber-500/10 text-amber-400',
    resolved: 'bg-green-500/10 text-green-400',
    silenced: 'bg-slate-700 text-slate-400',
  };
  return colors[status] ?? 'bg-slate-700 text-slate-400';
}

function AlertRow({ alert }: { alert: Alert }) {
  const { mutate: acknowledge, isPending: acking } = useAcknowledgeAlert();
  const { mutate: resolve, isPending: resolving } = useResolveAlert();

  return (
    <div className={cn(
      'p-4 border-b border-surface-border last:border-0',
      alert.status === 'active' && alert.severity === 'critical' && 'bg-red-500/5',
    )}>
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0 pt-0.5">{severityIcon(alert.severity)}</div>
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2 flex-wrap">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-sm font-medium text-slate-100">{alert.title}</span>
              <span className={cn('px-1.5 py-0.5 text-[10px] rounded border', severityBadge(alert.severity))}>
                {alert.severity}
              </span>
              <span className={cn('px-1.5 py-0.5 text-[10px] rounded', statusBadge(alert.status))}>
                {alert.status}
              </span>
              <span className="px-1.5 py-0.5 text-[10px] bg-slate-700 text-slate-400 rounded">
                {alert.category}
              </span>
            </div>
            <span className="text-xs text-slate-500 flex-shrink-0">{formatRelativeTime(alert.createdAt)}</span>
          </div>
          <p className="text-xs text-slate-400 mt-1">{alert.message}</p>
          {alert.storeName && (
            <p className="text-xs text-slate-500 mt-0.5">Store: {alert.storeName}</p>
          )}
          {/* Actions */}
          {alert.status === 'active' && (
            <div className="flex gap-2 mt-2">
              <button
                onClick={() => acknowledge(alert.id)}
                disabled={acking}
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 rounded-lg min-h-[32px] transition-colors disabled:opacity-50"
              >
                <CheckCheck className="w-3.5 h-3.5" />
                Acknowledge
              </button>
              <button
                onClick={() => resolve(alert.id)}
                disabled={resolving}
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs bg-green-500/10 hover:bg-green-500/20 text-green-400 rounded-lg min-h-[32px] transition-colors disabled:opacity-50"
              >
                <Check className="w-3.5 h-3.5" />
                Resolve
              </button>
            </div>
          )}
          {alert.status === 'acknowledged' && (
            <div className="flex gap-2 mt-2">
              <button
                onClick={() => resolve(alert.id)}
                disabled={resolving}
                className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs bg-green-500/10 hover:bg-green-500/20 text-green-400 rounded-lg min-h-[32px] transition-colors disabled:opacity-50"
              >
                <Check className="w-3.5 h-3.5" />
                Resolve
              </button>
              {alert.acknowledgedBy && (
                <span className="text-xs text-slate-500 flex items-center">by {alert.acknowledgedBy}</span>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function RuleRow({ rule }: { rule: AlertRule }) {
  const { mutate: toggle, isPending } = useToggleAlertRule();
  const [optimisticEnabled, setOptimisticEnabled] = useState(rule.enabled);

  const handleToggle = () => {
    const next = !optimisticEnabled;
    setOptimisticEnabled(next);
    toggle({ id: rule.id, enabled: next }, {
      onError: () => setOptimisticEnabled(!next),
    });
  };

  return (
    <div className="flex items-start justify-between gap-4 py-3 border-b border-surface-border last:border-0">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-medium text-slate-100">{rule.name}</span>
          <span className={cn('px-1.5 py-0.5 text-[10px] rounded border', severityBadge(rule.severity))}>{rule.severity}</span>
          <span className="px-1.5 py-0.5 text-[10px] bg-slate-700 text-slate-400 rounded">{rule.category}</span>
        </div>
        <p className="text-xs text-slate-500 mt-0.5">{rule.description}</p>
      </div>
      <button
        role="switch"
        aria-checked={optimisticEnabled}
        onClick={handleToggle}
        disabled={isPending}
        className="flex-shrink-0 min-h-[44px] min-w-[44px] flex items-center justify-center disabled:opacity-50"
        aria-label={optimisticEnabled ? 'Disable rule' : 'Enable rule'}
      >
        <span className={cn(
          'relative inline-flex h-6 w-11 items-center rounded-full transition-colors duration-200',
          optimisticEnabled ? 'bg-brand-500' : 'bg-slate-600',
        )}>
          <span className={cn(
            'inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform duration-200',
            optimisticEnabled ? 'translate-x-6' : 'translate-x-1',
          )} />
        </span>
      </button>
    </div>
  );
}

function AlertsPage() {
  const [tab, setTab] = useState<'active' | 'history' | 'rules'>('active');
  const [filter, setFilter] = useState<AlertFilter>({ status: 'active', page: 1, pageSize: 20 });
  const [severityFilter, setSeverityFilter] = useState<AlertSeverity | ''>('');
  const [categoryFilter, setCategoryFilter] = useState<AlertCategory | ''>('');

  const effectiveFilter: AlertFilter = {
    ...filter,
    status: tab === 'active' ? 'active' : tab === 'history' ? undefined : undefined,
    severity: severityFilter || undefined,
    category: categoryFilter || undefined,
  };

  const { data: alertsPage, isLoading } = useAlerts(effectiveFilter);
  const { data: counts } = useAlertCounts();
  const { data: rules, isLoading: rulesLoading } = useAlertRules();

  const alerts = alertsPage?.items ?? [];

  return (
    <div className="p-4 md:p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Alerts</h1>
          <p className="text-sm text-slate-400 mt-1">Monitor and manage system alerts</p>
        </div>
        {/* Count badges */}
        <div className="flex items-center gap-2">
          {counts?.['critical'] ? (
            <span className="flex items-center gap-1 px-2 py-1 bg-red-500/20 text-red-400 text-xs font-medium rounded-full border border-red-500/30">
              <Zap className="w-3 h-3" />{counts['critical']} critical
            </span>
          ) : null}
          {counts?.['active'] ? (
            <span className="flex items-center gap-1 px-2 py-1 bg-amber-500/10 text-amber-400 text-xs font-medium rounded-full">
              <Bell className="w-3 h-3" />{counts['active']} active
            </span>
          ) : null}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-surface-elevated rounded-xl p-1">
        {[
          { id: 'active' as const, label: 'Active' },
          { id: 'history' as const, label: 'History' },
          { id: 'rules' as const, label: 'Alert Rules' },
        ].map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              'flex-1 py-2 px-3 rounded-lg text-sm font-medium transition-colors min-h-[44px]',
              tab === t.id ? 'bg-surface-card text-slate-100 shadow-sm' : 'text-slate-400 hover:text-slate-100',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Filters (for active/history tabs) */}
      {tab !== 'rules' && (
        <div className="flex gap-2 flex-wrap">
          <select
            value={severityFilter}
            onChange={e => setSeverityFilter(e.target.value as AlertSeverity | '')}
            className="h-10 px-3 bg-surface-elevated border border-surface-border rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500"
          >
            <option value="">All Severities</option>
            <option value="critical">Critical</option>
            <option value="high">High</option>
            <option value="medium">Medium</option>
            <option value="low">Low</option>
            <option value="info">Info</option>
          </select>
          <select
            value={categoryFilter}
            onChange={e => setCategoryFilter(e.target.value as AlertCategory | '')}
            className="h-10 px-3 bg-surface-elevated border border-surface-border rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500"
          >
            <option value="">All Categories</option>
            <option value="sync">Sync</option>
            <option value="license">License</option>
            <option value="payment">Payment</option>
            <option value="security">Security</option>
            <option value="system">System</option>
            <option value="store">Store</option>
          </select>
        </div>
      )}

      {/* Content */}
      {tab === 'rules' ? (
        <div className="bg-surface-card border border-surface-border rounded-xl p-4">
          {rulesLoading ? (
            <div className="space-y-3">{[1,2,3,4].map(i=><div key={i} className="h-14 bg-surface-elevated rounded animate-pulse"/>)}</div>
          ) : (rules??[]).length===0 ? (
            <p className="text-center py-6 text-slate-400">No alert rules configured</p>
          ) : (
            <div>{(rules??[]).map(r=><RuleRow key={r.id} rule={r}/>)}</div>
          )}
        </div>
      ) : (
        <div className="bg-surface-card border border-surface-border rounded-xl overflow-hidden">
          {isLoading ? (
            <div className="p-4 space-y-4">{[1,2,3,4,5].map(i=><div key={i} className="h-20 bg-surface-elevated rounded animate-pulse"/>)}</div>
          ) : alerts.length===0 ? (
            <div className="text-center py-12">
              <Bell className="w-10 h-10 text-slate-600 mx-auto mb-3"/>
              <p className="text-slate-400">{tab==='active' ? 'No active alerts' : 'No alerts in history'}</p>
            </div>
          ) : (
            <div>{alerts.map(a=><AlertRow key={a.id} alert={a}/>)}</div>
          )}
          {/* Pagination */}
          {(alertsPage?.total ?? 0) > (filter.pageSize ?? 20) && (
            <div className="p-4 border-t border-surface-border flex items-center justify-between">
              <span className="text-xs text-slate-500">
                Showing {Math.min((filter.page! - 1) * filter.pageSize! + 1, alertsPage!.total)}–{Math.min(filter.page! * filter.pageSize!, alertsPage!.total)} of {alertsPage!.total}
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setFilter(f => ({ ...f, page: (f.page ?? 1) - 1 }))}
                  disabled={(filter.page ?? 1) <= 1}
                  className="px-3 py-1.5 text-xs bg-surface-elevated rounded-lg text-slate-400 hover:text-slate-100 disabled:opacity-40 min-h-[36px]"
                >
                  Prev
                </button>
                <button
                  onClick={() => setFilter(f => ({ ...f, page: (f.page ?? 1) + 1 }))}
                  disabled={(filter.page ?? 1) * (filter.pageSize ?? 20) >= (alertsPage?.total ?? 0)}
                  className="px-3 py-1.5 text-xs bg-surface-elevated rounded-lg text-slate-400 hover:text-slate-100 disabled:opacity-40 min-h-[36px]"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
