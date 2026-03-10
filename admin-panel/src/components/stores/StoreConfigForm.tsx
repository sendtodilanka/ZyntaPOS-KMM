import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Save } from 'lucide-react';
import { useUpdateStoreConfig } from '@/api/stores';
import type { StoreConfig } from '@/types/store';

const schema = z.object({
  timezone: z.string().min(1),
  currency: z.string().length(3),
  receiptFooter: z.string().max(200),
  syncIntervalSeconds: z.number().int().min(30).max(3600),
});

type FormData = z.infer<typeof schema>;

interface StoreConfigFormProps {
  storeId: string;
  config?: StoreConfig;
}

export function StoreConfigForm({ storeId, config }: StoreConfigFormProps) {
  const update = useUpdateStoreConfig();
  const { register, handleSubmit, formState: { errors, isDirty } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      timezone: config?.timezone ?? 'Asia/Colombo',
      currency: config?.currency ?? 'LKR',
      receiptFooter: config?.receiptFooter ?? '',
      syncIntervalSeconds: config?.syncIntervalSeconds ?? 300,
    },
  });

  const onSubmit = (data: FormData) => {
    update.mutate({ storeId, config: data });
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-medium text-slate-400 mb-1.5">Timezone</label>
          <input {...register('timezone')} className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500" />
          {errors.timezone && <p className="text-xs text-red-400 mt-1">{errors.timezone.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-400 mb-1.5">Currency Code</label>
          <input {...register('currency')} maxLength={3} className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500" />
          {errors.currency && <p className="text-xs text-red-400 mt-1">{errors.currency.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-400 mb-1.5">Sync Interval (seconds)</label>
          <input {...register('syncIntervalSeconds', { valueAsNumber: true })} type="number" min={30} max={3600} className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500" />
          {errors.syncIntervalSeconds && <p className="text-xs text-red-400 mt-1">{errors.syncIntervalSeconds.message}</p>}
        </div>
        <div className="sm:col-span-2">
          <label className="block text-xs font-medium text-slate-400 mb-1.5">Receipt Footer</label>
          <textarea {...register('receiptFooter')} rows={2} className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500 resize-none" placeholder="Thank you for your purchase!" />
        </div>
      </div>
      <div className="flex justify-end">
        <button
          type="submit"
          disabled={!isDirty || update.isPending}
          className="flex items-center gap-2 px-4 py-2 bg-brand-700 hover:bg-brand-800 text-white text-sm font-medium rounded-lg transition-colors min-h-[44px] disabled:opacity-50"
        >
          <Save className="w-4 h-4" />
          {update.isPending ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
    </form>
  );
}
