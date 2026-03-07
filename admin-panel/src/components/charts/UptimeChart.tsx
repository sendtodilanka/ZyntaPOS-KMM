import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { Skeleton } from '../shared/LoadingState';

interface UptimeDataPoint {
  service: string;
  uptimePercent: number;
}

interface UptimeChartProps {
  data?: UptimeDataPoint[];
  isLoading?: boolean;
}

function getUptimeColor(percent: number): string {
  if (percent >= 99.9) return '#10b981';
  if (percent >= 99) return '#0ea5e9';
  if (percent >= 95) return '#f59e0b';
  return '#ef4444';
}

export function UptimeChart({ data = [], isLoading = false }: UptimeChartProps) {
  if (isLoading) return <Skeleton className="h-40 w-full" />;

  return (
    <ResponsiveContainer width="100%" height={160}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 8, bottom: 0, left: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#334155" horizontal={false} />
        <XAxis
          type="number"
          domain={[90, 100]}
          tickFormatter={(v) => `${v}%`}
          tick={{ fill: '#94a3b8', fontSize: 10 }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          type="category"
          dataKey="service"
          tick={{ fill: '#94a3b8', fontSize: 11 }}
          axisLine={false}
          tickLine={false}
          width={50}
        />
        <Tooltip
          formatter={(value: number) => [`${value.toFixed(2)}%`, 'Uptime']}
          contentStyle={{
            backgroundColor: '#1e293b',
            border: '1px solid #334155',
            borderRadius: '8px',
            fontSize: '12px',
          }}
          labelStyle={{ color: '#f1f5f9' }}
          itemStyle={{ color: '#94a3b8' }}
        />
        <Bar dataKey="uptimePercent" radius={[0, 4, 4, 0]} barSize={20}>
          {data.map((entry, index) => (
            <Cell key={index} fill={getUptimeColor(entry.uptimePercent)} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
