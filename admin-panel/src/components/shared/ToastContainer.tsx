import { useEffect } from 'react';
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react';
import { useUiStore, type Toast } from '@/stores/ui-store';
import { cn } from '@/lib/utils';

const ICONS = {
  success: CheckCircle,
  error: XCircle,
  warning: AlertTriangle,
  default: Info,
};

const STYLES = {
  success: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400',
  error: 'border-red-500/30 bg-red-500/10 text-red-400',
  warning: 'border-amber-500/30 bg-amber-500/10 text-amber-400',
  default: 'border-brand-500/30 bg-brand-500/10 text-brand-400',
};

function ToastItem({ toast }: { toast: Toast }) {
  const { removeToast } = useUiStore();
  const Icon = ICONS[toast.variant];

  useEffect(() => {
    const timer = setTimeout(() => removeToast(toast.id), toast.duration ?? 4000);
    return () => clearTimeout(timer);
  }, [toast.id, toast.duration, removeToast]);

  return (
    <div
      className={cn(
        'flex items-start gap-3 p-3.5 rounded-lg border bg-surface-card shadow-xl animate-fade-in',
        'min-w-[280px] max-w-[360px] sm:max-w-[420px]',
        STYLES[toast.variant],
      )}
      role="alert"
    >
      <Icon className="w-5 h-5 flex-shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-slate-100">{toast.title}</p>
        {toast.description && (
          <p className="text-xs text-slate-400 mt-0.5">{toast.description}</p>
        )}
      </div>
      <button
        onClick={() => removeToast(toast.id)}
        className="p-1 -mr-1 -mt-1 rounded text-slate-400 hover:text-slate-100 transition-colors flex-shrink-0 min-w-[32px] min-h-[32px] flex items-center justify-center"
        aria-label="Dismiss"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}

export function ToastContainer() {
  const { toasts } = useUiStore();

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-20 right-4 z-50 flex flex-col gap-2 md:bottom-6 md:right-6">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} />
      ))}
    </div>
  );
}
