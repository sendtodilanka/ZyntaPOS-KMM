import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useState, useEffect } from 'react';
import { ArrowLeft, AlertTriangle, Clock, User } from 'lucide-react';
import { TicketStatusBadge, TicketPriorityBadge } from '@/components/tickets/TicketStatusBadge';
import { TicketAssignModal } from '@/components/tickets/TicketAssignModal';
import { TicketResolveModal } from '@/components/tickets/TicketResolveModal';
import { TicketCommentThread } from '@/components/tickets/TicketCommentThread';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { PageLoader } from '@/components/shared/LoadingState';
import { useTicket, useCloseTicket } from '@/api/tickets';
import { useAuth } from '@/hooks/use-auth';
import { useTimezone } from '@/hooks/use-timezone';

export const Route = createFileRoute('/tickets/$ticketId')({
  component: TicketDetailPage,
});

function SlaStatus({ slaDueAt, slaBreached, status, now }: { slaDueAt: number | null; slaBreached: boolean; status: string; now: number }) {
  if (status === 'RESOLVED' || status === 'CLOSED') return <span className="text-slate-500 text-sm">—</span>;
  if (!slaDueAt) return <span className="text-slate-500 text-sm">No SLA</span>;

  if (slaBreached || slaDueAt < now) {
    return (
      <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-red-400">
        <AlertTriangle className="w-4 h-4" /> SLA Breached
      </span>
    );
  }

  const remaining = slaDueAt - now;
  const hours = Math.floor(remaining / 3_600_000);
  const mins = Math.floor((remaining % 3_600_000) / 60_000);
  const label = hours > 0 ? `${hours}h ${mins}m remaining` : `${mins}m remaining`;
  const isWarning = remaining < 3_600_000 * 2;

  return (
    <span className={`inline-flex items-center gap-1.5 text-sm ${isWarning ? 'text-amber-400' : 'text-slate-300'}`}>
      <Clock className="w-4 h-4" /> {label}
    </span>
  );
}

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium text-slate-500 mb-1">{label}</dt>
      <dd className="text-sm text-slate-300">{children}</dd>
    </div>
  );
}

function TicketDetailPage() {
  const { ticketId } = Route.useParams();
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const { formatDateTime } = useTimezone();

  const { data: ticket, isLoading } = useTicket(ticketId);
  const closeTicket = useCloseTicket();
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(id);
  }, []);

  const [assignOpen, setAssignOpen] = useState(false);
  const [resolveOpen, setResolveOpen] = useState(false);
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false);

  if (isLoading) return <PageLoader />;
  if (!ticket) return <div className="text-center py-16 text-slate-400">Ticket not found.</div>;

  const canAssign = hasPermission('tickets:assign');
  const canResolve = hasPermission('tickets:resolve');
  const canClose = hasPermission('tickets:close');

  const showResolveButton = canResolve && ticket.status !== 'RESOLVED' && ticket.status !== 'CLOSED';
  const showCloseButton = canClose && ticket.status === 'RESOLVED';
  const showAssignButton = canAssign && ticket.status !== 'RESOLVED' && ticket.status !== 'CLOSED';

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start gap-3">
        <button
          onClick={() => navigate({ to: '/tickets' })}
          className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated transition-colors min-w-[44px] min-h-[44px] flex items-center justify-center flex-shrink-0 mt-0.5"
          aria-label="Back to tickets"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2 mb-1">
            <span className="font-mono text-xs text-brand-400 font-semibold">{ticket.ticketNumber}</span>
            <TicketStatusBadge status={ticket.status} />
            <TicketPriorityBadge priority={ticket.priority} />
          </div>
          <h1 className="panel-title">{ticket.title}</h1>
          <p className="text-sm text-slate-400 mt-0.5">{ticket.category} · Created by {ticket.createdByName}</p>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          {showAssignButton && (
            <button
              onClick={() => setAssignOpen(true)}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-slate-300 border border-surface-border rounded-lg hover:bg-surface-elevated transition-colors min-h-[40px]"
            >
              <User className="w-4 h-4" />
              {ticket.assignedTo ? 'Reassign' : 'Assign'}
            </button>
          )}
          {showResolveButton && (
            <button
              onClick={() => setResolveOpen(true)}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors min-h-[40px]"
            >
              Mark Resolved
            </button>
          )}
          {showCloseButton && (
            <button
              onClick={() => setCloseConfirmOpen(true)}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium bg-slate-600 text-white rounded-lg hover:bg-slate-700 transition-colors min-h-[40px]"
            >
              Close Ticket
            </button>
          )}
        </div>
      </div>

      {/* Main content */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: details + description + comments */}
        <div className="lg:col-span-2 space-y-6">
          {/* Description */}
          <div className="bg-surface-card border border-surface-border rounded-xl p-5">
            <h2 className="text-sm font-semibold text-slate-300 mb-3">Description</h2>
            <p className="text-sm text-slate-400 whitespace-pre-wrap">{ticket.description || 'No description provided.'}</p>
          </div>

          {/* Resolution note (if resolved) */}
          {ticket.resolutionNote && (
            <div className="bg-emerald-500/5 border border-emerald-500/20 rounded-xl p-5">
              <h2 className="text-sm font-semibold text-emerald-400 mb-2">Resolution</h2>
              <p className="text-sm text-slate-300 whitespace-pre-wrap">{ticket.resolutionNote}</p>
              {ticket.timeSpentMin && (
                <p className="text-xs text-slate-500 mt-2">Time spent: {ticket.timeSpentMin} min</p>
              )}
            </div>
          )}

          {/* Comments */}
          <div className="bg-surface-card border border-surface-border rounded-xl p-5">
            <TicketCommentThread ticketId={ticket.id} />
          </div>
        </div>

        {/* Right: metadata */}
        <div className="space-y-4">
          <div className="bg-surface-card border border-surface-border rounded-xl p-5">
            <h2 className="text-sm font-semibold text-slate-300 mb-4">Details</h2>
            <dl className="space-y-3">
              <InfoRow label="Status"><TicketStatusBadge status={ticket.status} /></InfoRow>
              <InfoRow label="Priority"><TicketPriorityBadge priority={ticket.priority} /></InfoRow>
              <InfoRow label="Category">{ticket.category}</InfoRow>
              <InfoRow label="SLA">
                <SlaStatus slaDueAt={ticket.slaDueAt} slaBreached={ticket.slaBreached} status={ticket.status} now={now} />
              </InfoRow>
            </dl>
          </div>

          <div className="bg-surface-card border border-surface-border rounded-xl p-5">
            <h2 className="text-sm font-semibold text-slate-300 mb-4">Customer</h2>
            <dl className="space-y-3">
              <InfoRow label="Name">{ticket.customerName}</InfoRow>
              {ticket.customerEmail && <InfoRow label="Email">{ticket.customerEmail}</InfoRow>}
              {ticket.customerPhone && <InfoRow label="Phone">{ticket.customerPhone}</InfoRow>}
            </dl>
          </div>

          <div className="bg-surface-card border border-surface-border rounded-xl p-5">
            <h2 className="text-sm font-semibold text-slate-300 mb-4">Assignment</h2>
            <dl className="space-y-3">
              <InfoRow label="Assigned To">
                {ticket.assignedToName ?? <span className="text-slate-500">Unassigned</span>}
              </InfoRow>
              {ticket.assignedAt && (
                <InfoRow label="Assigned At">{formatDateTime(ticket.assignedAt)}</InfoRow>
              )}
              <InfoRow label="Created By">{ticket.createdByName}</InfoRow>
              <InfoRow label="Created">{formatDateTime(ticket.createdAt)}</InfoRow>
              <InfoRow label="Updated">{formatDateTime(ticket.updatedAt)}</InfoRow>
            </dl>
          </div>

          {ticket.storeId && (
            <div className="bg-surface-card border border-surface-border rounded-xl p-5">
              <h2 className="text-sm font-semibold text-slate-300 mb-4">Store</h2>
              <dl className="space-y-3">
                <InfoRow label="Store ID">
                  <span className="font-mono text-xs text-slate-400">{ticket.storeId}</span>
                </InfoRow>
              </dl>
            </div>
          )}
        </div>
      </div>

      {/* Modals */}
      {assignOpen && (
        <TicketAssignModal ticket={ticket} open={assignOpen} onClose={() => setAssignOpen(false)} />
      )}
      {resolveOpen && (
        <TicketResolveModal ticket={ticket} open={resolveOpen} onClose={() => setResolveOpen(false)} />
      )}
      <ConfirmDialog
        open={closeConfirmOpen}
        onClose={() => setCloseConfirmOpen(false)}
        onConfirm={() => {
          closeTicket.mutate(ticket.id, { onSettled: () => setCloseConfirmOpen(false) });
        }}
        title="Close Ticket"
        description={`Close ${ticket.ticketNumber}? This action marks the ticket as permanently closed. The ticket must be in RESOLVED status.`}
        confirmLabel="Close Ticket"
        variant="destructive"
        isLoading={closeTicket.isPending}
      />
    </div>
  );
}
