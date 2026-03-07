import { X, CheckCircle, XCircle } from 'lucide-react';
import { formatDateTime } from '@/lib/utils';
import type { AuditEntry } from '@/types/audit';

interface AuditDetailModalProps {
  entry: AuditEntry | null;
  onClose: () => void;
}

function JsonDiff({ label, data }: { label: string; data: Record<string, unknown> | null }) {
  if (!data) return null;
  return (
    <div>
      <p className="text-xs font-medium text-slate-400 mb-1">{label}</p>
      <pre className="text-xs text-slate-300 bg-surface-elevated rounded-lg p-3 overflow-x-auto max-h-48 font-mono">
        {JSON.stringify(data, null, 2)}
      </pre>
    </div>
  );
}

export function AuditDetailModal({ entry, onClose }: AuditDetailModalProps) {
  if (!entry) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-lg max-h-[85vh] overflow-y-auto animate-fade-in">
        <div className="flex items-center justify-between p-6 border-b border-surface-border sticky top-0 bg-surface-card z-10">
          <div className="flex items-center gap-3">
            {entry.success
              ? <CheckCircle className="w-5 h-5 text-emerald-400" />
              : <XCircle className="w-5 h-5 text-red-400" />
            }
            <h3 className="text-base font-semibold text-slate-100 truncate">{entry.eventType}</h3>
          </div>
          <button onClick={onClose} className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          {[
            ['Timestamp', formatDateTime(entry.createdAt)],
            ['Category', entry.category],
            ['User', entry.userName ?? '—'],
            ['Store', entry.storeName ?? '—'],
            ['Entity', entry.entityType ? `${entry.entityType} / ${entry.entityId}` : '—'],
            ['IP Address', entry.ipAddress ?? '—'],
            ['Result', entry.success ? 'Success' : `Failed: ${entry.errorMessage ?? 'Unknown error'}`],
          ].map(([label, value]) => (
            <div key={label} className="flex gap-4">
              <span className="text-xs font-medium text-slate-400 w-24 flex-shrink-0 pt-0.5">{label}</span>
              <span className={`text-sm text-slate-200 break-all ${label === 'Result' && !entry.success ? 'text-red-400' : ''}`}>{value}</span>
            </div>
          ))}

          {(entry.previousValues || entry.newValues) && (
            <div className="border-t border-surface-border pt-4 space-y-3">
              <JsonDiff label="Previous Values" data={entry.previousValues} />
              <JsonDiff label="New Values" data={entry.newValues} />
            </div>
          )}

          <div className="border-t border-surface-border pt-4">
            <p className="text-xs font-medium text-slate-400 mb-1">Hash Chain</p>
            <p className="font-mono text-[10px] text-slate-500 break-all">{entry.hashChain}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
