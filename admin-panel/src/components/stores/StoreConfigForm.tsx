import type { StoreConfig } from '@/types/store';

interface StoreConfigFormProps {
  storeId: string;
  config?: StoreConfig;
}

export function StoreConfigForm({ config }: StoreConfigFormProps) {
  const fields = [
    { label: 'Timezone', value: config?.timezone ?? 'Asia/Colombo' },
    { label: 'Currency Code', value: config?.currency ?? 'LKR' },
    { label: 'Sync Interval (seconds)', value: String(config?.syncIntervalSeconds ?? 300) },
  ];

  return (
    <div className="space-y-4">
      <p className="text-xs text-slate-500">
        Store configuration is managed by the store owner via the POS app.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {fields.map(({ label, value }) => (
          <div key={label}>
            <span className="block text-xs font-medium text-slate-400 mb-1.5">{label}</span>
            <div className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 flex items-center">
              {value}
            </div>
          </div>
        ))}
        <div className="sm:col-span-2">
          <span className="block text-xs font-medium text-slate-400 mb-1.5">Receipt Footer</span>
          <div className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-300 min-h-[3.5rem]">
            {config?.receiptFooter || <span className="text-slate-500">Not configured</span>}
          </div>
        </div>
      </div>
    </div>
  );
}
