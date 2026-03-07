import { useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { useForceSync } from '@/api/sync';

interface ForceSyncButtonProps {
  storeId: string;
  storeName: string;
  className?: string;
}

export function ForceSyncButton({ storeId, storeName, className }: ForceSyncButtonProps) {
  const [open, setOpen] = useState(false);
  const forceSync = useForceSync();

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className={`flex items-center gap-2 px-3 py-2 bg-surface-elevated border border-surface-border rounded-lg text-sm text-slate-300 hover:text-slate-100 hover:bg-surface-card transition-colors min-h-[44px] ${className ?? ''}`}
      >
        <RefreshCw className="w-4 h-4" />
        <span className="hidden sm:inline">Force Sync</span>
      </button>
      <ConfirmDialog
        open={open}
        onClose={() => setOpen(false)}
        onConfirm={() => forceSync.mutate(storeId, { onSettled: () => setOpen(false) })}
        title="Force Re-sync"
        description={`Trigger a full re-sync for "${storeName}"? All pending operations will be re-queued.`}
        confirmLabel="Force Sync"
        isLoading={forceSync.isPending}
      />
    </>
  );
}
