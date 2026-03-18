import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Mail, CheckCircle, XCircle, AlertTriangle, Clock, Filter, X } from 'lucide-react';
import {
  useEmailDeliveryLogs,
  type EmailDeliveryLog,
  type EmailStatus,
  type EmailLogFilters,
} from '@/api/email';
import { useTimezone } from '@/hooks/use-timezone';

export const Route = createFileRoute('/settings/email')({
  component: EmailSettingsPage,
});

const STATUS_CONFIG = {
  SENT: { icon: Clock, color: 'text-blue-500', bg: 'bg-blue-50', label: 'Sent' },
  DELIVERED: { icon: CheckCircle, color: 'text-green-500', bg: 'bg-green-50', label: 'Delivered' },
  BOUNCED: { icon: AlertTriangle, color: 'text-yellow-500', bg: 'bg-yellow-50', label: 'Bounced' },
  FAILED: { icon: XCircle, color: 'text-red-500', bg: 'bg-red-50', label: 'Failed' },
} as const;

const STATUSES: EmailStatus[] = ['SENT', 'DELIVERED', 'BOUNCED', 'FAILED'];

function EmailSettingsPage() {
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState<EmailLogFilters>({});
  const [showFilters, setShowFilters] = useState(false);
  const { data, isLoading, isError } = useEmailDeliveryLogs(page, 20, filters);
  const { formatDateTime } = useTimezone();

  const hasActiveFilters = filters.status || filters.startDate || filters.endDate;

  const updateFilter = (key: keyof EmailLogFilters, value: string | undefined) => {
    setPage(1);
    setFilters((prev) => ({ ...prev, [key]: value || undefined }));
  };

  const clearFilters = () => {
    setPage(1);
    setFilters({});
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold flex items-center gap-2">
            <Mail className="h-5 w-5" />
            Email Delivery Logs
          </h2>
          <p className="text-sm text-muted-foreground mt-1">
            Track transactional email delivery status and troubleshoot failures.
          </p>
        </div>
        <button
          onClick={() => setShowFilters(!showFilters)}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-sm border rounded-md transition-colors ${
            hasActiveFilters
              ? 'border-blue-300 bg-blue-50 text-blue-700'
              : 'hover:bg-muted/50'
          }`}
        >
          <Filter className="h-4 w-4" />
          Filters
          {hasActiveFilters && (
            <span className="ml-1 px-1.5 py-0.5 text-xs bg-blue-200 text-blue-800 rounded-full">
              {[filters.status, filters.startDate, filters.endDate].filter(Boolean).length}
            </span>
          )}
        </button>
      </div>

      {/* Filter bar */}
      {showFilters && (
        <div className="flex flex-wrap items-end gap-4 p-4 bg-muted/30 rounded-lg border">
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground">Status</label>
            <select
              value={filters.status || ''}
              onChange={(e) => updateFilter('status', e.target.value as EmailStatus)}
              className="px-3 py-1.5 text-sm border rounded-md bg-background"
            >
              <option value="">All statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_CONFIG[s].label}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground">From</label>
            <input
              type="date"
              value={filters.startDate || ''}
              onChange={(e) => updateFilter('startDate', e.target.value)}
              className="px-3 py-1.5 text-sm border rounded-md bg-background"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground">To</label>
            <input
              type="date"
              value={filters.endDate || ''}
              onChange={(e) => updateFilter('endDate', e.target.value)}
              className="px-3 py-1.5 text-sm border rounded-md bg-background"
            />
          </div>

          {hasActiveFilters && (
            <button
              onClick={clearFilters}
              className="flex items-center gap-1 px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
            >
              <X className="h-3.5 w-3.5" />
              Clear
            </button>
          )}
        </div>
      )}

      {isLoading && (
        <div className="text-center py-12 text-muted-foreground">Loading delivery logs...</div>
      )}

      {isError && (
        <div className="text-center py-12 text-destructive">
          Failed to load delivery logs. The email delivery log endpoint may not be configured yet.
        </div>
      )}

      {data && data.logs.length === 0 && (
        <div className="text-center py-12 text-muted-foreground">
          {hasActiveFilters
            ? 'No emails match the current filters.'
            : 'No email delivery logs found.'}
        </div>
      )}

      {data && data.logs.length > 0 && (
        <>
          <div className="border rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="text-left px-4 py-3 font-medium">Recipient</th>
                  <th className="text-left px-4 py-3 font-medium">Subject</th>
                  <th className="text-left px-4 py-3 font-medium">Template</th>
                  <th className="text-left px-4 py-3 font-medium">Status</th>
                  <th className="text-left px-4 py-3 font-medium">Sent At</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {data.logs.map((log: EmailDeliveryLog) => {
                  const config = STATUS_CONFIG[log.status];
                  const StatusIcon = config.icon;
                  return (
                    <tr key={log.id} className="hover:bg-muted/30" title={log.error || undefined}>
                      <td className="px-4 py-3 font-mono text-xs">{log.to}</td>
                      <td className="px-4 py-3 truncate max-w-[200px]">{log.subject}</td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs bg-muted">
                          {log.template}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs ${config.bg} ${config.color}`}>
                          <StatusIcon className="h-3 w-3" />
                          {config.label}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground text-xs">
                        {log.sentAt ? formatDateTime(new Date(log.sentAt).getTime()) : '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          <div className="flex items-center justify-between text-sm text-muted-foreground">
            <span>
              Showing {(page - 1) * 20 + 1}–{Math.min(page * 20, data.total)} of {data.total}
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page <= 1}
                className="px-3 py-1 border rounded disabled:opacity-50"
              >
                Previous
              </button>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={page * 20 >= data.total}
                className="px-3 py-1 border rounded disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
