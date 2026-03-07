import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Plus, Edit2, Trash2, Check, X } from 'lucide-react';
import { useTaxRates, useCreateTaxRate, useUpdateTaxRate, useDeleteTaxRate } from '@/api/config';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import type { TaxRate } from '@/types/config';

const taxRateSchema = z.object({
  name: z.string().min(1, 'Name required'),
  rate: z.number().min(0).max(100),
  description: z.string().min(1, 'Description required'),
  country: z.string().min(2, 'Country required'),
  region: z.string().optional(),
  isDefault: z.boolean(),
});

type TaxRateForm = z.infer<typeof taxRateSchema>;

function TaxRateRow({ rate, onEdit, onDelete }: { rate: TaxRate; onEdit: (r: TaxRate) => void; onDelete: (id: string) => void }) {
  const [confirmDelete, setConfirmDelete] = useState(false);
  return (
    <>
      <div className="flex items-center gap-3 py-3 border-b border-surface-border last:border-0">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-medium text-slate-100">{rate.name}</span>
            {rate.isDefault && <span className="px-1.5 py-0.5 text-[10px] bg-green-500/10 text-green-400 rounded">Default</span>}
            {!rate.active && <span className="px-1.5 py-0.5 text-[10px] bg-slate-700 text-slate-400 rounded">Inactive</span>}
          </div>
          <p className="text-xs text-slate-500">{rate.description} · {rate.country}{rate.region ? `, ${rate.region}` : ''}</p>
        </div>
        <span className="text-lg font-bold text-brand-400">{rate.rate}%</span>
        <div className="flex items-center gap-1">
          <button onClick={() => onEdit(rate)} className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-h-[44px] min-w-[44px] flex items-center justify-center" aria-label="Edit"><Edit2 className="w-4 h-4" /></button>
          <button onClick={() => setConfirmDelete(true)} className="p-2 rounded-lg text-slate-400 hover:text-red-400 hover:bg-red-500/10 min-h-[44px] min-w-[44px] flex items-center justify-center" aria-label="Delete"><Trash2 className="w-4 h-4" /></button>
        </div>
      </div>
      <ConfirmDialog open={confirmDelete} onClose={() => setConfirmDelete(false)} title="Delete Tax Rate" description={`Delete "${rate.name}" (${rate.rate}%)? This cannot be undone.`} variant="destructive" onConfirm={() => onDelete(rate.id)} />
    </>
  );
}

export function TaxRateEditor() {
  const { data: rates, isLoading } = useTaxRates();
  const { mutate: createRate, isPending: creating } = useCreateTaxRate();
  const { mutate: updateRate, isPending: updating } = useUpdateTaxRate();
  const { mutate: deleteRate } = useDeleteTaxRate();
  const [editing, setEditing] = useState<TaxRate | null>(null);
  const [showForm, setShowForm] = useState(false);
  const { register, handleSubmit, reset, formState: { errors } } = useForm<TaxRateForm>({ resolver: zodResolver(taxRateSchema), defaultValues: { isDefault: false, rate: 0 } });

  const openCreate = () => { reset({ isDefault: false, rate: 0 }); setEditing(null); setShowForm(true); };
  const openEdit = (rate: TaxRate) => { setEditing(rate); reset({ name: rate.name, rate: rate.rate, description: rate.description, country: rate.country, region: rate.region, isDefault: rate.isDefault }); setShowForm(true); };
  const onSubmit = (data: TaxRateForm) => {
    if (editing) updateRate({ id: editing.id, ...data }, { onSuccess: () => { setShowForm(false); setEditing(null); } });
    else createRate({ ...data, applicableTo: [] }, { onSuccess: () => { setShowForm(false); reset(); } });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-slate-400">{(rates ?? []).length} tax rates configured</p>
        <button onClick={openCreate} className="flex items-center gap-2 px-3 py-2 bg-brand-500 hover:bg-brand-600 text-white text-sm font-medium rounded-lg min-h-[44px] transition-colors"><Plus className="w-4 h-4" />Add Rate</button>
      </div>
      {showForm && (
        <form onSubmit={handleSubmit(onSubmit)} className="bg-surface-elevated border border-surface-border rounded-xl p-4 space-y-3">
          <h3 className="text-sm font-semibold text-slate-100">{editing ? 'Edit Tax Rate' : 'New Tax Rate'}</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div><label className="block text-xs text-slate-400 mb-1">Name</label><input {...register('name')} className="w-full h-10 px-3 bg-surface-card border border-surface-border rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500" placeholder="VAT, GST..." />{errors.name && <p className="text-xs text-red-400 mt-1">{errors.name.message}</p>}</div>
            <div><label className="block text-xs text-slate-400 mb-1">Rate (%)</label><input {...register('rate', { valueAsNumber: true })} type="number" step="0.01" className="w-full h-10 px-3 bg-surface-card border border-surface-border rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500" /></div>
            <div><label className="block text-xs text-slate-400 mb-1">Country Code</label><input {...register('country')} className="w-full h-10 px-3 bg-surface-card border border-surface-border rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500" placeholder="LK, US..." /></div>
            <div><label className="block text-xs text-slate-400 mb-1">Region (optional)</label><input {...register('region')} className="w-full h-10 px-3 bg-surface-card border border-surface-border rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500" /></div>
            <div className="sm:col-span-2"><label className="block text-xs text-slate-400 mb-1">Description</label><input {...register('description')} className="w-full h-10 px-3 bg-surface-card border border-surface-border rounded-lg text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-brand-500" />{errors.description && <p className="text-xs text-red-400 mt-1">{errors.description.message}</p>}</div>
            <div className="sm:col-span-2 flex items-center gap-2"><input {...register('isDefault')} type="checkbox" id="isDefault" className="w-4 h-4 rounded border-surface-border bg-surface-card accent-brand-500" /><label htmlFor="isDefault" className="text-sm text-slate-400">Set as default tax rate</label></div>
          </div>
          <div className="flex gap-2 pt-2">
            <button type="submit" disabled={creating || updating} className="flex items-center gap-2 px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white text-sm font-medium rounded-lg min-h-[44px] disabled:opacity-50 transition-colors"><Check className="w-4 h-4" />{creating || updating ? 'Saving...' : 'Save'}</button>
            <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="flex items-center gap-2 px-4 py-2 text-slate-400 hover:text-slate-100 text-sm rounded-lg min-h-[44px] border border-surface-border transition-colors"><X className="w-4 h-4" />Cancel</button>
          </div>
        </form>
      )}
      <div className="bg-surface-card border border-surface-border rounded-xl p-4">
        {isLoading ? <div className="space-y-3">{[1,2,3].map(i=><div key={i} className="h-14 bg-surface-elevated rounded animate-pulse"/>)}</div>
          : (rates??[]).length===0 ? <p className="text-center py-6 text-slate-400">No tax rates configured</p>
          : <div>{(rates??[]).map(r=><TaxRateRow key={r.id} rate={r} onEdit={openEdit} onDelete={id=>deleteRate(id)}/>)}</div>}
      </div>
    </div>
  );
}
