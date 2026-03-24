import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import {
  DollarSign,
  Plus,
  ArrowRightLeft,
  Clock,
  Save,
  X,
  Loader2,
} from 'lucide-react';
import {
  useExchangeRates,
  useUpsertExchangeRate,
  type ExchangeRateDto,
} from '@/api/exchange-rates';
import { useTimezone } from '@/hooks/use-timezone';
import { toast } from '@/stores/ui-store';

export const Route = createFileRoute('/settings/exchange-rates')({
  component: ExchangeRatesPage,
});

const CURRENCIES = [
  'LKR', 'USD', 'EUR', 'GBP', 'INR', 'JPY', 'AUD', 'CAD', 'SGD',
] as const;

function ExchangeRatesPage() {
  const { data, isLoading, error } = useExchangeRates();
  const upsertMutation = useUpsertExchangeRate();
  const { formatDate } = useTimezone();
  const [showForm, setShowForm] = useState(false);
  const [editingRate, setEditingRate] = useState<ExchangeRateDto | null>(null);

  const handleEdit = (rate: ExchangeRateDto) => {
    setEditingRate(rate);
    setShowForm(true);
  };

  const handleAddNew = () => {
    setEditingRate(null);
    setShowForm(true);
  };

  const handleCloseForm = () => {
    setShowForm(false);
    setEditingRate(null);
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold flex items-center gap-2">
            <DollarSign className="h-5 w-5" />
            Exchange Rates
          </h2>
          <p className="text-sm text-muted-foreground mt-1">
            Manage currency exchange rates for multi-currency support across all stores.
          </p>
        </div>
        <button
          onClick={handleAddNew}
          className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus className="h-4 w-4" />
          Add Rate
        </button>
      </div>

      {/* Upsert Form */}
      {showForm && (
        <ExchangeRateForm
          initial={editingRate}
          onSave={async (data) => {
            try {
              await upsertMutation.mutateAsync(data);
              toast.success(editingRate ? 'Exchange rate updated' : 'Exchange rate added');
              handleCloseForm();
            } catch {
              toast.error('Failed to save exchange rate');
            }
          }}
          onCancel={handleCloseForm}
          isSaving={upsertMutation.isPending}
        />
      )}

      {/* Rates Table */}
      {isLoading && (
        <div className="flex items-center justify-center py-12 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin mr-2" />
          Loading exchange rates...
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Failed to load exchange rates. Please try again.
        </div>
      )}

      {data && data.rates.length === 0 && !showForm && (
        <div className="rounded-lg border border-dashed border-muted-foreground/30 p-8 text-center">
          <ArrowRightLeft className="h-8 w-8 mx-auto text-muted-foreground/50 mb-3" />
          <p className="text-sm text-muted-foreground">No exchange rates configured yet.</p>
          <button
            onClick={handleAddNew}
            className="mt-3 text-sm text-blue-600 hover:text-blue-700 font-medium"
          >
            Add your first exchange rate
          </button>
        </div>
      )}

      {data && data.rates.length > 0 && (
        <div className="rounded-lg border border-surface-border overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-surface-elevated">
              <tr>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Pair</th>
                <th className="px-4 py-3 text-right font-medium text-muted-foreground">Rate</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Source</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">Last Updated</th>
                <th className="px-4 py-3 text-right font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-border">
              {data.rates.map((rate) => (
                <tr key={rate.id} className="hover:bg-surface-elevated/50 transition-colors">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <span className="font-mono font-medium">{rate.sourceCurrency}</span>
                      <ArrowRightLeft className="h-3.5 w-3.5 text-muted-foreground" />
                      <span className="font-mono font-medium">{rate.targetCurrency}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right font-mono tabular-nums">
                    {rate.rate.toFixed(4)}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                      rate.source === 'MANUAL'
                        ? 'bg-blue-50 text-blue-700'
                        : 'bg-green-50 text-green-700'
                    }`}>
                      {rate.source}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">
                    <div className="flex items-center gap-1">
                      <Clock className="h-3.5 w-3.5" />
                      {formatDate(new Date(rate.updatedAt).getTime())}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => handleEdit(rate)}
                      className="text-sm text-blue-600 hover:text-blue-700 font-medium"
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-4 py-2 bg-surface-elevated text-xs text-muted-foreground">
            {data.total} exchange rate{data.total !== 1 ? 's' : ''} configured
          </div>
        </div>
      )}
    </div>
  );
}

interface ExchangeRateFormProps {
  initial: ExchangeRateDto | null;
  onSave: (data: { sourceCurrency: string; targetCurrency: string; rate: number; source?: string }) => Promise<void>;
  onCancel: () => void;
  isSaving: boolean;
}

function ExchangeRateForm({ initial, onSave, onCancel, isSaving }: ExchangeRateFormProps) {
  const [source, setSource] = useState(initial?.sourceCurrency ?? 'USD');
  const [target, setTarget] = useState(initial?.targetCurrency ?? 'LKR');
  const [rate, setRate] = useState(initial?.rate.toString() ?? '');
  const [rateSource, setRateSource] = useState(initial?.source ?? 'MANUAL');

  const isValid = source !== target && rate !== '' && parseFloat(rate) > 0;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValid) return;
    onSave({
      sourceCurrency: source,
      targetCurrency: target,
      rate: parseFloat(rate),
      source: rateSource,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-surface-border p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold">
          {initial ? 'Edit Exchange Rate' : 'Add Exchange Rate'}
        </h3>
        <button type="button" onClick={onCancel} className="text-muted-foreground hover:text-foreground">
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">
            Source Currency
          </label>
          <select
            value={source}
            onChange={(e) => setSource(e.target.value)}
            disabled={!!initial}
            className="w-full rounded-lg border border-surface-border bg-surface-card px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">
            Target Currency
          </label>
          <select
            value={target}
            onChange={(e) => setTarget(e.target.value)}
            disabled={!!initial}
            className="w-full rounded-lg border border-surface-border bg-surface-card px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </div>
      </div>

      {source === target && (
        <p className="text-xs text-red-500">Source and target currencies must be different.</p>
      )}

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">
            Exchange Rate
          </label>
          <input
            type="number"
            step="0.0001"
            min="0.0001"
            value={rate}
            onChange={(e) => setRate(e.target.value)}
            placeholder="e.g., 320.5000"
            className="w-full rounded-lg border border-surface-border bg-surface-card px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {rate && parseFloat(rate) > 0 && (
            <p className="text-xs text-muted-foreground mt-1">
              1 {source} = {parseFloat(rate).toFixed(4)} {target}
            </p>
          )}
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">
            Rate Source
          </label>
          <select
            value={rateSource}
            onChange={(e) => setRateSource(e.target.value)}
            className="w-full rounded-lg border border-surface-border bg-surface-card px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="MANUAL">Manual</option>
            <option value="ECB">ECB (European Central Bank)</option>
            <option value="CBSL">CBSL (Central Bank of Sri Lanka)</option>
          </select>
        </div>
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm font-medium text-muted-foreground hover:text-foreground rounded-lg border border-surface-border hover:bg-surface-elevated transition-colors"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={!isValid || isSaving}
          className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {isSaving ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Save className="h-4 w-4" />
          )}
          {initial ? 'Update Rate' : 'Add Rate'}
        </button>
      </div>
    </form>
  );
}
