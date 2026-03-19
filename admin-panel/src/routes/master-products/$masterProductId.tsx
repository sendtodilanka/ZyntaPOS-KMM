import { createFileRoute, Link } from '@tanstack/react-router';
import { useState } from 'react';
import {
  useMasterProduct,
  useMasterProductStores,
  useUpdateMasterProduct,
  useAssignToStore,
  useRemoveFromStore,
  useUpdateStoreOverride,
} from '@/api/master-products';
import { useStores } from '@/api/stores';
import type { StoreProductAssignment, AssignToStoreRequest } from '@/types/master-product';

export const Route = createFileRoute('/master-products/$masterProductId')({
  component: MasterProductDetailPage,
});

function MasterProductDetailPage() {
  const { masterProductId } = Route.useParams();
  const { data: product, isLoading } = useMasterProduct(masterProductId);
  const { data: assignments } = useMasterProductStores(masterProductId);
  const updateMutation = useUpdateMasterProduct();
  const assignMutation = useAssignToStore();
  const removeMutation = useRemoveFromStore();
  const overrideMutation = useUpdateStoreOverride();
  const { data: allStores } = useStores({ size: 100 });

  const [showAssign, setShowAssign] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [name, setName] = useState('');
  const [editingPrice, setEditingPrice] = useState(false);
  const [basePrice, setBasePrice] = useState('');

  if (isLoading) return <div className="text-center py-12 text-slate-400">Loading…</div>;
  if (!product) return <div className="text-center py-12 text-slate-400">Product not found.</div>;

  const assignedStoreIds = new Set(assignments?.map(a => a.store_id) ?? []);
  const unassignedStores = allStores?.data?.filter(s => !assignedStoreIds.has(s.id)) ?? [];

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-sm text-slate-400">
        <Link to="/master-products" className="hover:text-brand-400">Product Catalog</Link>
        <span>/</span>
        <span className="text-white">{product.name}</span>
      </div>

      <div className="panel-header">
        <div>
          <h1 className="panel-title">{product.name}</h1>
          <p className="panel-subtitle">
            SKU: {product.sku || '—'} | Barcode: {product.barcode || '—'} | {product.store_count} stores
          </p>
        </div>
      </div>

      {/* Product Details */}
      <div className="bg-surface-elevated rounded-xl p-6 space-y-4">
        <h2 className="text-lg font-semibold text-white">Product Details</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <InfoField label="Name" value={product.name} />
          <InfoField label="SKU" value={product.sku || '—'} />
          <InfoField label="Barcode" value={product.barcode || '—'} />
          <InfoField label="Base Price" value={product.base_price.toFixed(2)} />
          <InfoField label="Cost Price" value={product.cost_price.toFixed(2)} />
          <InfoField label="Status" value={product.is_active ? 'Active' : 'Inactive'} />
        </div>
        {product.description && (
          <div>
            <span className="text-sm text-slate-400">Description</span>
            <p className="text-sm text-white mt-1">{product.description}</p>
          </div>
        )}
      </div>

      {/* Store Assignments */}
      <div className="bg-surface-elevated rounded-xl p-6 space-y-4">
        <div className="flex justify-between items-center">
          <h2 className="text-lg font-semibold text-white">Store Assignments</h2>
          <button onClick={() => setShowAssign(true)} className="btn btn-primary btn-sm">
            + Assign Store
          </button>
        </div>

        {assignments && assignments.length > 0 ? (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-surface-border text-left text-slate-400">
                <th className="px-4 py-2 font-medium">Store</th>
                <th className="px-4 py-2 font-medium text-right">Local Price</th>
                <th className="px-4 py-2 font-medium text-right">Stock</th>
                <th className="px-4 py-2 font-medium text-center">Status</th>
                <th className="px-4 py-2 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {assignments.map((a: StoreProductAssignment) => (
                <tr key={a.store_id} className="border-b border-surface-border/50">
                  <td className="px-4 py-2 text-white">{a.store_name}</td>
                  <td className="px-4 py-2 text-right">
                    {a.local_price != null ? a.local_price.toFixed(2) : <span className="text-slate-500">base</span>}
                  </td>
                  <td className="px-4 py-2 text-right">{a.local_stock_qty}</td>
                  <td className="px-4 py-2 text-center">
                    <span className={`px-2 py-0.5 rounded-full text-xs ${a.is_active ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}`}>
                      {a.is_active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-right">
                    <button
                      onClick={() => removeMutation.mutate({ masterProductId, storeId: a.store_id })}
                      className="text-red-400 hover:text-red-300 text-xs"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p className="text-sm text-slate-400 py-4 text-center">
            Not assigned to any stores yet.
          </p>
        )}
      </div>

      {showAssign && (
        <AssignStoreDialog
          unassignedStores={unassignedStores}
          onClose={() => setShowAssign(false)}
          onAssign={(storeId, data) => {
            assignMutation.mutate({ masterProductId, storeId, data }, { onSuccess: () => setShowAssign(false) });
          }}
          isLoading={assignMutation.isPending}
        />
      )}
    </div>
  );
}

function InfoField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="text-sm text-slate-400">{label}</span>
      <p className="text-sm text-white mt-0.5">{value}</p>
    </div>
  );
}

function AssignStoreDialog({
  unassignedStores,
  onClose,
  onAssign,
  isLoading,
}: {
  unassignedStores: Array<{ id: string; name: string }>;
  onClose: () => void;
  onAssign: (storeId: string, data?: AssignToStoreRequest) => void;
  isLoading: boolean;
}) {
  const [selectedStore, setSelectedStore] = useState('');
  const [localPrice, setLocalPrice] = useState('');

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-surface-elevated rounded-xl shadow-xl p-6 w-full max-w-md">
        <h2 className="text-lg font-semibold text-white mb-4">Assign to Store</h2>
        <div className="space-y-4">
          <div>
            <label className="block text-sm text-slate-400 mb-1">Store</label>
            <select
              value={selectedStore}
              onChange={(e) => setSelectedStore(e.target.value)}
              className="w-full h-10 bg-surface border border-surface-border rounded-lg px-3 text-sm text-white"
            >
              <option value="">Select a store…</option>
              {unassignedStores.map(s => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm text-slate-400 mb-1">Local Price Override (optional)</label>
            <input
              type="number"
              step="0.01"
              value={localPrice}
              onChange={(e) => setLocalPrice(e.target.value)}
              placeholder="Leave blank to use base price"
              className="w-full h-10 bg-surface border border-surface-border rounded-lg px-3 text-sm text-white"
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button onClick={onClose} className="btn btn-secondary">Cancel</button>
            <button
              disabled={!selectedStore || isLoading}
              onClick={() =>
                onAssign(selectedStore, localPrice ? { local_price: parseFloat(localPrice) } : undefined)
              }
              className="btn btn-primary"
            >
              {isLoading ? 'Assigning…' : 'Assign'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
