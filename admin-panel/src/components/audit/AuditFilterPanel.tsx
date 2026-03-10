import { Filter, X } from 'lucide-react';
import { DateRangePicker } from '@/components/shared/DateRangePicker';
import { cn } from '@/lib/utils';
import type { AuditFilter, AuditEventCategory } from '@/types/audit';

const CATEGORIES: AuditEventCategory[] = ['AUTH', 'LICENSE', 'INVENTORY', 'ORDER', 'PAYMENT', 'USER', 'SETTINGS', 'SYNC', 'SYSTEM'];

interface AuditFilterPanelProps {
  filters: AuditFilter;
  onChange: (filters: AuditFilter) => void;
  className?: string;
}

export function AuditFilterPanel({ filters, onChange, className }: AuditFilterPanelProps) {
  const hasFilters = !!(filters.category || filters.from || filters.storeId || filters.success !== undefined);

  const update = (patch: Partial<AuditFilter>) => onChange({ ...filters, ...patch, page: 0 });
  const clear = () => onChange({ page: 0, size: 50 });

  return (
    <div className={cn('flex flex-wrap gap-3 items-end', className)}>
      {/* Category */}
      <select
        aria-label="Filter by category"
        value={filters.category ?? ''}
        onChange={(e) => update({ category: (e.target.value as AuditEventCategory) || undefined })}
        className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[130px]"
      >
        <option value="">All Categories</option>
        {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
      </select>

      {/* Success filter */}
      <select
        aria-label="Filter by result"
        value={filters.success === undefined ? '' : String(filters.success)}
        onChange={(e) => update({ success: e.target.value === '' ? undefined : e.target.value === 'true' })}
        className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[120px]"
      >
        <option value="">All Results</option>
        <option value="true">Success</option>
        <option value="false">Failed</option>
      </select>

      {/* Date range */}
      <DateRangePicker
        value={filters.from && filters.to ? { from: filters.from, to: filters.to } : undefined}
        onChange={(range) => update({ from: range?.from, to: range?.to })}
        className="min-w-[180px]"
      />

      {/* Clear */}
      {hasFilters && (
        <button
          onClick={clear}
          className="flex items-center gap-1.5 h-10 px-3 text-sm text-slate-400 hover:text-slate-100 border border-surface-border rounded-lg hover:bg-surface-elevated transition-colors min-w-[44px]"
        >
          <X className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Clear</span>
        </button>
      )}

      {hasFilters && (
        <div className="flex items-center gap-1.5 h-10 px-3 text-xs text-brand-400 border border-brand-500/30 bg-brand-500/10 rounded-lg">
          <Filter className="w-3.5 h-3.5" />
          Filtered
        </div>
      )}
    </div>
  );
}
