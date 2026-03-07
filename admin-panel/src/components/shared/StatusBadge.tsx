import { cn } from '@/lib/utils';

type StatusVariant =
  | 'ACTIVE'
  | 'EXPIRED'
  | 'REVOKED'
  | 'SUSPENDED'
  | 'EXPIRING_SOON'
  | 'HEALTHY'
  | 'WARNING'
  | 'CRITICAL'
  | 'OFFLINE'
  | 'SYNCED'
  | 'PENDING'
  | 'SYNCING'
  | 'FAILED'
  | 'STALE'
  | 'OK'
  | 'FIRING'
  | 'RESOLVED';

const STATUS_STYLES: Record<StatusVariant, string> = {
  ACTIVE:        'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  HEALTHY:       'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  SYNCED:        'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  OK:            'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  RESOLVED:      'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',

  EXPIRING_SOON: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
  WARNING:       'bg-amber-500/15 text-amber-400 border-amber-500/30',
  PENDING:       'bg-amber-500/15 text-amber-400 border-amber-500/30',
  SYNCING:       'bg-blue-500/15 text-blue-400 border-blue-500/30',

  EXPIRED:       'bg-red-500/15 text-red-400 border-red-500/30',
  REVOKED:       'bg-red-500/15 text-red-400 border-red-500/30',
  CRITICAL:      'bg-red-500/15 text-red-400 border-red-500/30',
  FAILED:        'bg-red-500/15 text-red-400 border-red-500/30',
  FIRING:        'bg-red-500/15 text-red-400 border-red-500/30',

  SUSPENDED:     'bg-slate-500/15 text-slate-400 border-slate-500/30',
  OFFLINE:       'bg-slate-500/15 text-slate-400 border-slate-500/30',
  STALE:         'bg-slate-500/15 text-slate-400 border-slate-500/30',
};

interface StatusBadgeProps {
  status: StatusVariant | string;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const styles = STATUS_STYLES[status as StatusVariant] ?? 'bg-slate-500/15 text-slate-400 border-slate-500/30';
  const label = status.replace(/_/g, ' ');

  return (
    <span
      className={cn(
        'inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold uppercase tracking-wide border',
        styles,
        className,
      )}
    >
      {label}
    </span>
  );
}
