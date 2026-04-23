import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Shield, AlertTriangle, Users, Activity,
  LogIn, Ban, Monitor, ShieldCheck,
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { KpiCard } from '@/components/shared/KpiCard';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { formatRelativeTime, formatDateTime } from '@/lib/utils';
import type { AuditEntry } from '@/types/audit';
import type { PagedResponse } from '@/types/api';

export const Route = createFileRoute('/security/')({
  component: SecurityPage,
});

// ── Types ────────────────────────────────────────────────────────────────────

interface SecurityMetrics {
  totalLoginAttempts24h: number;
  failedLogins24h: number;
  activeSessions: number;
  blockedIps: number;
}

interface ActiveAdminSession {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
  expiresAt: string;
}

interface VulnerabilityScanStatus {
  lastScanDate: string | null;
  scanType: string;
  issuesFound: number;
  criticalCount: number;
  highCount: number;
  status: 'passed' | 'failed' | 'unknown';
}

// ── API hooks ────────────────────────────────────────────────────────────────

function useSecurityMetrics() {
  return useQuery({
    queryKey: ['security', 'metrics'],
    queryFn: () => apiClient.get('admin/security/metrics').json<SecurityMetrics>(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}

function useSecurityEvents() {
  const qs = new URLSearchParams({
    page: '0',
    size: '20',
    category: 'AUTH',
  });
  return useQuery({
    queryKey: ['security', 'events'],
    queryFn: () => apiClient.get(`admin/audit?${qs}`).json<PagedResponse<AuditEntry>>(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}

function useActiveAdminSessions() {
  return useQuery({
    queryKey: ['security', 'sessions'],
    queryFn: () => apiClient.get('admin/security/sessions').json<ActiveAdminSession[]>(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}

function useVulnerabilityScan() {
  return useQuery({
    queryKey: ['security', 'vuln-scan'],
    queryFn: () => apiClient.get('admin/health/system').json<{ vulnerabilityScan?: VulnerabilityScanStatus }>(),
    staleTime: 60_000,
  });
}

// ── Page ─────────────────────────────────────────────────────────────────────

function SecurityPage() {
  const metricsQ = useSecurityMetrics();
  const eventsQ = useSecurityEvents();
  const sessionsQ = useActiveAdminSessions();
  const scanQ = useVulnerabilityScan();

  const { data: metrics, isLoading: metricsLoading, isError: metricsError } = metricsQ;
  const { data: eventsPage, isLoading: eventsLoading, isError: eventsError } = eventsQ;
  const { data: sessions, isLoading: sessionsLoading, isError: sessionsError } = sessionsQ;
  const { data: healthData, isLoading: scanLoading, isError: scanError } = scanQ;

  const anyError = metricsError || eventsError || sessionsError || scanError;
  const retryFailed = () => {
    if (metricsError) metricsQ.refetch();
    if (eventsError) eventsQ.refetch();
    if (sessionsError) sessionsQ.refetch();
    if (scanError) scanQ.refetch();
  };

  const events = eventsPage?.data ?? [];
  const scanStatus = healthData?.vulnerabilityScan;

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Security Dashboard</h1>
          <p className="panel-subtitle">Authentication activity, sessions, and threat monitoring</p>
        </div>
        <Shield className="w-5 h-5 text-brand-400" />
      </div>

      {anyError && (
        <ErrorBanner
          message="One or more security data sources failed to load — threat monitoring may be incomplete."
          onRetry={retryFailed}
        />
      )}

      {/* Threat Overview KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          title="Login Attempts (24h)"
          value={metrics?.totalLoginAttempts24h ?? 0}
          subtitle="total attempts"
          icon={LogIn}
          iconColor="text-brand-400"
          isLoading={metricsLoading}
        />
        <KpiCard
          title="Failed Logins (24h)"
          value={metrics?.failedLogins24h ?? 0}
          subtitle="failed attempts"
          icon={AlertTriangle}
          iconColor="text-red-400"
          isLoading={metricsLoading}
        />
        <KpiCard
          title="Active Sessions"
          value={metrics?.activeSessions ?? 0}
          subtitle="across all admins"
          icon={Users}
          iconColor="text-emerald-400"
          isLoading={metricsLoading}
        />
        <KpiCard
          title="Blocked IPs"
          value={metrics?.blockedIps ?? 0}
          subtitle="brute-force lockouts"
          icon={Ban}
          iconColor="text-amber-400"
          isLoading={metricsLoading}
        />
      </div>

      {/* Middle row — Events table + Vulnerability scan */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Recent Security Events — 2/3 width */}
        <div className="panel-card lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="panel-title text-base">Recent Security Events</h2>
              <p className="panel-subtitle text-xs">Last 20 AUTH audit entries</p>
            </div>
            <Activity className="w-4 h-4 text-slate-500" />
          </div>

          {eventsLoading && (
            <div className="space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="h-12 rounded-lg bg-surface-elevated animate-pulse" />
              ))}
            </div>
          )}

          {!eventsLoading && events.length === 0 && (
            <p className="text-xs text-slate-500 py-8 text-center">No recent security events.</p>
          )}

          {!eventsLoading && events.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b border-slate-700/50">
                    <th className="text-left py-2 px-2 text-slate-400 font-medium">Time</th>
                    <th className="text-left py-2 px-2 text-slate-400 font-medium">Event</th>
                    <th className="text-left py-2 px-2 text-slate-400 font-medium">User</th>
                    <th className="text-left py-2 px-2 text-slate-400 font-medium">IP Address</th>
                    <th className="text-left py-2 px-2 text-slate-400 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {events.map((event) => (
                    <tr key={event.id} className="border-b border-slate-800/50 hover:bg-surface-elevated transition-colors">
                      <td className="py-2 px-2 text-slate-400 whitespace-nowrap" title={formatDateTime(event.createdAt)}>
                        {formatRelativeTime(event.createdAt)}
                      </td>
                      <td className="py-2 px-2 text-slate-200 font-medium">{event.eventType}</td>
                      <td className="py-2 px-2 text-slate-300">{event.userName ?? '—'}</td>
                      <td className="py-2 px-2 text-slate-400 font-mono">{event.ipAddress ?? '—'}</td>
                      <td className="py-2 px-2">
                        <StatusBadge status={event.success ? 'OK' : 'FAILED'} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Vulnerability Scan Status — 1/3 width */}
        <div className="panel-card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="panel-title text-base">Vulnerability Scan</h2>
            <ShieldCheck className="w-4 h-4 text-slate-500" />
          </div>

          {scanLoading && (
            <div className="space-y-3">
              <div className="h-16 rounded-lg bg-surface-elevated animate-pulse" />
              <div className="h-4 w-32 rounded bg-surface-elevated animate-pulse" />
            </div>
          )}

          {!scanLoading && !scanStatus && (
            <div className="text-center py-6 space-y-3">
              <ShieldCheck className="w-10 h-10 text-slate-600 mx-auto" />
              <div>
                <p className="text-sm text-slate-300">No scan data available</p>
                <p className="text-xs text-slate-500 mt-1">
                  OWASP/Snyk scans run in CI pipeline.
                  Check GitHub Actions for latest results.
                </p>
              </div>
              <div className="rounded-lg bg-surface-elevated p-3 text-left">
                <p className="text-[11px] text-slate-400 font-medium uppercase tracking-wide mb-1">CI Scan Checks</p>
                <p className="text-xs text-slate-500">OWASP Dependency Check + NVD API</p>
                <p className="text-xs text-slate-500">Detekt static analysis</p>
                <p className="text-xs text-slate-500">Android Lint security rules</p>
              </div>
            </div>
          )}

          {!scanLoading && scanStatus && (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <StatusBadge status={scanStatus.status === 'passed' ? 'OK' : scanStatus.status === 'failed' ? 'CRITICAL' : 'WARNING'} />
                <span className="text-sm text-slate-200 font-medium">{scanStatus.scanType}</span>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-lg bg-surface-elevated p-3">
                  <p className="text-[11px] text-slate-400 font-medium uppercase tracking-wide">Issues</p>
                  <p className="text-lg font-bold text-slate-100 tabular-nums">{scanStatus.issuesFound}</p>
                </div>
                <div className="rounded-lg bg-surface-elevated p-3">
                  <p className="text-[11px] text-slate-400 font-medium uppercase tracking-wide">Critical</p>
                  <p className="text-lg font-bold text-red-400 tabular-nums">{scanStatus.criticalCount}</p>
                </div>
              </div>
              {scanStatus.lastScanDate && (
                <p className="text-xs text-slate-500">Last scan: {formatRelativeTime(scanStatus.lastScanDate)}</p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Active Admin Sessions */}
      <div className="panel-card">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="panel-title text-base">Active Admin Sessions</h2>
            <p className="panel-subtitle text-xs">Currently authenticated admin users</p>
          </div>
          <Monitor className="w-4 h-4 text-slate-500" />
        </div>

        {sessionsLoading && (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="h-14 rounded-lg bg-surface-elevated animate-pulse" />
            ))}
          </div>
        )}

        {!sessionsLoading && (!sessions || sessions.length === 0) && (
          <p className="text-xs text-slate-500 py-4 text-center">No active sessions found.</p>
        )}

        {!sessionsLoading && sessions && sessions.length > 0 && (
          <div className="space-y-2">
            {sessions.map((session) => (
              <div key={session.id} className="flex items-center gap-4 p-3 rounded-lg bg-surface-elevated">
                <div className="w-8 h-8 rounded-full bg-brand-700/30 flex items-center justify-center flex-shrink-0">
                  <Users className="w-4 h-4 text-brand-400" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="text-xs font-medium text-slate-200 truncate">{session.userName}</p>
                    <span className="text-[10px] text-slate-500">{session.userEmail}</span>
                  </div>
                  <div className="flex items-center gap-3 mt-0.5">
                    {session.ipAddress && (
                      <span className="text-[11px] text-slate-400 font-mono">{session.ipAddress}</span>
                    )}
                    {session.userAgent && (
                      <span className="text-[11px] text-slate-500 truncate max-w-[300px]">{parseUserAgent(session.userAgent)}</span>
                    )}
                  </div>
                </div>
                <div className="text-right flex-shrink-0">
                  <p className="text-[11px] text-slate-400">Started {formatRelativeTime(session.createdAt)}</p>
                  <p className="text-[10px] text-slate-500">Expires {formatRelativeTime(session.expiresAt)}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Extract a short browser/OS label from a full User-Agent string. */
function parseUserAgent(ua: string): string {
  if (ua.includes('Chrome') && !ua.includes('Edg')) return 'Chrome';
  if (ua.includes('Edg')) return 'Edge';
  if (ua.includes('Firefox')) return 'Firefox';
  if (ua.includes('Safari') && !ua.includes('Chrome')) return 'Safari';
  if (ua.includes('curl')) return 'curl';
  return ua.length > 40 ? `${ua.slice(0, 40)}...` : ua;
}
