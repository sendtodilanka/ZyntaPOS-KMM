import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import {
  ArrowRightLeft, ChevronDown, Check, Send, PackageCheck, X,
} from 'lucide-react';
import { DataTable, type Column } from '@/components/shared/DataTable';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { useTransfers, useApproveTransfer, useDispatchTransfer, useReceiveTransfer, useCancelTransfer } from '@/api/transfers';
import { useAuth } from '@/hooks/use-auth';
import { cn } from '@/lib/utils';
import type { StockTransfer, TransferStatus } from '@/types/transfer';

export const Route = createFileRoute('/transfers/')({
  component: TransfersPage,
});

// ── Status helpers ────────────────────────────────────────────────────────────

const STATUS_LABELS: Record<TransferStatus, string> = {
  PENDING:    'Pending',
  APPROVED:   'Approved',
  IN_TRANSIT: 'In Transit',
  RECEIVED:   'Received',
  COMMITTED:  'Committed',
  CANCELLED:  'Cancelled',
};

function formatEpoch(ms: number | null): string {
  if (!ms) return '—';
  return new Date(ms).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

// ── Confirm action dialog state ───────────────────────────────────────────────

type PendingAction =
  | { type: 'approve';  transfer: StockTransfer }
  | { type: 'dispatch'; transfer: StockTransfer }
  | { type: 'receive';  transfer: StockTransfer }
  | { type: 'cancel';   transfer: StockTransfer }
  | null;

// ── Page ──────────────────────────────────────────────────────────────────────

function TransfersPage() {
  const { user, hasPermission } = useAuth();
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<TransferStatus | ''>('');
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);

  const { data, isLoading } = useTransfers({
    page, size: 20,
    status: statusFilter || undefined,
  });

  const approve   = useApproveTransfer();
  const dispatch  = useDispatchTransfer();
  const receive   = useReceiveTransfer();
  const cancel    = useCancelTransfer();

  const actorId = user?.id ?? 'admin';
  const canWrite = hasPermission('store:sync:manage');

  function handleConfirm() {
    if (!pendingAction) return;
    const { type, transfer } = pendingAction;
    switch (type) {
      case 'approve':
        approve.mutate({ id: transfer.id, body: { approvedBy: actorId } });
        break;
      case 'dispatch':
        dispatch.mutate({ id: transfer.id, body: { dispatchedBy: actorId } });
        break;
      case 'receive':
        receive.mutate({ id: transfer.id, body: { receivedBy: actorId } });
        break;
      case 'cancel':
        cancel.mutate({ id: transfer.id, cancelledBy: actorId });
        break;
    }
    setPendingAction(null);
  }

  const isActionLoading =
    approve.isPending || dispatch.isPending || receive.isPending || cancel.isPending;

  // ── Table columns ───────────────────────────────────────────────────────────

  const columns: Column<StockTransfer>[] = [
    {
      key:    'route',
      header: 'Route',
      cell: (t) => (
        <span className="text-slate-300 text-sm flex items-center gap-1">
          <span className="font-mono text-xs text-slate-400 max-w-[80px] truncate" title={t.sourceWarehouseId}>
            {t.sourceStoreId ?? t.sourceWarehouseId.slice(0, 8)}
          </span>
          <ArrowRightLeft className="w-3 h-3 text-slate-500 shrink-0" />
          <span className="font-mono text-xs text-slate-400 max-w-[80px] truncate" title={t.destWarehouseId}>
            {t.destStoreId ?? t.destWarehouseId.slice(0, 8)}
          </span>
        </span>
      ),
    },
    {
      key:    'quantity',
      header: 'Qty',
      cell: (t) => <span className="font-mono text-slate-200">{t.quantity}</span>,
    },
    {
      key:    'status',
      header: 'Status',
      cell: (t) => <StatusBadge status={t.status} />,
    },
    {
      key:    'createdBy',
      header: 'Created By',
      cell: (t) => (
        <span className="text-sm text-slate-400 truncate max-w-[100px]" title={t.createdBy ?? undefined}>
          {t.createdBy ?? '—'}
        </span>
      ),
    },
    {
      key:    'updatedAt',
      header: 'Last Updated',
      cell: (t) => (
        <span className="text-sm text-slate-400 whitespace-nowrap">
          {formatEpoch(t.updatedAt)}
        </span>
      ),
    },
    ...(canWrite ? [{
      key:    'actions',
      header: '',
      cell: (t: StockTransfer) => <TransferActions transfer={t} onAction={setPendingAction} />,
    }] : []),
  ];

  // ── Confirm dialog props ───────────────────────────────────────────────────

  const confirmProps = pendingAction ? ({
    approve:  { title: 'Approve transfer',  description: 'Approve this transfer? The sender will be notified to dispatch goods.',  confirmLabel: 'Approve',  variant: 'default'      },
    dispatch: { title: 'Dispatch transfer', description: 'Mark this transfer as IN_TRANSIT? This confirms goods have left the source warehouse.', confirmLabel: 'Dispatch', variant: 'default' },
    receive:  { title: 'Receive transfer',  description: 'Mark goods as RECEIVED? This completes the inter-store transfer.',       confirmLabel: 'Receive',  variant: 'default'      },
    cancel:   { title: 'Cancel transfer',   description: 'Cancel this transfer? No stock will be moved.',                         confirmLabel: 'Cancel',   variant: 'destructive'  },
  }[pendingAction.type] as const) : null;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title">Stock Transfers</h1>
          <p className="panel-subtitle">
            {data?.total ?? 0} inter-store transfer{(data?.total ?? 0) !== 1 ? 's' : ''}
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <select
          aria-label="Filter by status"
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value as TransferStatus | ''); setPage(0); }}
          className="h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500 min-w-[160px]"
        >
          <option value="">All Statuses</option>
          {(['PENDING', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED'] as TransferStatus[]).map((s) => (
            <option key={s} value={s}>{STATUS_LABELS[s]}</option>
          ))}
        </select>

        {/* Active count badges */}
        <div className="flex items-center gap-2 text-sm text-slate-400">
          {(['PENDING', 'APPROVED', 'IN_TRANSIT'] as TransferStatus[]).map((s) => {
            const count = data?.transfers.filter((t) => t.status === s).length ?? 0;
            if (!count) return null;
            return (
              <button
                key={s}
                onClick={() => setStatusFilter(statusFilter === s ? '' : s)}
                className={cn(
                  'px-2 py-0.5 rounded-full text-xs font-medium border transition-colors',
                  statusFilter === s
                    ? 'bg-brand-600 text-white border-brand-500'
                    : 'bg-surface-elevated border-surface-border text-slate-400 hover:text-slate-200',
                )}
              >
                {STATUS_LABELS[s]}: {count}
              </button>
            );
          })}
        </div>
      </div>

      {/* Table */}
      <DataTable<StockTransfer>
        columns={columns}
        data={data?.transfers ?? []}
        isLoading={isLoading}
        page={page}
        totalPages={Math.ceil((data?.total ?? 0) / 20)}
        total={data?.total ?? 0}
        onPageChange={setPage}
        rowKey={(t) => t.id}
        emptyTitle="No transfers"
        emptyDescription="No inter-store stock transfers match the current filter."
      />

      {/* Confirm dialog */}
      {pendingAction && confirmProps && (
        <ConfirmDialog
          open
          onClose={() => setPendingAction(null)}
          onConfirm={handleConfirm}
          title={confirmProps.title}
          description={confirmProps.description}
          confirmLabel={confirmProps.confirmLabel}
          variant={confirmProps.variant as 'default' | 'destructive'}
          isLoading={isActionLoading}
        />
      )}
    </div>
  );
}

// ── Inline action menu ────────────────────────────────────────────────────────

function TransferActions({
  transfer,
  onAction,
}: {
  transfer: StockTransfer;
  onAction: (a: PendingAction) => void;
}) {
  const [open, setOpen] = useState(false);
  const { status } = transfer;

  const canApprove  = status === 'PENDING';
  const canDispatch = status === 'APPROVED';
  const canReceive  = status === 'IN_TRANSIT';
  const canCancel   = status === 'PENDING' || status === 'APPROVED';

  if (!canApprove && !canDispatch && !canReceive && !canCancel) return null;

  return (
    <div className="relative flex justify-end">
      <button
        onClick={(e) => { e.stopPropagation(); setOpen((v) => !v); }}
        className="p-1.5 rounded hover:bg-surface-elevated text-slate-400 hover:text-slate-200 transition-colors"
        aria-label="Transfer actions"
      >
        <ChevronDown className="w-4 h-4" />
      </button>

      {open && (
        <>
          <div
            className="fixed inset-0 z-10"
            onClick={() => setOpen(false)}
          />
          <div className="absolute right-0 top-full mt-1 z-20 min-w-[150px] bg-surface-elevated border border-surface-border rounded-lg shadow-xl py-1">
            {canApprove && (
              <ActionItem
                icon={<Check className="w-3.5 h-3.5" />}
                label="Approve"
                color="text-emerald-400"
                onClick={() => { setOpen(false); onAction({ type: 'approve', transfer }); }}
              />
            )}
            {canDispatch && (
              <ActionItem
                icon={<Send className="w-3.5 h-3.5" />}
                label="Dispatch"
                color="text-blue-400"
                onClick={() => { setOpen(false); onAction({ type: 'dispatch', transfer }); }}
              />
            )}
            {canReceive && (
              <ActionItem
                icon={<PackageCheck className="w-3.5 h-3.5" />}
                label="Mark Received"
                color="text-violet-400"
                onClick={() => { setOpen(false); onAction({ type: 'receive', transfer }); }}
              />
            )}
            {canCancel && (
              <ActionItem
                icon={<X className="w-3.5 h-3.5" />}
                label="Cancel"
                color="text-red-400"
                onClick={() => { setOpen(false); onAction({ type: 'cancel', transfer }); }}
              />
            )}
          </div>
        </>
      )}
    </div>
  );
}

function ActionItem({
  icon, label, color, onClick,
}: {
  icon: React.ReactNode;
  label: string;
  color: string;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-surface-hover transition-colors text-left',
        color,
      )}
    >
      {icon}
      {label}
    </button>
  );
}
