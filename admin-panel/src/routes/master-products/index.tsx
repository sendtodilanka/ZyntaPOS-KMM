import { createFileRoute, Link } from '@tanstack/react-router';
import { useState } from 'react';
import { useMasterProducts, useCreateMasterProduct, useDeleteMasterProduct } from '@/api/master-products';
import { useDebounce } from '@/hooks/use-debounce';
import type { MasterProduct, CreateMasterProductRequest } from '@/types/master-product';

export const Route = createFileRoute('/master-products/')({
  component: MasterProductsPage,
});

function MasterProductsPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const debouncedSearch = useDebounce(search, 300);

  const { data, isLoading } = useMasterProducts({
    page,
    size: 20,
    search: debouncedSearch || undefined,
  });

  const createMutation = useCreateMasterProduct();
  const deleteMutation = useDeleteMasterProduct();

  const handleCreate = (req: CreateMasterProductRequest) => {
    createMutation.mutate(req, { onSuccess: () => setShowCreate(false) });
  };

  return (
    <div className="space-y-6">
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Product Catalog</h1>
          <p className="panel-subtitle">
            {data?.total ?? 0} master products — shared across all stores
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="btn btn-primary"
        >
          + New Product
        </button>
      </div>

      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder="Search by name, SKU, or barcode…"
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 flex-1 min-w-[200px] max-w-sm"
        />
      </div>

      {isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading…</div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-surface-border text-left text-slate-400">
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium">SKU</th>
                  <th className="px-4 py-3 font-medium">Barcode</th>
                  <th className="px-4 py-3 font-medium text-right">Base Price</th>
                  <th className="px-4 py-3 font-medium text-center">Stores</th>
                  <th className="px-4 py-3 font-medium text-center">Status</th>
                  <th className="px-4 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data?.data?.map((p: MasterProduct) => (
                  <tr key={p.id} className="border-b border-surface-border/50 hover:bg-surface-elevated/50">
                    <td className="px-4 py-3">
                      <Link
                        to="/master-products/$masterProductId"
                        params={{ masterProductId: p.id }}
                        className="text-brand-400 hover:underline font-medium"
                      >
                        {p.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-slate-400">{p.sku || '—'}</td>
                    <td className="px-4 py-3 text-slate-400">{p.barcode || '—'}</td>
                    <td className="px-4 py-3 text-right">{p.base_price.toFixed(2)}</td>
                    <td className="px-4 py-3 text-center">
                      <span className="bg-brand-500/20 text-brand-400 px-2 py-0.5 rounded-full text-xs">
                        {p.store_count}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={`px-2 py-0.5 rounded-full text-xs ${p.is_active ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}`}>
                        {p.is_active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => deleteMutation.mutate(p.id)}
                        className="text-red-400 hover:text-red-300 text-xs"
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
                {(!data?.data || data.data.length === 0) && (
                  <tr>
                    <td colSpan={7} className="px-4 py-12 text-center text-slate-400">
                      No master products found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {data && data.total > 20 && (
            <div className="flex justify-between items-center">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => Math.max(0, p - 1))}
                className="btn btn-secondary"
              >
                Previous
              </button>
              <span className="text-sm text-slate-400">
                Page {page + 1} of {Math.ceil(data.total / 20)}
              </span>
              <button
                disabled={!data.hasMore}
                onClick={() => setPage(p => p + 1)}
                className="btn btn-secondary"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}

      {showCreate && (
        <CreateMasterProductDialog
          onClose={() => setShowCreate(false)}
          onSubmit={handleCreate}
          isLoading={createMutation.isPending}
        />
      )}
    </div>
  );
}

function CreateMasterProductDialog({
  onClose,
  onSubmit,
  isLoading,
}: {
  onClose: () => void;
  onSubmit: (req: CreateMasterProductRequest) => void;
  isLoading: boolean;
}) {
  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [barcode, setBarcode] = useState('');
  const [basePrice, setBasePrice] = useState('');
  const [costPrice, setCostPrice] = useState('');
  const [description, setDescription] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      name,
      sku: sku || undefined,
      barcode: barcode || undefined,
      base_price: parseFloat(basePrice) || 0,
      cost_price: parseFloat(costPrice) || 0,
      description: description || undefined,
    });
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-surface-elevated rounded-xl shadow-xl p-6 w-full max-w-md">
        <h2 className="text-lg font-semibold text-white mb-4">New Master Product</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm text-slate-400 mb-1">Name *</label>
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} required
              className="w-full h-10 bg-surface border border-surface-border rounded-lg px-3 text-sm text-white" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm text-slate-400 mb-1">SKU</label>
              <input type="text" value={sku} onChange={(e) => setSku(e.target.value)}
                className="w-full h-10 bg-surface border border-surface-border rounded-lg px-3 text-sm text-white" />
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Barcode</label>
              <input type="text" value={barcode} onChange={(e) => setBarcode(e.target.value)}
                className="w-full h-10 bg-surface border border-surface-border rounded-lg px-3 text-sm text-white" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Base Price *</label>
              <input type="number" step="0.01" value={basePrice} onChange={(e) => setBasePrice(e.target.value)} required
                className="w-full h-10 bg-surface border border-surface-border rounded-lg px-3 text-sm text-white" />
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Cost Price</label>
              <input type="number" step="0.01" value={costPrice} onChange={(e) => setCostPrice(e.target.value)}
                className="w-full h-10 bg-surface border border-surface-border rounded-lg px-3 text-sm text-white" />
            </div>
          </div>
          <div>
            <label className="block text-sm text-slate-400 mb-1">Description</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3}
              className="w-full bg-surface border border-surface-border rounded-lg px-3 py-2 text-sm text-white" />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
            <button type="submit" disabled={isLoading || !name} className="btn btn-primary">
              {isLoading ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
