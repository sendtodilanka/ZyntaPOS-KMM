import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '../../utils';
import { StoreComparisonChart } from '@/components/charts/StoreComparisonChart';
import type { StoreComparisonData } from '@/types/metrics';

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

const mockData: StoreComparisonData[] = [
  { storeId: 'store-1', storeName: 'Colombo Store', revenue: 1500000, orders: 42, growth: 5.2 },
  { storeId: 'store-2', storeName: 'Kandy Store', revenue: 900000, orders: 28, growth: -1.3 },
];

describe('StoreComparisonChart', () => {
  it('renders without error with empty data', () => {
    const { container } = render(<StoreComparisonChart data={[]} />);
    expect(container).toBeInTheDocument();
    expect(screen.getByTestId('bar-chart')).toBeInTheDocument();
  });

  it('renders with store data and passes correct count', () => {
    render(<StoreComparisonChart data={mockData} />);
    const chart = screen.getByTestId('bar-chart');
    expect(chart).toBeInTheDocument();
    expect(chart).toHaveAttribute('data-points', '2');
  });

  it('shows loading skeleton when isLoading is true', () => {
    const { container } = render(<StoreComparisonChart isLoading={true} />);
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    expect(screen.queryByTestId('bar-chart')).not.toBeInTheDocument();
  });
});
