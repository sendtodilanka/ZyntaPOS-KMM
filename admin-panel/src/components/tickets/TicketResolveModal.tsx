import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X } from 'lucide-react';
import { useResolveTicket } from '@/api/tickets';
import type { Ticket } from '@/types/ticket';

const schema = z.object({
  resolutionNote: z.string().min(1, 'Resolution note is required'),
  timeSpentMin: z.number({ invalid_type_error: 'Must be a number' }).min(1, 'Must be at least 1 minute'),
});
type FormData = z.infer<typeof schema>;

interface TicketResolveModalProps {
  ticket: Ticket;
  open: boolean;
  onClose: () => void;
}

export function TicketResolveModal({ ticket, open, onClose }: TicketResolveModalProps) {
  const resolveTicket = useResolveTicket();

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { timeSpentMin: 30 },
  });

  const onSubmit = (data: FormData) => {
    resolveTicket.mutate(
      {
        id: ticket.id,
        body: { resolutionNote: data.resolutionNote, timeSpentMin: data.timeSpentMin },
      },
      { onSuccess: () => { reset(); onClose(); } },
    );
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-md animate-fade-in">
        <div className="flex items-center justify-between p-6 border-b border-surface-border">
          <div>
            <h3 className="text-lg font-semibold text-slate-100">Resolve Ticket</h3>
            <p className="text-xs text-slate-400 mt-0.5">{ticket.ticketNumber}</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Resolution Note *</label>
            <textarea
              {...register('resolutionNote')}
              rows={4}
              className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500 resize-none"
              placeholder="Describe how the issue was resolved…"
            />
            {errors.resolutionNote && <p className="text-xs text-red-400 mt-1">{errors.resolutionNote.message}</p>}
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Time Spent (minutes) *</label>
            <input
              {...register('timeSpentMin', { valueAsNumber: true })}
              type="number"
              min={1}
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            {errors.timeSpentMin && <p className="text-xs text-red-400 mt-1">{errors.timeSpentMin.message}</p>}
          </div>
          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium text-slate-300 border border-surface-border hover:bg-surface-elevated transition-colors min-h-[44px]"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={resolveTicket.isPending}
              className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50 transition-colors min-h-[44px]"
            >
              {resolveTicket.isPending ? 'Resolving…' : 'Mark Resolved'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
