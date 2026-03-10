import { Bell, AlertTriangle, CheckCircle, X } from 'lucide-react';
import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from '@tanstack/react-router';
import { useAlerts, useAcknowledgeAlert } from '@/api/alerts';
import type { AlertSeverity } from '@/types/alert';

const SEVERITY_COLOR: Record<AlertSeverity, string> = {
  critical: 'text-red-400',
  high:     'text-orange-400',
  medium:   'text-yellow-400',
  low:      'text-blue-400',
  info:     'text-slate-400',
};

export function NotificationsPanel() {
  const [open, setOpen] = useState(false);
  const [panelPos, setPanelPos] = useState<{ top: number; right: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef  = useRef<HTMLDivElement>(null);
  const navigate  = useNavigate();

  const { data, isLoading } = useAlerts({ status: 'active', pageSize: 10 });
  const acknowledge = useAcknowledgeAlert();

  const alerts     = data?.items ?? [];
  const activeCount = alerts.filter((a) => a.status === 'active').length;

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (
        panelRef.current  && !panelRef.current.contains(e.target as Node) &&
        buttonRef.current && !buttonRef.current.contains(e.target as Node)
      ) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const handleToggle = () => {
    if (!open && buttonRef.current) {
      const rect = buttonRef.current.getBoundingClientRect();
      setPanelPos({ top: rect.bottom + 8, right: window.innerWidth - rect.right });
    }
    setOpen((v) => !v);
  };

  return (
    <>
      <button
        ref={buttonRef}
        onClick={handleToggle}
        aria-label="Notifications"
        className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center relative transition-colors"
      >
        <Bell className="w-5 h-5" />
        {activeCount > 0 && (
          <span className="absolute top-2 right-2 w-2 h-2 bg-red-500 rounded-full animate-pulse" />
        )}
      </button>

      {open && panelPos && createPortal(
        <div
          ref={panelRef}
          style={{ position: 'fixed', top: panelPos.top, right: panelPos.right, zIndex: 9999 }}
          className="w-80 bg-surface-card border border-surface-border rounded-xl shadow-2xl overflow-hidden animate-fade-in"
        >
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-surface-border">
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-semibold text-slate-100">Notifications</h3>
              {activeCount > 0 && (
                <span className="px-1.5 py-0.5 text-[10px] font-semibold bg-red-500/20 text-red-400 rounded-full">
                  {activeCount}
                </span>
              )}
            </div>
            <button
              onClick={() => setOpen(false)}
              className="p-1 rounded text-slate-500 hover:text-slate-300 transition-colors"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>

          {/* Body */}
          <div className="max-h-80 overflow-y-auto">
            {isLoading ? (
              <div className="p-4 space-y-3">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="h-12 bg-surface-elevated rounded animate-pulse" />
                ))}
              </div>
            ) : alerts.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-10 px-4 text-center">
                <CheckCircle className="w-9 h-9 text-green-400 mb-3" />
                <p className="text-sm font-semibold text-slate-300">All clear</p>
                <p className="text-xs text-slate-500 mt-1">No active alerts right now</p>
              </div>
            ) : (
              alerts.map((alert) => (
                <div
                  key={alert.id}
                  className="flex items-start gap-3 px-4 py-3 border-b border-surface-border last:border-0 hover:bg-surface-elevated transition-colors"
                >
                  <AlertTriangle className={`w-4 h-4 mt-0.5 flex-shrink-0 ${SEVERITY_COLOR[alert.severity]}`} />
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold text-slate-200 truncate">{alert.title}</p>
                    <p className="text-[10px] text-slate-500 mt-0.5 truncate">{alert.message}</p>
                    <p className="text-[10px] text-slate-500 mt-0.5 capitalize">
                      {alert.category} · {new Date(alert.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </p>
                  </div>
                  {alert.status === 'active' && (
                    <button
                      onClick={() => acknowledge.mutate(alert.id)}
                      disabled={acknowledge.isPending}
                      className="text-[10px] text-slate-500 hover:text-brand-400 flex-shrink-0 transition-colors disabled:opacity-50 mt-0.5"
                    >
                      Dismiss
                    </button>
                  )}
                </div>
              ))
            )}
          </div>

          {/* Footer */}
          {alerts.length > 0 && (
            <div className="px-4 py-2.5 border-t border-surface-border">
              <button
                onClick={() => { setOpen(false); navigate({ to: '/alerts' }); }}
                className="text-xs text-brand-400 hover:text-brand-300 font-medium transition-colors"
              >
                View all alerts →
              </button>
            </div>
          )}
        </div>,
        document.body,
      )}
    </>
  );
}
