import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Cell,
} from 'recharts';
import { formatCurrency, truncate } from '@/lib/utils';
import type { StoreComparisonData } from '@/types/metrics';
import { TableSkeleton } from '../shared/LoadingState';

interface StoreComparisonChartProps {
  data?: StoreComparisonData[];
  isLoading?: boolean;
}

function CustomTooltip({ active, payload }: {
  active?: boolean;
  payload?: Array<{ payload: StoreComparisonData }>;
}) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div className="bg-surface-card border border-surface-border rounded-lg p-3 shadow-xl text-xs">
      <p className="font-semibold text-slate-100 mb-2">{d.storeName}</p>
      <div className="space-y-1">
        <div className="flex justify-between gap-4">
          <span className="text-slate-400">Revenue</span>
          <span className="text-slate-100 font-medium">{formatCurrency(d.revenue)}</span>
        </div>
        <div className="flex justify-between gap-4">
          <span className="text-slate-400">Orders</span>
          <span className="text-slate-100 font-medium">{d.orders.toLocaleString()}</span>
        </div>
        <div className="flex justify-between gap-4">
          <span className="text-slate-400">Growth</span>
          <span className={d.growth >= 0 ? 'text-emerald-400' : 'text-red-400'}>
            {d.growth >= 0 ? '+' : ''}{d.growth.toFixed(1)}%
          </span>
        </div>
      </div>
    </div>
  );
}

const COLORS = ['#0ea5e9', '#38bdf8', '#7dd3fc', '#bae6fd', '#e0f2fe'];

export function StoreComparisonChart({ data = [], isLoading = false }: StoreComparisonChartProps) {
  if (isLoading) return <TableSkeleton rows={3} />;

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#334155" horizontal vertical={false} />
        <XAxis
          dataKey="storeName"
          tickFormatter={(v) => truncate(v, 8)}
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
        <Bar dataKey="revenue" name="Revenue" radius={[4, 4, 0, 0]}>
          {data.map((_, index) => (
            <Cell key={index} fill={COLORS[index % COLORS.length]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
