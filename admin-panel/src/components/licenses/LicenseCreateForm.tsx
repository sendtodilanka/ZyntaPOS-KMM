import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X } from 'lucide-react';
import { useCreateLicense } from '@/api/licenses';
import type { LicenseEdition } from '@/types/license';

const schema = z.object({
  customerId: z.string().min(1, 'Customer ID is required').refine((v) => !v.startsWith('cust_'), {
    message: 'Do not include the "cust_" prefix — it is added automatically',
  }),
  edition: z.enum(['STARTER', 'PROFESSIONAL', 'ENTERPRISE'] as const),
  maxDevices: z.number().int().min(1).max(100),
  expiresAt: z.string().optional(),
});

type FormData = z.infer<typeof schema>;

interface LicenseCreateFormProps {
  open: boolean;
  onClose: () => void;
}

const EDITIONS: LicenseEdition[] = ['STARTER', 'PROFESSIONAL', 'ENTERPRISE'];

export function LicenseCreateForm({ open, onClose }: LicenseCreateFormProps) {
  const createLicense = useCreateLicense();
  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { edition: 'PROFESSIONAL', maxDevices: 3 },
  });

  const onSubmit = (data: FormData) => {
    createLicense.mutate(
      { ...data, customerId: `cust_${data.customerId}`, expiresAt: data.expiresAt ? `${data.expiresAt}T00:00:00Z` : undefined },
      { onSuccess: () => { reset(); onClose(); } },
    );
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-md animate-fade-in">
        <div className="flex items-center justify-between p-6 border-b border-surface-border">
          <h3 className="text-lg font-semibold text-slate-100">Create License</h3>
          <button onClick={onClose} className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center">
            <X className="w-4 h-4" />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Customer ID</label>
            <div className="flex h-10 rounded-lg border border-surface-border overflow-hidden focus-within:ring-1 focus-within:ring-brand-500">
              <span className="flex items-center px-3 bg-surface-border text-sm font-mono text-slate-300 select-none whitespace-nowrap">
                cust_
              </span>
              <input
                {...register('customerId')}
                className="flex-1 bg-surface-elevated px-3 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none"
                placeholder="your-id-here"
              />
            </div>
            {errors.customerId && <p className="text-xs text-red-400 mt-1">{errors.customerId.message}</p>}
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Edition</label>
            <select {...register('edition')} className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500">
              {EDITIONS.map((e) => <option key={e} value={e}>{e}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Max Devices</label>
            <input {...register('maxDevices', { valueAsNumber: true })} type="number" min={1} max={100} className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500" />
            {errors.maxDevices && <p className="text-xs text-red-400 mt-1">{errors.maxDevices.message}</p>}
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Expiry Date <span className="text-slate-500">(optional)</span></label>
            <input {...register('expiresAt')} type="date" className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500" />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium text-slate-300 border border-surface-border hover:bg-surface-elevated transition-colors min-h-[44px]">Cancel</button>
            <button type="submit" disabled={createLicense.isPending} className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium bg-brand-700 text-white hover:bg-brand-600 disabled:opacity-50 transition-colors min-h-[44px]">
              {createLicense.isPending ? 'Creating…' : 'Create License'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
