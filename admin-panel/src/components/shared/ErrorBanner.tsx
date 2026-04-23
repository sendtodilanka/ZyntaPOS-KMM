import { AlertCircle } from 'lucide-react';

interface ErrorBannerProps {
  message?: string;
  onRetry?: () => void;
  isRetrying?: boolean;
}

export function ErrorBanner({
  message = 'Failed to load data. Please try again.',
  onRetry,
  isRetrying = false,
}: ErrorBannerProps) {
  return (
    <div className="text-center py-12 flex flex-col items-center gap-3">
      <AlertCircle className="w-6 h-6 text-destructive" />
      <p className="text-destructive text-sm">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          disabled={isRetrying}
          className="px-4 py-1.5 rounded-lg bg-surface-elevated border border-surface-border text-slate-300 text-xs font-medium hover:bg-surface-card disabled:opacity-50 transition-colors"
        >
          {isRetrying ? 'Retrying…' : 'Retry'}
        </button>
      )}
    </div>
  );
}
