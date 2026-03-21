import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import {
  Monitor, Plus, X, Clock, CheckCircle2, ShieldAlert,
  AlertTriangle, Copy, Check,
} from 'lucide-react';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { useStores } from '@/api/stores';
import {
  useActiveDiagnosticSession,
  useCreateDiagnosticSession,
  useRevokeDiagnosticSession,
} from '@/api/diagnostic';
import { useAuth } from '@/hooks/use-auth';
import { cn } from '@/lib/utils';
import type { DiagnosticSession, DiagnosticDataScope, DiagnosticSessionStatus } from '@/types/diagnostic';
import type { Store } from '@/types/store';

export const Route = createFileRoute('/diagnostic/')({
  component: DiagnosticPage,
});

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatEpoch(ms: number | null | undefined): string {
  if (!ms) return '—';
  return new Date(ms).toLocaleString(undefined, {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function ttlLabel(expiresAt: number): string {
  const remaining = Math.max(0, expiresAt - Date.now());
  const mins = Math.floor(remaining / 60_000);
  const secs = Math.floor((remaining % 60_000) / 1_000);
  if (remaining <= 0) return 'Expired';
  return `${mins}m ${secs}s`;
}

function StatusBadge({ status }: { status: DiagnosticSessionStatus }) {
  const map: Record<DiagnosticSessionStatus, { label: string; cls: string; icon: React.ReactNode }> = {
    PENDING_CONSENT: {
      label: 'Pending Consent',
      cls:   'bg-yellow-500/10 text-yellow-400 border border-yellow-500/20',
      icon:  <Clock className="w-3 h-3" />,
    },
    ACTIVE: {
      label: 'Active',
      cls:   'bg-green-500/10 text-green-400 border border-green-500/20',
      icon:  <CheckCircle2 className="w-3 h-3" />,
    },
    EXPIRED: {
      label: 'Expired',
      cls:   'bg-slate-500/10 text-slate-400 border border-slate-500/20',
      icon:  <AlertTriangle className="w-3 h-3" />,
    },
    REVOKED: {
      label: 'Revoked',
      cls:   'bg-red-500/10 text-red-400 border border-red-500/20',
      icon:  <ShieldAlert className="w-3 h-3" />,
    },
  };
  const { label, cls, icon } = map[status] ?? map.EXPIRED;
  return (
    <span className={cn('inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium', cls)}>
      {icon}{label}
    </span>
  );
}

// ── Create session modal ──────────────────────────────────────────────────────

interface CreateSessionModalProps {
  stores: Store[];
  onClose: () => void;
  onCreated: (session: DiagnosticSession) => void;
}

function CreateSessionModal({ stores, onClose, onCreated }: CreateSessionModalProps) {
  const [storeId, setStoreId]     = useState('');
  const [dataScope, setDataScope] = useState<DiagnosticDataScope>('READ_ONLY_DIAGNOSTICS');
  const createMutation = useCreateDiagnosticSession();
  const { user } = useAuth();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!storeId) return;
    createMutation.mutate(
      { storeId, technicianId: user?.id ?? '', dataScope },
      { onSuccess: (session) => { onCreated(session); onClose(); } },
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-surface-elevated border border-surface-border rounded-xl shadow-xl w-full max-w-md">
        <div className="flex items-center justify-between px-5 py-4 border-b border-surface-border">
          <h2 className="text-base font-semibold text-slate-100 flex items-center gap-2">
            <Monitor className="w-4 h-4 text-brand-400" />
            New Diagnostic Session
          </h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-200 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">
              Target Store <span className="text-red-400">*</span>
            </label>
            <select
              required
              value={storeId}
              onChange={(e) => setStoreId(e.target.value)}
              className="w-full h-10 bg-surface border border-surface-border rounded-lg px-3 text-sm text-slate-300 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="">Select store…</option>
              {stores.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">
              Access Scope
            </label>
            <div className="space-y-2">
              {([
                { value: 'READ_ONLY_DIAGNOSTICS', label: 'Read-only Diagnostics', desc: 'System metrics, logs, device status only.' },
                { value: 'FULL_READ_ONLY',        label: 'Full Read-only',         desc: 'Includes orders, inventory, customer records.' },
              ] as { value: DiagnosticDataScope; label: string; desc: string }[]).map(({ value, label, desc }) => (
                <label key={value} className={cn(
                  'flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors',
                  dataScope === value
                    ? 'border-brand-500/40 bg-brand-500/10'
                    : 'border-surface-border hover:border-slate-600',
                )}>
                  <input
                    type="radio"
                    name="scope"
                    value={value}
                    checked={dataScope === value}
                    onChange={() => setDataScope(value)}
                    className="mt-0.5 accent-brand-500"
                  />
                  <div>
                    <div className="text-sm font-medium text-slate-200">{label}</div>
                    <div className="text-xs text-slate-400 mt-0.5">{desc}</div>
                  </div>
                </label>
              ))}
            </div>
          </div>

          <p className="text-xs text-slate-500 bg-surface rounded-lg px-3 py-2 border border-surface-border">
            <ShieldAlert className="w-3 h-3 inline mr-1 text-yellow-400" />
            The generated token expires in <strong className="text-slate-300">15 minutes</strong>.
            The store operator must grant consent before the session becomes active.
          </p>

          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 h-10 rounded-lg border border-surface-border text-sm text-slate-300 hover:text-slate-100 hover:border-slate-500 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!storeId || createMutation.isPending}
              className="flex-1 h-10 rounded-lg bg-brand-500 text-white text-sm font-medium hover:bg-brand-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {createMutation.isPending ? 'Creating…' : 'Generate Token'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Token display modal ───────────────────────────────────────────────────────

function TokenModal({ session, onClose }: { session: DiagnosticSession; onClose: () => void }) {
  const [copied, setCopied] = useState(false);

  function copyToken() {
    if (!session.token) return;
    navigator.clipboard.writeText(session.token).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
      <div className="bg-surface-elevated border border-surface-border rounded-xl shadow-xl w-full max-w-lg">
        <div className="flex items-center justify-between px-5 py-4 border-b border-surface-border">
          <h2 className="text-base font-semibold text-green-400 flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4" />
            Session Token Generated
          </h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-200 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          <p className="text-sm text-slate-300">
            Deliver this token to the store operator via a secure channel.
            It is displayed <strong className="text-white">only once</strong> and expires in 15 minutes.
          </p>

          <div className="bg-surface rounded-lg border border-surface-border p-3">
            <div className="flex items-start gap-2">
              <code className="flex-1 text-xs text-green-300 font-mono break-all leading-relaxed">
                {session.token ?? '—'}
              </code>
              <button
                onClick={copyToken}
                title="Copy token"
                className="shrink-0 p-1.5 rounded text-slate-400 hover:text-slate-200 hover:bg-surface-elevated transition-colors"
              >
                {copied ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 text-xs">
            <div className="bg-surface rounded-lg p-3 border border-surface-border">
              <div className="text-slate-400 mb-1">Session ID</div>
              <div className="font-mono text-slate-200">{session.id.slice(0, 8)}…</div>
            </div>
            <div className="bg-surface rounded-lg p-3 border border-surface-border">
              <div className="text-slate-400 mb-1">Expires at</div>
              <div className="text-slate-200">{formatEpoch(session.expiresAt)}</div>
            </div>
          </div>

          <button
            onClick={onClose}
            className="w-full h-10 rounded-lg bg-brand-500 text-white text-sm font-medium hover:bg-brand-600 transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Store session row ─────────────────────────────────────────────────────────

function StoreSessionRow({ store, canWrite }: { store: Store; canWrite: boolean }) {
  const { data: session, isLoading } = useActiveDiagnosticSession(store.id);
  const revokeSession = useRevokeDiagnosticSession();
  const [revokeTarget, setRevokeTarget] = useState<string | null>(null);

  return (
    <>
      <tr className="border-b border-surface-border hover:bg-surface-elevated/40 transition-colors">
        <td className="px-4 py-3 text-sm text-slate-200">{store.name}</td>
        <td className="px-4 py-3">
          <span className="font-mono text-xs text-slate-400">{store.id.slice(0, 8)}…</span>
        </td>
        <td className="px-4 py-3">
          {isLoading ? (
            <span className="text-xs text-slate-500">Loading…</span>
          ) : session ? (
            <StatusBadge status={session.status} />
          ) : (
            <span className="text-xs text-slate-500">—</span>
          )}
        </td>
        <td className="px-4 py-3 text-sm text-slate-400">
          {session && (session.status === 'PENDING_CONSENT' || session.status === 'ACTIVE')
            ? ttlLabel(session.expiresAt)
            : '—'}
        </td>
        <td className="px-4 py-3 text-sm text-slate-400">
          {session ? formatEpoch(session.consentGrantedAt) : '—'}
        </td>
        {canWrite && (
          <td className="px-4 py-3">
            {session && (session.status === 'PENDING_CONSENT' || session.status === 'ACTIVE') && (
              <button
                onClick={() => setRevokeTarget(session.id)}
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded text-xs text-red-400 border border-red-400/20 hover:bg-red-400/10 transition-colors"
              >
                <X className="w-3 h-3" />
                Revoke
              </button>
            )}
          </td>
        )}
      </tr>

      {revokeTarget && (
        <ConfirmDialog
          open
          onClose={() => setRevokeTarget(null)}
          onConfirm={() =>
            revokeSession.mutate(revokeTarget, { onSuccess: () => setRevokeTarget(null) })
          }
          title="Revoke Diagnostic Session"
          description="Are you sure you want to revoke this session? The technician will immediately lose access."
          confirmLabel="Revoke"
          variant="destructive"
          isLoading={revokeSession.isPending}
        />
      )}
    </>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

function DiagnosticPage() {
  const { hasPermission } = useAuth();
  const canAccess = hasPermission('diagnostics:access');
  const canRead   = hasPermission('diagnostics:read');

  const [createOpen, setCreateOpen]   = useState(false);
  const [tokenSession, setTokenSession] = useState<DiagnosticSession | null>(null);

  const storesQuery = useStores({ page: 0, size: 100 });
  const stores = storesQuery.data?.data ?? [];

  if (!canRead) {
    return (
      <div className="flex flex-col items-center justify-center h-64 space-y-3 text-slate-500">
        <ShieldAlert className="w-10 h-10" />
        <p className="text-sm">You don't have permission to view diagnostic sessions.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="panel-header flex-wrap gap-3">
        <div>
          <h1 className="panel-title flex items-center gap-2">
            <Monitor className="w-5 h-5 text-brand-400" />
            Remote Diagnostics
          </h1>
          <p className="panel-subtitle">
            Create and manage JIT technician sessions for store diagnostics (ENTERPRISE).
            Each session token expires in 15 minutes and requires store operator consent.
          </p>
        </div>

        {canAccess && (
          <button
            onClick={() => setCreateOpen(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-brand-500 text-white text-sm font-medium hover:bg-brand-600 transition-colors"
          >
            <Plus className="w-4 h-4" />
            New Session
          </button>
        )}
      </div>

      {/* Store sessions table */}
      <div className="bg-surface rounded-xl border border-surface-border overflow-hidden">
        <div className="px-4 py-3 border-b border-surface-border">
          <h2 className="text-sm font-medium text-slate-300">Active Sessions by Store</h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Shows the current diagnostic session for each store. Sessions auto-refresh every 15s.
          </p>
        </div>

        {storesQuery.isLoading ? (
          <div className="p-8 text-center text-slate-500 text-sm">Loading stores…</div>
        ) : stores.length === 0 ? (
          <div className="p-8 text-center space-y-2">
            <Monitor className="w-8 h-8 text-slate-600 mx-auto" />
            <p className="text-sm text-slate-500">No stores found.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-surface-border bg-surface-elevated/50">
                  <th className="px-4 py-2.5 text-left text-xs font-medium text-slate-400 uppercase tracking-wide">Store</th>
                  <th className="px-4 py-2.5 text-left text-xs font-medium text-slate-400 uppercase tracking-wide">ID</th>
                  <th className="px-4 py-2.5 text-left text-xs font-medium text-slate-400 uppercase tracking-wide">Status</th>
                  <th className="px-4 py-2.5 text-left text-xs font-medium text-slate-400 uppercase tracking-wide">Expires In</th>
                  <th className="px-4 py-2.5 text-left text-xs font-medium text-slate-400 uppercase tracking-wide">Consent At</th>
                  {canAccess && (
                    <th className="px-4 py-2.5 text-left text-xs font-medium text-slate-400 uppercase tracking-wide">Actions</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {stores.map((store) => (
                  <StoreSessionRow key={store.id} store={store} canWrite={canAccess} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Info panel */}
      <div className="bg-surface rounded-xl border border-surface-border p-4 space-y-3">
        <h3 className="text-sm font-medium text-slate-300 flex items-center gap-2">
          <ShieldAlert className="w-4 h-4 text-yellow-400" />
          Security Notes
        </h3>
        <ul className="text-xs text-slate-400 space-y-1.5 list-disc list-inside">
          <li>Sessions use RS256-signed JIT tokens with a <strong className="text-slate-300">15-minute TTL</strong>.</li>
          <li>Tokens are shown <strong className="text-slate-300">only once</strong> at creation and are not stored in plaintext.</li>
          <li>The store operator must <strong className="text-slate-300">explicitly grant consent</strong> before a session becomes active.</li>
          <li>All session creation, consent, and revocation events are written to the <strong className="text-slate-300">audit log</strong>.</li>
          <li>Only users with the <code className="text-brand-400">diagnostics:access</code> permission can create or revoke sessions.</li>
        </ul>
      </div>

      {/* Modals */}
      {createOpen && (
        <CreateSessionModal
          stores={stores}
          onClose={() => setCreateOpen(false)}
          onCreated={(session) => {
            setCreateOpen(false);
            setTokenSession(session);
          }}
        />
      )}

      {tokenSession && (
        <TokenModal session={tokenSession} onClose={() => setTokenSession(null)} />
      )}
    </div>
  );
}
