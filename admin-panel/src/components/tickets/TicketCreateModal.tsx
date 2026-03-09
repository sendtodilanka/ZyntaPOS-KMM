import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X, ChevronDown } from 'lucide-react';
import { useState, useEffect } from 'react';
import { useCreateTicket } from '@/api/tickets';
import { toast } from '@/stores/ui-store';
import { TICKET_CATEGORY_TREE } from '@/types/ticket';
import type { TicketCategory, TicketPriority } from '@/types/ticket';

const CATEGORIES = Object.keys(TICKET_CATEGORY_TREE) as TicketCategory[];

const schema = z.object({
  customerName:  z.string().min(1, 'Customer name is required'),
  customerEmail: z.string().email('Invalid email').optional().or(z.literal('')),
  customerPhone: z.string().optional(),
  title:         z.string().min(1, 'Title is required').max(255, 'Max 255 characters'),
  description:   z.string().min(1, 'Description is required'),
  category:      z.enum(CATEGORIES as [TicketCategory, ...TicketCategory[]]),
  priority:      z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const),
  storeId:       z.string().optional(),
});
type FormData = z.infer<typeof schema>;

interface TicketCreateModalProps {
  open:    boolean;
  onClose: () => void;
}

const INPUT_CLS  = 'w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500';
const SELECT_CLS = 'w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500 appearance-none';
const LABEL_CLS  = 'block text-xs font-medium text-slate-400 mb-1.5';

export function TicketCreateModal({ open, onClose }: TicketCreateModalProps) {
  const createTicket = useCreateTicket();
  const [subcategory, setSubcategory] = useState('');

  const { register, handleSubmit, reset, watch, setValue, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { category: 'OTHER', priority: 'MEDIUM' },
  });

  const selectedCategory = watch('category');
  const subcategoryOptions = TICKET_CATEGORY_TREE[selectedCategory]?.subcategories ?? [];

  // Reset subcategory when category changes
  useEffect(() => {
    setSubcategory('');
    setValue('title', '');
  }, [selectedCategory, setValue]);

  const handleSubcategoryChange = (value: string) => {
    setSubcategory(value);
    if (value) setValue('title', value, { shouldValidate: true });
  };

  const onSubmit = (data: FormData) => {
    createTicket.mutate(
      {
        customerName:  data.customerName,
        customerEmail: data.customerEmail || undefined,
        customerPhone: data.customerPhone || undefined,
        title:         data.title,
        description:   data.description,
        category:      data.category as TicketCategory,
        priority:      data.priority as TicketPriority,
        storeId:       data.storeId || undefined,
      },
      {
        onSuccess: () => {
          reset();
          setSubcategory('');
          onClose();
          toast.success('Ticket created', 'Support ticket has been submitted.');
        },
      },
    );
  };

  const handleClose = () => {
    reset();
    setSubcategory('');
    onClose();
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={handleClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto animate-fade-in">

        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-surface-border sticky top-0 bg-surface-card z-10">
          <div>
            <h3 className="text-lg font-semibold text-slate-100">New Support Ticket</h3>
            <p className="text-xs text-slate-500 mt-0.5">Select a category to get started</p>
          </div>
          <button
            onClick={handleClose}
            className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-5">

          {/* ── Category + Subcategory ───────────────────────────────────── */}
          <div className="space-y-3 p-4 bg-surface-elevated/50 rounded-lg border border-surface-border">
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide">Issue Category</p>

            {/* Main category */}
            <div>
              <label className={LABEL_CLS}>Category *</label>
              <div className="relative">
                <select {...register('category')} className={SELECT_CLS}>
                  {CATEGORIES.map((cat) => (
                    <option key={cat} value={cat}>
                      {TICKET_CATEGORY_TREE[cat].label}
                    </option>
                  ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              </div>
            </div>

            {/* Subcategory */}
            <div>
              <label className={LABEL_CLS}>Issue Type *</label>
              <div className="relative">
                <select
                  value={subcategory}
                  onChange={(e) => handleSubcategoryChange(e.target.value)}
                  className={SELECT_CLS}
                >
                  <option value="">— Select issue type —</option>
                  {subcategoryOptions.map((sub) => (
                    <option key={sub} value={sub}>{sub}</option>
                  ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              </div>
            </div>
          </div>

          {/* ── Title ──────────────────────────────────────────────────────── */}
          <div>
            <label className={LABEL_CLS}>
              Title *
              {subcategory && (
                <span className="ml-2 text-brand-400 font-normal normal-case tracking-normal">
                  (auto-filled from issue type — edit if needed)
                </span>
              )}
            </label>
            <input
              {...register('title')}
              className={INPUT_CLS}
              placeholder="Brief summary of the issue"
            />
            {errors.title && <p className="text-xs text-red-400 mt-1">{errors.title.message}</p>}
          </div>

          {/* ── Description ───────────────────────────────────────────────── */}
          <div>
            <label className={LABEL_CLS}>Description *</label>
            <textarea
              {...register('description')}
              rows={4}
              className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500 resize-none"
              placeholder="Describe the issue in detail — steps to reproduce, error messages, what was expected vs what happened…"
            />
            {errors.description && <p className="text-xs text-red-400 mt-1">{errors.description.message}</p>}
          </div>

          {/* ── Priority ──────────────────────────────────────────────────── */}
          <div>
            <label className={LABEL_CLS}>Priority *</label>
            <div className="relative">
              <select {...register('priority')} className={SELECT_CLS}>
                <option value="LOW">Low — Minor inconvenience, workaround available</option>
                <option value="MEDIUM">Medium — Significant impact, partial workaround</option>
                <option value="HIGH">High — Core function broken, no workaround</option>
                <option value="CRITICAL">Critical — Complete outage / data loss</option>
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            </div>
          </div>

          {/* ── Customer details ──────────────────────────────────────────── */}
          <div className="space-y-3 pt-1">
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide">Customer Details</p>
            <div>
              <label className={LABEL_CLS}>Customer Name *</label>
              <input {...register('customerName')} className={INPUT_CLS} placeholder="John Doe" />
              {errors.customerName && <p className="text-xs text-red-400 mt-1">{errors.customerName.message}</p>}
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={LABEL_CLS}>Email</label>
                <input {...register('customerEmail')} type="email" className={INPUT_CLS} placeholder="john@example.com" />
                {errors.customerEmail && <p className="text-xs text-red-400 mt-1">{errors.customerEmail.message}</p>}
              </div>
              <div>
                <label className={LABEL_CLS}>Phone</label>
                <input {...register('customerPhone')} className={INPUT_CLS} placeholder="+1 555 0100" />
              </div>
            </div>
            <div>
              <label className={LABEL_CLS}>Store ID (optional)</label>
              <input {...register('storeId')} className={INPUT_CLS} placeholder="Store UUID" />
            </div>
          </div>

          {/* ── Actions ───────────────────────────────────────────────────── */}
          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={handleClose}
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
