import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { RefreshCw, AlertTriangle, Trash2, CheckCircle2, XCircle } from 'lucide-react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import {
  useReplenishmentRules,
  useReplenishmentSuggestions,
  useDeleteReplenishmentRule,
} from '@/api/replenishment';
import { useAuth } from '@/hooks/use-auth';
import { cn } from '@/lib/utils';
import type { ReplenishmentRule, ReplenishmentSuggestion } from '@/types/replenishment';

export const Route = createFileRoute('/replenishment/')({
  component: ReplenishmentPage,
});

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatEpoch(ms: number | null): string {
  if (!ms) return '\u2014';
  return new Date(ms).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

type Tab = 'suggestions' | 'rules';

// ── Page ─────────────────────────────────────────────────────────────────────

function ReplenishmentPage() {
  const { hasPermission } = useAuth();
  const [tab, setTab] = useState<Tab>('suggestions');
  const [deleteTarget, setDeleteTarget] = useState<ReplenishmentRule | null>(null);

  const suggestionsQuery = useReplenishmentSuggestions();
  const rulesQuery = useReplenishmentRules();
  const deleteMutation = useDeleteReplenishmentRule();

  const canWrite = hasPermission('inventory:write');

  // ── Suggestion columns ───────────────────────────────────────────────────

  const suggestionColumns: Column<ReplenishmentSuggestion>[] = [
    { key: 'productId',    header: 'Product ID',    cell: (r) => <span className="font-mono text-xs">{r.productId.slice(0, 8)}</span> },
    { key: 'warehouseId',  header: 'Warehouse ID',  cell: (r) => <span className="font-mono text-xs">{r.warehouseId.slice(0, 8)}</span> },
    { key: 'currentStock', header: 'Current Stock',  cell: (r) => (
      <span className={cn('font-medium', r.currentStock <= r.reorderPoint ? 'text-red-400' : 'text-slate-300')}>
        {r.currentStock.toFixed(1)}
      </span>
    )},
    { key: 'reorderPoint', header: 'Reorder Point', cell: (r) => r.reorderPoint.toFixed(1) },
    { key: 'reorderQty',   header: 'Reorder Qty',   cell: (r) => r.reorderQty.toFixed(1) },
    { key: 'supplierId',   header: 'Supplier ID',   cell: (r) => <span className="font-mono text-xs">{r.supplierId.slice(0, 8)}</span> },
    { key: 'autoApprove',  header: 'Auto-approve',  cell: (r) =>
      r.autoApprove
        ? <CheckCircle2 className="w-4 h-4 text-green-400" />
        : <XCircle className="w-4 h-4 text-slate-500" />
    },
  ];

  // ── Rule columns ─────────────────────────────────────────────────────────

  const ruleColumns: Column<ReplenishmentRule>[] = [
    { key: 'id',           header: 'Rule ID',       cell: (r) => <span className="font-mono text-xs">{r.id.slice(0, 8)}</span> },
    { key: 'productId',    header: 'Product ID',    cell: (r) => <span className="font-mono text-xs">{r.productId.slice(0, 8)}</span> },
    { key: 'warehouseId',  header: 'Warehouse ID',  cell: (r) => <span className="font-mono text-xs">{r.warehouseId.slice(0, 8)}</span> },
    { key: 'supplierId',   header: 'Supplier ID',   cell: (r) => <span className="font-mono text-xs">{r.supplierId.slice(0, 8)}</span> },
    { key: 'reorderPoint', header: 'Reorder Point', cell: (r) => r.reorderPoint.toFixed(1) },
    { key: 'reorderQty',   header: 'Reorder Qty',   cell: (r) => r.reorderQty.toFixed(1) },
    { key: 'autoApprove',  header: 'Auto-approve',  cell: (r) =>
      r.autoApprove
        ? <CheckCircle2 className="w-4 h-4 text-green-400" />
        : <XCircle className="w-4 h-4 text-slate-500" />
    },
    { key: 'isActive',     header: 'Active',        cell: (r) =>
      r.isActive
        ? <span className="text-green-400 text-xs font-medium">Active</span>
        : <span className="text-slate-500 text-xs">Inactive</span>
    },
    { key: 'updatedAt',    header: 'Updated',       cell: (r) => formatEpoch(r.updatedAt) },
    ...(canWrite ? [{
      key: 'actions',
      header: '',
      cell: (r: ReplenishmentRule) => (
        <button
          onClick={() => setDeleteTarget(r)}
          className="p-1.5 rounded text-slate-400 hover:text-red-400 hover:bg-red-400/10 transition-colors"
          title="Delete rule"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      ),
    }] : []),
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-100">Replenishment</h1>
          <p className="text-sm text-slate-400 mt-0.5">
            Auto-replenishment rules and reorder suggestions (C1.5)
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-surface-elevated rounded-lg p-1 w-fit">
        <button
          onClick={() => setTab('suggestions')}
          className={cn(
            'px-4 py-2 text-sm font-medium rounded-md transition-colors flex items-center gap-2',
            tab === 'suggestions'
              ? 'bg-brand-500/20 text-brand-400'
              : 'text-slate-400 hover:text-slate-200',
          )}
        >
          <AlertTriangle className="w-4 h-4" />
          Reorder Alerts
          {suggestionsQuery.data && (
            <span className="ml-1 bg-red-500/20 text-red-400 text-xs px-1.5 py-0.5 rounded-full">
              {suggestionsQuery.data.total}
            </span>
          )}
        </button>
        <button
          onClick={() => setTab('rules')}
          className={cn(
            'px-4 py-2 text-sm font-medium rounded-md transition-colors flex items-center gap-2',
            tab === 'rules'
              ? 'bg-brand-500/20 text-brand-400'
              : 'text-slate-400 hover:text-slate-200',
          )}
        >
          <RefreshCw className="w-4 h-4" />
          Rules ({rulesQuery.data?.total ?? 0})
        </button>
      </div>

      {/* Content */}
      {tab === 'suggestions' && (
        <div className="bg-surface-card rounded-xl border border-surface-border">
          {suggestionsQuery.isLoading ? (
            <div className="flex items-center justify-center py-16 text-slate-400">Loading suggestions...</div>
          ) : suggestionsQuery.error ? (
            <div className="flex items-center justify-center py-16 text-red-400">Failed to load suggestions</div>
          ) : suggestionsQuery.data?.suggestions.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-slate-400">
              <CheckCircle2 className="w-10 h-10 mb-3 text-green-400" />
              <p className="font-medium text-slate-300">All stock levels are healthy</p>
              <p className="text-sm mt-1">No products are at or below their reorder point.</p>
            </div>
          ) : (
            <DataTable
              columns={suggestionColumns}
              data={suggestionsQuery.data?.suggestions ?? []}
              rowKey={(r) => r.ruleId}
            />
          )}
        </div>
      )}

      {tab === 'rules' && (
        <div className="bg-surface-card rounded-xl border border-surface-border">
          {rulesQuery.isLoading ? (
            <div className="flex items-center justify-center py-16 text-slate-400">Loading rules...</div>
          ) : rulesQuery.error ? (
            <div className="flex items-center justify-center py-16 text-red-400">Failed to load rules</div>
          ) : rulesQuery.data?.rules.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-slate-400">
              <RefreshCw className="w-10 h-10 mb-3" />
              <p className="font-medium text-slate-300">No replenishment rules</p>
              <p className="text-sm mt-1">Rules are created from the POS terminal or via sync.</p>
            </div>
          ) : (
            <DataTable
              columns={ruleColumns}
              data={rulesQuery.data?.rules ?? []}
              rowKey={(r) => r.id}
            />
          )}
        </div>
      )}

      {/* Delete confirm dialog */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Delete Replenishment Rule"
        description={`Are you sure you want to delete the rule for product ${deleteTarget?.productId.slice(0, 8)}...? This action cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        isLoading={deleteMutation.isPending}
        onConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget.id, {
              onSuccess: () => setDeleteTarget(null),
            });
          }
        }}
        onClose={() => setDeleteTarget(null)}
      />
    </div>
  );
}
