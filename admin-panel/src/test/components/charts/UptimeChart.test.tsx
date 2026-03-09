import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '../../utils';
import { UptimeChart } from '@/components/charts/UptimeChart';

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  BarChart: ({ data, children }: { data?: unknown[]; children?: React.ReactNode }) => (
    <div data-testid="bar-chart" data-points={data?.length ?? 0}>{children}</div>
  ),
  Bar: () => null,
  Cell: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  CartesianGrid: () => null,
}));

interface UptimeDataPoint {
  service: string;
  uptimePercent: number;
}

const mockUptimeData: UptimeDataPoint[] = [
  { service: 'API', uptimePercent: 99.95 },
  { service: 'License', uptimePercent: 99.8 },
  { service: 'Sync', uptimePercent: 97.5 },
];

describe('UptimeChart', () => {
  it('renders with empty data without error', () => {
    render(<UptimeChart data={[]} />);
    expect(screen.getByTestId('bar-chart')).toBeInTheDocument();
    const chart = screen.getByTestId('bar-chart');
    expect(chart).toHaveAttribute('data-points', '0');
  });

  it('renders with service uptime data', () => {
    render(<UptimeChart data={mockUptimeData} />);
    const chart = screen.getByTestId('bar-chart');
    expect(chart).toBeInTheDocument();
    expect(chart).toHaveAttribute('data-points', '3');
  });

  it('shows loading skeleton when isLoading is true', () => {
    const { container } = render(<UptimeChart isLoading={true} />);
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    expect(screen.queryByTestId('bar-chart')).not.toBeInTheDocument();
  });
});
