import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X } from 'lucide-react';
import { useAssignTicket } from '@/api/tickets';
import { useAdminUsers } from '@/api/users';
import type { Ticket } from '@/types/ticket';

const schema = z.object({
  assigneeId: z.string().min(1, 'Please select an assignee'),
});
type FormData = z.infer<typeof schema>;

interface TicketAssignModalProps {
  ticket: Ticket;
  open: boolean;
  onClose: () => void;
}

export function TicketAssignModal({ ticket, open, onClose }: TicketAssignModalProps) {
  const assignTicket = useAssignTicket();
  const { data: users } = useAdminUsers({ size: 100 });

  const eligibleUsers = users?.data?.filter((u) =>
    u.isActive && (u.role === 'ADMIN' || u.role === 'OPERATOR'),
  ) ?? [];

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { assigneeId: ticket.assignedTo ?? '' },
  });

  const onSubmit = (data: FormData) => {
    assignTicket.mutate(
      { id: ticket.id, body: { assigneeId: data.assigneeId } },
      { onSuccess: () => { reset(); onClose(); } },
    );
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-sm animate-fade-in">
        <div className="flex items-center justify-between p-6 border-b border-surface-border">
          <h3 className="text-lg font-semibold text-slate-100">Assign Ticket</h3>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Assignee</label>
            <select
              {...register('assigneeId')}
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="">— Select operator —</option>
              {eligibleUsers.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name} ({u.role})
                </option>
              ))}
            </select>
            {errors.assigneeId && <p className="text-xs text-red-400 mt-1">{errors.assigneeId.message}</p>}
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
              disabled={assignTicket.isPending}
              className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium bg-brand-700 text-white hover:bg-brand-600 disabled:opacity-50 transition-colors min-h-[44px]"
            >
              {assignTicket.isPending ? 'Assigning…' : 'Assign'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
