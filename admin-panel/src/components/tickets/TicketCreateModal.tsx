import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X } from 'lucide-react';
import { useCreateTicket } from '@/api/tickets';
import type { TicketCategory, TicketPriority } from '@/types/ticket';

const schema = z.object({
  customerName: z.string().min(1, 'Customer name is required'),
  customerEmail: z.string().email('Invalid email').optional().or(z.literal('')),
  customerPhone: z.string().optional(),
  title: z.string().min(1, 'Title is required').max(255, 'Max 255 characters'),
  description: z.string().min(1, 'Description is required'),
  category: z.enum(['HARDWARE', 'SOFTWARE', 'SYNC', 'BILLING', 'OTHER'] as const),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const),
  storeId: z.string().optional(),
});
type FormData = z.infer<typeof schema>;

interface TicketCreateModalProps {
  open: boolean;
  onClose: () => void;
}

const INPUT_CLS = 'w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500';
const LABEL_CLS = 'block text-xs font-medium text-slate-400 mb-1.5';

export function TicketCreateModal({ open, onClose }: TicketCreateModalProps) {
  const createTicket = useCreateTicket();

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { category: 'OTHER', priority: 'MEDIUM' },
  });

  const onSubmit = (data: FormData) => {
    createTicket.mutate(
      {
        customerName: data.customerName,
        customerEmail: data.customerEmail || undefined,
        customerPhone: data.customerPhone || undefined,
        title: data.title,
        description: data.description,
        category: data.category as TicketCategory,
        priority: data.priority as TicketPriority,
        storeId: data.storeId || undefined,
      },
      {
        onSuccess: () => { reset(); onClose(); },
      },
    );
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto animate-fade-in">
        <div className="flex items-center justify-between p-6 border-b border-surface-border sticky top-0 bg-surface-card z-10">
          <h3 className="text-lg font-semibold text-slate-100">New Support Ticket</h3>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2">
              <label className={LABEL_CLS}>Customer Name *</label>
              <input {...register('customerName')} className={INPUT_CLS} placeholder="John Doe" />
              {errors.customerName && <p className="text-xs text-red-400 mt-1">{errors.customerName.message}</p>}
            </div>
            <div>
              <label className={LABEL_CLS}>Customer Email</label>
              <input {...register('customerEmail')} type="email" className={INPUT_CLS} placeholder="john@example.com" />
              {errors.customerEmail && <p className="text-xs text-red-400 mt-1">{errors.customerEmail.message}</p>}
            </div>
            <div>
              <label className={LABEL_CLS}>Customer Phone</label>
              <input {...register('customerPhone')} className={INPUT_CLS} placeholder="+1 555 0100" />
            </div>
            <div className="col-span-2">
              <label className={LABEL_CLS}>Title *</label>
              <input {...register('title')} className={INPUT_CLS} placeholder="Brief summary of the issue" />
              {errors.title && <p className="text-xs text-red-400 mt-1">{errors.title.message}</p>}
            </div>
            <div className="col-span-2">
              <label className={LABEL_CLS}>Description *</label>
              <textarea
                {...register('description')}
                rows={4}
                className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500 resize-none"
                placeholder="Describe the issue in detail…"
              />
              {errors.description && <p className="text-xs text-red-400 mt-1">{errors.description.message}</p>}
            </div>
            <div>
              <label className={LABEL_CLS}>Category *</label>
              <select {...register('category')} className={INPUT_CLS}>
                <option value="HARDWARE">Hardware</option>
                <option value="SOFTWARE">Software</option>
                <option value="SYNC">Sync</option>
                <option value="BILLING">Billing</option>
                <option value="OTHER">Other</option>
              </select>
            </div>
            <div>
              <label className={LABEL_CLS}>Priority *</label>
              <select {...register('priority')} className={INPUT_CLS}>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </select>
            </div>
            <div className="col-span-2">
              <label className={LABEL_CLS}>Store ID (optional)</label>
              <input {...register('storeId')} className={INPUT_CLS} placeholder="Store UUID" />
            </div>
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
              disabled={createTicket.isPending}
              className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium bg-brand-500 text-white hover:bg-brand-600 disabled:opacity-50 transition-colors min-h-[44px]"
            >
              {createTicket.isPending ? 'Creating…' : 'Create Ticket'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
