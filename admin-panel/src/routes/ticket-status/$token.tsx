import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle2, Clock, Ticket } from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { TicketStatusBadge, TicketPriorityBadge } from '@/components/tickets/TicketStatusBadge';
import type { TicketStatus, TicketPriority } from '@/types/ticket';

interface PublicTicketView {
  ticketNumber: string;
  status: TicketStatus;
  priority: TicketPriority;
  title: string;
  category: string;
  slaBreached: boolean;
  createdAt: number;
  updatedAt: number;
}

export const Route = createFileRoute('/ticket-status/$token')({
  component: TicketStatusPage,
});

function TicketStatusPage() {
  const { token } = Route.useParams();

  const { data: ticket, isLoading, isError } = useQuery({
    queryKey: ['ticket-status', token],
    queryFn: () => apiClient.get(`tickets/status/${token}`).json<PublicTicketView>(),
    staleTime: 30_000,
    retry: 1,
  });

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-surface-base">
        <div className="text-slate-400 text-sm">Loading ticket status…</div>
      </div>
    );
  }

  if (isError || !ticket) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-surface-base">
        <div className="text-center space-y-3">
          <AlertTriangle className="w-12 h-12 text-slate-500 mx-auto" />
          <h1 className="text-lg font-semibold text-slate-300">Ticket Not Found</h1>
          <p className="text-sm text-slate-500">The ticket status link may be invalid or expired.</p>
        </div>
      </div>
    );
  }

  const isResolved = ticket.status === 'RESOLVED' || ticket.status === 'CLOSED';
  const createdDate = new Date(ticket.createdAt).toLocaleDateString('en-US', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
  const updatedDate = new Date(ticket.updatedAt).toLocaleDateString('en-US', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });

  return (
    <div className="min-h-screen bg-surface-base flex items-center justify-center p-4">
      <div className="w-full max-w-lg">
        <div className="text-center mb-6">
          <Ticket className="w-10 h-10 text-brand-400 mx-auto mb-2" />
          <h1 className="text-xl font-bold text-slate-200">Ticket Status</h1>
          <p className="text-sm text-slate-500">ZyntaPOS Support</p>
        </div>

        <div className="bg-surface-card border border-surface-border rounded-xl p-6 space-y-5">
          <div className="flex items-center justify-between">
            <span className="font-mono text-sm text-brand-400 font-semibold">{ticket.ticketNumber}</span>
            <TicketStatusBadge status={ticket.status} />
          </div>

          <div>
            <p className="text-sm font-medium text-slate-200">{ticket.title}</p>
            <p className="text-xs text-slate-500 mt-1">{ticket.category}</p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-xs text-slate-500 font-medium">Priority</p>
              <div className="mt-1"><TicketPriorityBadge priority={ticket.priority} /></div>
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">SLA Status</p>
              <div className="mt-1">
                {ticket.slaBreached ? (
                  <span className="inline-flex items-center gap-1 text-xs font-semibold text-red-400">
                    <AlertTriangle className="w-3 h-3" /> Breached
                  </span>
                ) : isResolved ? (
                  <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-400">
                    <CheckCircle2 className="w-3 h-3" /> Met
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 text-xs font-medium text-slate-400">
                    <Clock className="w-3 h-3" /> Active
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="border-t border-surface-border pt-4 space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-500">Created</span>
              <span className="text-slate-400">{createdDate}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-slate-500">Last Updated</span>
              <span className="text-slate-400">{updatedDate}</span>
            </div>
          </div>
        </div>

        <p className="text-center text-xs text-slate-600 mt-4">
          Powered by ZyntaPOS
        </p>
      </div>
    </div>
  );
}
