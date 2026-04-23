import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { Package, AlertTriangle } from 'lucide-react';
import { useGlobalInventory } from '@/api/inventory';
import { useDebounce } from '@/hooks/use-debounce';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { cn } from '@/lib/utils';

export const Route = createFileRoute('/inventory/')({
  component: InventoryPage,
});

function InventoryPage() {
  const [productId, setProductId] = useState('');
  const [storeId, setStoreId]     = useState('');
  const [showLowOnly, setShowLowOnly] = useState(false);
  const debouncedProduct = useDebounce(productId, 300);
  const debouncedStore   = useDebounce(storeId, 300);

  const { data, isLoading, isError, refetch } = useGlobalInventory({
    productId: debouncedProduct || undefined,
    storeId:   debouncedStore   || undefined,
  });

  const items = showLowOnly
    ? (data?.items ?? []).filter((r) => r.isLowStock)
    : (data?.items ?? []);

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Warehouse Inventory</h1>
          <p className="panel-subtitle">
            {data?.total ?? 0} stock rows
            {(data?.lowStock ?? 0) > 0 && (
              <span className="ml-2 text-amber-400 font-medium">
                · {data!.lowStock} low stock
              </span>
            )}
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          value={productId}
          onChange={(e) => setProductId(e.target.value)}
          placeholder="Filter by Product ID…"
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 flex-1 min-w-[200px] max-w-xs"
        />
        <input
          type="text"
          value={storeId}
          onChange={(e) => setStoreId(e.target.value)}
          placeholder="Filter by Store ID…"
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 flex-1 min-w-[200px] max-w-xs"
        />
        <button
          onClick={() => setShowLowOnly((v) => !v)}
          className={cn(
            'h-10 px-4 rounded-lg text-sm font-medium transition-colors flex items-center gap-2',
            showLowOnly
              ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40'
              : 'bg-surface-elevated border border-surface-border text-slate-400 hover:text-slate-100',
          )}
        >
          <AlertTriangle className="w-4 h-4" />
          Low stock only
        </button>
      </div>

      {/* Table */}
      {isError ? (
        <ErrorBanner message="Failed to load inventory." onRetry={() => refetch()} />
      ) : isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading…</div>
      ) : items.length === 0 ? (
        <div className="text-center py-12 text-slate-500">
          <Package className="w-10 h-10 mx-auto mb-3 opacity-30" />
          <p className="text-sm">No stock rows found</p>
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-surface-border text-slate-500 text-xs uppercase tracking-wider">
                  <th className="px-4 py-3 text-left">Store ID</th>
                  <th className="px-4 py-3 text-left">Warehouse ID</th>
                  <th className="px-4 py-3 text-left">Product ID</th>
                  <th className="px-4 py-3 text-right">Quantity</th>
                  <th className="px-4 py-3 text-right">Min Qty</th>
                  <th className="px-4 py-3 text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-border">
                {items.map((row) => (
                  <tr
                    key={row.id}
                    className={cn(
                      'hover:bg-surface-elevated/50 transition-colors',
                      row.isLowStock && 'bg-amber-500/5',
                    )}
                  >
                    <td className="px-4 py-3 font-mono text-slate-400 text-xs">{row.storeId}</td>
                    <td className="px-4 py-3 font-mono text-slate-400 text-xs">{row.warehouseId}</td>
                    <td className="px-4 py-3 font-mono text-slate-300 text-xs">{row.productId}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-slate-200">
                      {row.quantity.toFixed(2)}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums text-slate-400">
                      {row.minQuantity.toFixed(2)}
                    </td>
                    <td className="px-4 py-3 text-center">
                      {row.isLowStock ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-500/20 text-amber-400">
                          <AlertTriangle className="w-3 h-3" /> Low
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-500/20 text-emerald-400">
                          OK
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
