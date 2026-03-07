import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } from 'recharts';
import { Skeleton } from '@/components/shared/LoadingState';

interface SyncDataPoint { time: string; queueDepth: number }

interface SyncHealthChartProps {
  data?: SyncDataPoint[];
  isLoading?: boolean;
}

export function SyncHealthChart({ data = [], isLoading = false }: SyncHealthChartProps) {
  if (isLoading) return <Skeleton className="h-40 w-full" />;

  return (
    <ResponsiveContainer width="100%" height={160}>
      <LineChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
        <XAxis dataKey="time" tick={{ fill: '#94a3b8', fontSize: 10 }} axisLine={false} tickLine={false} />
        <YAxis tick={{ fill: '#94a3b8', fontSize: 10 }} axisLine={false} tickLine={false} width={32} />
        <Tooltip
          contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px', fontSize: '12px' }}
          labelStyle={{ color: '#f1f5f9' }}
          itemStyle={{ color: '#94a3b8' }}
        />
        <ReferenceLine y={50} stroke="#f59e0b" strokeDasharray="4 4" label={{ value: 'Warning', fill: '#f59e0b', fontSize: 10 }} />
        <Line type="monotone" dataKey="queueDepth" name="Queue Depth" stroke="#0ea5e9" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
      </LineChart>
    </ResponsiveContainer>
  );
}
