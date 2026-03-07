import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { LicenseStats } from '@/types/license';
import { Skeleton } from '../shared/LoadingState';

interface LicenseDistributionProps {
  stats?: LicenseStats;
  isLoading?: boolean;
}

const EDITION_COLORS = {
  STARTER: '#94a3b8',
  PROFESSIONAL: '#0ea5e9',
  ENTERPRISE: '#7c3aed',
};

export function LicenseDistribution({ stats, isLoading = false }: LicenseDistributionProps) {
  if (isLoading) return <Skeleton className="h-48 w-full" />;
  if (!stats) return null;

  const data = Object.entries(stats.byEdition)
    .filter(([, count]) => count > 0)
    .map(([edition, count]) => ({ name: edition, value: count }));

  if (data.length === 0) {
    return (
      <div className="flex items-center justify-center h-48 text-slate-500 text-sm">
        No license data
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={200}>
      <PieChart>
        <Pie
          data={data}
          cx="50%"
          cy="50%"
          innerRadius={55}
          outerRadius={80}
          paddingAngle={3}
          dataKey="value"
        >
          {data.map((entry) => (
            <Cell
              key={entry.name}
              fill={EDITION_COLORS[entry.name as keyof typeof EDITION_COLORS] ?? '#64748b'}
            />
          ))}
        </Pie>
        <Tooltip
          contentStyle={{
            backgroundColor: '#1e293b',
            border: '1px solid #334155',
            borderRadius: '8px',
            fontSize: '12px',
          }}
          labelStyle={{ color: '#f1f5f9' }}
          itemStyle={{ color: '#94a3b8' }}
        />
        <Legend
          wrapperStyle={{ fontSize: '11px', color: '#94a3b8' }}
          iconType="circle"
          iconSize={8}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}
