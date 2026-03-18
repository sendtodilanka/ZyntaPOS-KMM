import { useState } from 'react';
import { useBulkResolveTickets } from '@/api/tickets';
import { toast } from 'sonner';

interface BulkResolveModalProps {
  ticketIds: string[];
  open: boolean;
  onClose: () => void;
}

export function BulkResolveModal({ ticketIds, open, onClose }: BulkResolveModalProps) {
  const [resolutionNote, setResolutionNote] = useState('');
  const bulkResolve = useBulkResolveTickets();

  if (!open) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!resolutionNote.trim()) return;
    bulkResolve.mutate(
      { ticketIds, resolutionNote: resolutionNote.trim() },
      {
        onSuccess: (result) => {
          toast.success(`${result.updated} tickets resolved${result.failed.length > 0 ? `, ${result.failed.length} failed` : ''}`);
          setResolutionNote('');
          onClose();
        },
      },
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-surface-card border border-surface-border rounded-xl shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-slate-200 mb-4">
          Bulk Resolve ({ticketIds.length} tickets)
        </h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-400 mb-1">Resolution note</label>
            <textarea
              value={resolutionNote}
              onChange={(e) => setResolutionNote(e.target.value)}
              rows={4}
              placeholder="Describe the resolution…"
              className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500 resize-none"
              required
            />
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-slate-400 hover:text-slate-200 transition-colors">
              Cancel
            </button>
            <button
              type="submit"
              disabled={!resolutionNote.trim() || bulkResolve.isPending}
              className="px-4 py-2 text-sm font-medium bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50 transition-colors min-h-[36px]"
            >
              {bulkResolve.isPending ? 'Resolving…' : 'Resolve All'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
