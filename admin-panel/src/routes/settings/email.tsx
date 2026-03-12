import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Mail, CheckCircle, XCircle, AlertTriangle, Clock } from 'lucide-react';
import { useEmailDeliveryLogs, type EmailDeliveryLog } from '@/api/email';
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

function EmailSettingsPage() {
  const [page, setPage] = useState(1);
  const { data, isLoading, isError } = useEmailDeliveryLogs(page);
  const { formatDateTime } = useTimezone();

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h2 className="text-xl font-semibold flex items-center gap-2">
          <Mail className="h-5 w-5" />
          Email Delivery Logs
        </h2>
        <p className="text-sm text-muted-foreground mt-1">
          Track transactional email delivery status and troubleshoot failures.
        </p>
      </div>

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
          No email delivery logs found.
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
                    <tr key={log.id} className="hover:bg-muted/30">
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
                        {formatDateTime(log.sentAt)}
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
