import { AlertCircle } from 'lucide-react';
import { formatRelativeTime, truncate } from '@/lib/utils';
import { TableSkeleton } from '@/components/shared/LoadingState';
import { EmptyState } from '@/components/shared/EmptyState';
import type { SyncOperation } from '@/types/sync';

interface SyncQueueViewProps {
  operations: SyncOperation[];
  isLoading: boolean;
}

const OP_COLORS: Record<string, string> = {
  CREATE: 'text-emerald-400 bg-emerald-400/10',
  UPDATE: 'text-brand-400 bg-brand-400/10',
  DELETE: 'text-red-400 bg-red-400/10',
};

export function SyncQueueView({ operations, isLoading }: SyncQueueViewProps) {
  if (isLoading) return <TableSkeleton rows={4} />;
  if (operations.length === 0) return <EmptyState title="Queue empty" description="No pending sync operations." />;

  return (
    <div className="overflow-x-auto rounded-lg border border-surface-border">
      <table className="w-full text-sm min-w-[500px]">
        <thead>
          <tr className="border-b border-surface-border bg-surface-elevated">
            {['Entity', 'Operation', 'Retries', 'Client Time', 'Error'].map((h) => (
              <th key={h} className="px-4 py-2.5 text-left text-[11px] font-semibold uppercase tracking-wide text-slate-400">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-surface-border">
          {operations.map((op) => (
            <tr key={op.id} className="hover:bg-surface-elevated/50">
              <td className="px-4 py-3">
                <p className="text-slate-200 text-xs font-medium">{op.entityType}</p>
                <p className="text-slate-500 text-[10px] font-mono">{truncate(op.entityId, 12)}</p>
              </td>
              <td className="px-4 py-3">
                <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold ${OP_COLORS[op.operationType] ?? 'text-slate-400 bg-slate-400/10'}`}>
                  {op.operationType}
                </span>
              </td>
              <td className="px-4 py-3">
                <span className={`text-xs font-medium ${op.retryCount > 0 ? 'text-amber-400' : 'text-slate-400'}`}>
                  {op.retryCount}
                </span>
              </td>
              <td className="px-4 py-3 text-slate-400 text-xs whitespace-nowrap">{formatRelativeTime(op.clientTimestamp)}</td>
              <td className="px-4 py-3">
                {op.lastErrorMessage ? (
                  <div className="flex items-center gap-1.5 text-red-400">
                    <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
                    <span className="text-xs">{truncate(op.lastErrorMessage, 30)}</span>
                  </div>
                ) : <span className="text-slate-500 text-xs">—</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
