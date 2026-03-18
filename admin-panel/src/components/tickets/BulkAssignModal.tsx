import { useState } from 'react';
import { useBulkAssignTickets } from '@/api/tickets';
import { useAdminUsers } from '@/api/users';
import { toast } from '@/stores/ui-store';

interface BulkAssignModalProps {
  ticketIds: string[];
  open: boolean;
  onClose: () => void;
}

export function BulkAssignModal({ ticketIds, open, onClose }: BulkAssignModalProps) {
  const [assigneeId, setAssigneeId] = useState('');
  const bulkAssign = useBulkAssignTickets();
  const { data: usersPage } = useAdminUsers({ size: 100 });
  const operators = (usersPage?.data ?? []).filter((u: { isActive: boolean; role: string }) => u.isActive && (u.role === 'ADMIN' || u.role === 'OPERATOR'));

  if (!open) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!assigneeId) return;
    bulkAssign.mutate(
      { ticketIds, assigneeId },
      {
        onSuccess: (result) => {
          toast.success(`${result.updated} tickets assigned`, result.failed.length > 0 ? `${result.failed.length} failed` : undefined);
          onClose();
        },
      },
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-surface-card border border-surface-border rounded-xl shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-slate-200 mb-4">
          Bulk Assign ({ticketIds.length} tickets)
        </h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-400 mb-1">Assign to</label>
            <select
              value={assigneeId}
              onChange={(e) => setAssigneeId(e.target.value)}
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500"
              required
            >
              <option value="">Select operator…</option>
              {operators.map((u: { id: string; name: string; role: string }) => (
                <option key={u.id} value={u.id}>
                  {u.name} ({u.role})
                </option>
              ))}
            </select>
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-slate-400 hover:text-slate-200 transition-colors">
              Cancel
            </button>
            <button
              type="submit"
              disabled={!assigneeId || bulkAssign.isPending}
              className="px-4 py-2 text-sm font-medium bg-brand-700 text-white rounded-lg hover:bg-brand-600 disabled:opacity-50 transition-colors min-h-[36px]"
            >
              {bulkAssign.isPending ? 'Assigning…' : 'Assign'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
