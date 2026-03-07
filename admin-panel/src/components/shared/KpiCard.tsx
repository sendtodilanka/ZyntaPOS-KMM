import { type LucideIcon, TrendingUp, TrendingDown, Minus } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Skeleton } from './LoadingState';

interface KpiCardProps {
  title?: string;
  label?: string;
  value: string | number;
  subtitle?: string;
  trend?: number;
  icon?: LucideIcon;
  iconColor?: string;
  isLoading?: boolean;
  loading?: boolean;
  className?: string;
}

export function KpiCard({
  title,
  label,
  value,
  subtitle,
  trend,
  icon: Icon,
  iconColor = 'text-brand-400',
  isLoading = false,
  loading = false,
  className,
}: KpiCardProps) {
  const heading = label ?? title ?? '';
  if (isLoading || loading) {
    return (
      <div className={cn('panel-card space-y-3', className)}>
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-8 w-32" />
        <Skeleton className="h-3 w-20" />
      </div>
    );
  }

  const TrendIcon = trend === undefined ? null : trend > 0 ? TrendingUp : trend < 0 ? TrendingDown : Minus;
  const trendColor = trend === undefined ? '' : trend > 0 ? 'text-emerald-400' : trend < 0 ? 'text-red-400' : 'text-slate-400';

  return (
    <div className={cn('panel-card group', className)}>
      <div className="flex items-start justify-between mb-3">
        <p className="text-xs font-medium text-slate-400 uppercase tracking-wide">{heading}</p>
        {Icon && (
          <div className="w-8 h-8 rounded-lg bg-surface-elevated flex items-center justify-center">
            <Icon className={cn('w-4 h-4', iconColor)} />
          </div>
        )}
      </div>
      <p className="text-2xl font-bold text-slate-100 mb-1 tabular-nums">{value}</p>
      <div className="flex items-center gap-2">
        {TrendIcon && trend !== undefined && (
          <span className={cn('flex items-center gap-0.5 text-xs font-medium', trendColor)}>
            <TrendIcon className="w-3.5 h-3.5" />
            {Math.abs(trend).toFixed(1)}%
          </span>
        )}
        {subtitle && (
          <span className="text-xs text-slate-500">{subtitle}</span>
        )}
      </div>
    </div>
  );
}
