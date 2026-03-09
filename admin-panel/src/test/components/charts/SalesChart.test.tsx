import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '../../utils';
import { SalesChart } from '@/components/charts/SalesChart';
import type { SalesChartData } from '@/types/metrics';

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  AreaChart: ({ data, children }: { data?: unknown[]; children?: React.ReactNode }) => (
    <div data-testid="area-chart" data-points={data?.length ?? 0}>{children}</div>
  ),
  Area: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
  Legend: () => null,
  CartesianGrid: () => null,
}));

const mockData: SalesChartData[] = [
  { period: '2025-01-01', revenue: 1500000, orders: 42, averageOrderValue: 35714 },
];

describe('SalesChart', () => {
  it('renders without error when data is empty', () => {
    const { container } = render(<SalesChart data={[]} />);
    expect(container).toBeInTheDocument();
  });

  it('renders chart container when data is provided', () => {
    render(<SalesChart data={mockData} />);
    expect(screen.getByTestId('area-chart')).toBeInTheDocument();
  });

  it('passes correct data length to chart component', () => {
    render(<SalesChart data={mockData} />);
    const chart = screen.getByTestId('area-chart');
    expect(chart).toHaveAttribute('data-points', '1');
  });

  it('shows loading skeleton when isLoading is true', () => {
    const { container } = render(<SalesChart isLoading={true} />);
    // TableSkeleton renders divs with animate-pulse
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    expect(screen.queryByTestId('area-chart')).not.toBeInTheDocument();
  });
});
