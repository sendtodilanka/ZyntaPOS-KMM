import { useState } from 'react';
import { Plus, Edit2, Trash2, Star } from 'lucide-react';
import { useTaxRates, useCreateTaxRate, useUpdateTaxRate, useDeleteTaxRate } from '@/api/config';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { useAuth } from '@/hooks/use-auth';
import type { TaxRate, TaxRateCreateRequest, TaxRateUpdateRequest } from '@/types/config';

export function TaxRatesTable() {
  const { hasPermission } = useAuth();
  // G-004: write actions (Add / Edit / Delete) are gated; AUDITOR sees a
  // read-only view.
  const canWrite = hasPermission('config:tax_rates:write');

  const { data, isLoading, isError, refetch } = useTaxRates();
  const deleteMutation = useDeleteTaxRate();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<TaxRate | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<TaxRate | null>(null);

  if (isError) {
    return <ErrorBanner message="Failed to load tax rates." onRetry={() => refetch()} />;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-slate-200">Tax Rates</h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Rates applied to orders when products match the applicable categories.
          </p>
        </div>
        {canWrite && (
          <button
            onClick={() => setCreateOpen(true)}
            className="flex items-center gap-1.5 px-3 py-2 bg-brand-700 hover:bg-brand-800 text-white text-sm font-medium rounded-lg transition-colors min-h-[40px]"
          >
            <Plus className="w-4 h-4" /> Add Tax Rate
          </button>
        )}
      </div>

      <div className="overflow-x-auto bg-surface-card border border-surface-border rounded-xl">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-surface-border text-left text-slate-400">
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium text-right">Rate</th>
              <th className="px-4 py-3 font-medium">Country / Region</th>
              <th className="px-4 py-3 font-medium">Applicable To</th>
              <th className="px-4 py-3 font-medium text-center">Status</th>
              <th className="px-4 py-3 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-slate-400">Loading…</td>
              </tr>
            ) : !data || data.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-slate-400">
                  No tax rates configured yet.
                </td>
              </tr>
            ) : (
              data.map((r) => (
                <tr key={r.id} className="border-b border-surface-border/50 hover:bg-surface-elevated/50">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-100">{r.name}</span>
                      {r.isDefault && (
                        <span title="Default" className="inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-amber-500/20 text-amber-400 text-[10px] rounded-full">
                          <Star className="w-3 h-3" /> default
                        </span>
                      )}
                    </div>
                    {r.description && <p className="text-xs text-slate-500 mt-0.5">{r.description}</p>}
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">{r.rate.toFixed(2)}%</td>
                  <td className="px-4 py-3 text-slate-300">
                    {r.country}
                    {r.region && <span className="text-slate-500"> / {r.region}</span>}
                  </td>
                  <td className="px-4 py-3 text-slate-400 text-xs">
                    {r.applicableTo.join(', ')}
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className={`px-2 py-0.5 rounded-full text-xs ${r.active ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}`}>
                      {r.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    {canWrite ? (
                      <div className="inline-flex items-center gap-1">
                        <button
                          onClick={() => setEditTarget(r)}
                          title="Edit"
                          className="p-1.5 rounded-md text-brand-400 hover:bg-brand-500/10 min-w-[32px] min-h-[32px] flex items-center justify-center"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setDeleteTarget(r)}
                          disabled={deleteMutation.isPending}
                          title="Delete"
                          className="p-1.5 rounded-md text-red-400 hover:bg-red-500/10 min-w-[32px] min-h-[32px] flex items-center justify-center disabled:opacity-40"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ) : (
                      <span className="text-xs text-slate-500">read-only</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {createOpen && (
        <TaxRateFormDialog
          mode="create"
          onClose={() => setCreateOpen(false)}
        />
      )}

      {editTarget && (
        <TaxRateFormDialog
          mode="edit"
          taxRate={editTarget}
          onClose={() => setEditTarget(null)}
        />
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget.id, { onSettled: () => setDeleteTarget(null) });
          }
        }}
        title={deleteTarget ? `Delete "${deleteTarget.name}"?` : 'Delete tax rate?'}
        description="Existing orders keep their historical rate. Future orders will fall back to the default rate if this one is removed."
        confirmLabel="Delete"
        variant="destructive"
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}

// ── Form dialog (shared between create + edit) ──────────────────────────────

function TaxRateFormDialog({
  mode,
  taxRate,
  onClose,
}: {
  mode: 'create' | 'edit';
  taxRate?: TaxRate;
  onClose: () => void;
}) {
  const createMutation = useCreateTaxRate();
  const updateMutation = useUpdateTaxRate();
  const isPending = createMutation.isPending || updateMutation.isPending;

  const [name, setName] = useState(taxRate?.name ?? '');
  const [rate, setRate] = useState(taxRate?.rate.toString() ?? '');
  const [description, setDescription] = useState(taxRate?.description ?? '');
  const [applicableTo, setApplicableTo] = useState((taxRate?.applicableTo ?? ['ALL']).join(', '));
  const [isDefault, setIsDefault] = useState(taxRate?.isDefault ?? false);
  const [country, setCountry] = useState(taxRate?.country ?? 'LK');
  const [region, setRegion] = useState(taxRate?.region ?? '');
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const parsedRate = parseFloat(rate);
    if (Number.isNaN(parsedRate) || parsedRate < 0 || parsedRate > 100) {
      setError('Rate must be a number between 0 and 100.');
      return;
    }
    if (!name.trim()) {
      setError('Name is required.');
      return;
    }
    const applicableArr = applicableTo.split(',').map((s) => s.trim()).filter(Boolean);

    if (mode === 'create') {
      const req: TaxRateCreateRequest = {
        name: name.trim(),
        rate: parsedRate,
        description: description.trim() || undefined,
        applicableTo: applicableArr.length ? applicableArr : undefined,
        isDefault,
        country: country.trim() || undefined,
        region: region.trim() || undefined,
      };
      createMutation.mutate(req, { onSuccess: () => onClose() });
    } else if (taxRate) {
      const req: TaxRateUpdateRequest = {
        name: name.trim(),
        rate: parsedRate,
        description: description.trim(),
        applicableTo: applicableArr,
        isDefault,
        country: country.trim(),
        region: region.trim() || undefined,
      };
      updateMutation.mutate({ id: taxRate.id, data: req }, { onSuccess: () => onClose() });
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-md p-6">
        <h3 className="text-lg font-semibold text-slate-100 mb-4">
          {mode === 'create' ? 'Add Tax Rate' : `Edit "${taxRate?.name}"`}
        </h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <p className="rounded-lg bg-red-500/10 border border-red-500/20 px-3 py-2 text-sm text-red-400">
              {error}
            </p>
          )}
          <Field label="Name *">
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              maxLength={100}
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Rate (%) *">
              <input
                type="number"
                step="0.01"
                min="0"
                max="100"
                value={rate}
                onChange={(e) => setRate(e.target.value)}
                required
                className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
            </Field>
            <Field label="Country">
              <input
                type="text"
                value={country}
                onChange={(e) => setCountry(e.target.value)}
                maxLength={2}
                placeholder="LK"
                className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
            </Field>
          </div>
          <Field label="Region (optional)">
            <input
              type="text"
              value={region}
              onChange={(e) => setRegion(e.target.value)}
              placeholder="e.g. Western Province"
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </Field>
          <Field label="Applicable to (comma-separated)">
            <input
              type="text"
              value={applicableTo}
              onChange={(e) => setApplicableTo(e.target.value)}
              placeholder="ALL, GOODS, SERVICES…"
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </Field>
          <Field label="Description">
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </Field>
          <label className="flex items-center gap-2 text-sm text-slate-300">
            <input
              type="checkbox"
              checked={isDefault}
              onChange={(e) => setIsDefault(e.target.checked)}
            />
            Set as default rate
          </label>

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isPending}
              className="px-4 py-2 rounded-lg text-sm font-medium text-slate-300 hover:bg-surface-elevated transition-colors min-h-[40px] disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="px-4 py-2 rounded-lg text-sm font-medium bg-brand-700 hover:bg-brand-800 text-white transition-colors min-h-[40px] disabled:opacity-50"
            >
              {isPending ? 'Saving…' : mode === 'create' ? 'Create' : 'Save'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-400 mb-1">{label}</label>
      {children}
    </div>
  );
}
