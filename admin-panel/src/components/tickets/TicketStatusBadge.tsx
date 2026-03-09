import { cn } from '@/lib/utils';
import type { TicketStatus, TicketPriority } from '@/types/ticket';

const STATUS_STYLES: Record<TicketStatus, string> = {
  OPEN:             'bg-blue-500/15 text-blue-400 border-blue-500/30',
  ASSIGNED:         'bg-amber-500/15 text-amber-400 border-amber-500/30',
  IN_PROGRESS:      'bg-orange-500/15 text-orange-400 border-orange-500/30',
  PENDING_CUSTOMER: 'bg-purple-500/15 text-purple-400 border-purple-500/30',
  RESOLVED:         'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
  CLOSED:           'bg-slate-500/15 text-slate-400 border-slate-500/30',
};

const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN:             'Open',
  ASSIGNED:         'Assigned',
  IN_PROGRESS:      'In Progress',
  PENDING_CUSTOMER: 'Pending Customer',
  RESOLVED:         'Resolved',
  CLOSED:           'Closed',
};

const PRIORITY_STYLES: Record<TicketPriority, string> = {
  LOW:      'bg-slate-500/15 text-slate-400 border-slate-500/30',
  MEDIUM:   'bg-blue-500/15 text-blue-400 border-blue-500/30',
  HIGH:     'bg-amber-500/15 text-amber-400 border-amber-500/30',
  CRITICAL: 'bg-red-500/15 text-red-400 border-red-500/30',
};

interface TicketStatusBadgeProps {
  status: TicketStatus;
  className?: string;
}

interface TicketPriorityBadgeProps {
  priority: TicketPriority;
  className?: string;
}

export function TicketStatusBadge({ status, className }: TicketStatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold tracking-wide border',
        STATUS_STYLES[status],
        className,
      )}
    >
      {STATUS_LABELS[status]}
    </span>
  );
}

export function TicketPriorityBadge({ priority, className }: TicketPriorityBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold uppercase tracking-wide border',
        PRIORITY_STYLES[priority],
        className,
      )}
    >
      {priority}
    </span>
  );
}
