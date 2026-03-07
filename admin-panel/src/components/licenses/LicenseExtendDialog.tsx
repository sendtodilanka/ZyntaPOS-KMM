import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X } from 'lucide-react';
import { useUpdateLicense } from '@/api/licenses';
import type { License } from '@/types/license';

const schema = z.object({
  expiresAt: z.string().min(1, 'New expiry date is required'),
  reason: z.string().optional(),
});
type FormData = z.infer<typeof schema>;

interface LicenseExtendDialogProps {
  license: License | null;
  onClose: () => void;
}

export function LicenseExtendDialog({ license, onClose }: LicenseExtendDialogProps) {
  const update = useUpdateLicense();
  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const onSubmit = (data: FormData) => {
    if (!license) return;
    update.mutate(
      { key: license.key, data: { expiresAt: data.expiresAt } },
      { onSuccess: () => { reset(); onClose(); } },
    );
  };

  if (!license) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-md animate-fade-in">
        <div className="flex items-center justify-between p-6 border-b border-surface-border">
          <h3 className="text-lg font-semibold text-slate-100">Extend License</h3>
          <button onClick={onClose} className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center">
            <X className="w-4 h-4" />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-4">
          <p className="text-sm text-slate-400">
            Extending license for <span className="text-slate-200 font-medium">{license.customerName}</span>
          </p>
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">New Expiry Date</label>
            <input {...register('expiresAt')} type="date" className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500" />
            {errors.expiresAt && <p className="text-xs text-red-400 mt-1">{errors.expiresAt.message}</p>}
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Reason <span className="text-slate-500">(optional)</span></label>
            <input {...register('reason')} className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500" placeholder="e.g. Annual renewal" />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium text-slate-300 border border-surface-border hover:bg-surface-elevated transition-colors min-h-[44px]">Cancel</button>
            <button type="submit" disabled={update.isPending} className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium bg-brand-500 text-white hover:bg-brand-600 disabled:opacity-50 transition-colors min-h-[44px]">
              {update.isPending ? 'Saving…' : 'Extend License'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
