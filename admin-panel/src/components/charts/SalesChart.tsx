import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Legend,
} from 'recharts';
import { format, parseISO } from 'date-fns';
import { formatCurrency } from '@/lib/utils';
import type { SalesChartData } from '@/types/metrics';
import { TableSkeleton } from '../shared/LoadingState';

interface SalesChartProps {
  data?: SalesChartData[];
  isLoading?: boolean;
  granularity?: 'hour' | 'day' | 'week' | 'month';
}

function CustomTooltip({ active, payload, label }: {
  active?: boolean;
  payload?: Array<{ value: number; name: string; color: string }>;
  label?: string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-surface-card border border-surface-border rounded-lg p-3 shadow-xl text-xs">
      <p className="text-slate-400 mb-2">{label}</p>
      {payload.map((p) => (
        <div key={p.name} className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full" style={{ backgroundColor: p.color }} />
          <span className="text-slate-300">{p.name}:</span>
          <span className="font-semibold text-slate-100">
            {p.name === 'Revenue' ? formatCurrency(p.value) : p.value.toLocaleString()}
          </span>
        </div>
      ))}
    </div>
  );
}

export function SalesChart({ data = [], isLoading = false, granularity = 'day' }: SalesChartProps) {
  if (isLoading) return <TableSkeleton rows={3} />;

  const formatXAxis = (value: string) => {
    try {
      const date = parseISO(value);
      if (granularity === 'hour') return format(date, 'HH:mm');
      if (granularity === 'month') return format(date, 'MMM');
      return format(date, 'MMM d');
    } catch {
      return value;
    }
  };

  return (
    <ResponsiveContainer width="100%" height={240}>
      <AreaChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id="revenueGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="#0ea5e9" stopOpacity={0.3} />
            <stop offset="95%" stopColor="#0ea5e9" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
        <XAxis
          dataKey="period"
          tickFormatter={formatXAxis}
          tick={{ fill: '#94a3b8', fontSize: 11 }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          tickFormatter={(v) => `${(v / 1000).toFixed(0)}k`}
          tick={{ fill: '#94a3b8', fontSize: 11 }}
          axisLine={false}
          tickLine={false}
          width={40}
        />
        <Tooltip content={<CustomTooltip />} />
        <Legend
          wrapperStyle={{ fontSize: '11px', color: '#94a3b8', paddingTop: '12px' }}
        />
        <Area
          type="monotone"
          dataKey="revenue"
          name="Revenue"
          stroke="#0ea5e9"
          strokeWidth={2}
          fill="url(#revenueGradient)"
          dot={false}
          activeDot={{ r: 4, fill: '#0ea5e9' }}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
