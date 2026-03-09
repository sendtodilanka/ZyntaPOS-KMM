import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '../../utils';
import { SyncHealthChart } from '@/components/charts/SyncHealthChart';

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  LineChart: ({ data, children }: { data?: unknown[]; children?: React.ReactNode }) => (
    <div data-testid="line-chart" data-points={data?.length ?? 0}>{children}</div>
  ),
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  CartesianGrid: () => null,
  ReferenceLine: () => null,
}));

interface SyncDataPoint {
  time: string;
  queueDepth: number;
}

const mockSyncData: SyncDataPoint[] = [
  { time: '00:00', queueDepth: 5 },
  { time: '01:00', queueDepth: 12 },
  { time: '02:00', queueDepth: 3 },
];

describe('SyncHealthChart', () => {
  it('renders with empty data without error', () => {
    render(<SyncHealthChart data={[]} />);
    expect(screen.getByTestId('line-chart')).toBeInTheDocument();
    const chart = screen.getByTestId('line-chart');
    expect(chart).toHaveAttribute('data-points', '0');
  });

  it('renders with sync data points', () => {
    render(<SyncHealthChart data={mockSyncData} />);
    const chart = screen.getByTestId('line-chart');
    expect(chart).toBeInTheDocument();
    expect(chart).toHaveAttribute('data-points', '3');
  });

  it('shows loading skeleton when isLoading is true', () => {
    const { container } = render(<SyncHealthChart isLoading={true} />);
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    expect(screen.queryByTestId('line-chart')).not.toBeInTheDocument();
  });
});
