import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '../../utils';
import { LicenseDistribution } from '@/components/charts/LicenseDistribution';
import type { LicenseStats } from '@/types/license';

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="responsive-container">{children}</div>
  ),
  PieChart: ({ children }: { children?: React.ReactNode }) => (
    <div data-testid="pie-chart">{children}</div>
  ),
  Pie: ({ data }: { data?: unknown[] }) => (
    <div data-testid="pie" data-items={data?.length ?? 0} />
  ),
  Cell: () => null,
  Tooltip: () => null,
  Legend: () => null,
}));

const mockStats: LicenseStats = {
  total: 12,
  active: 10,
  expired: 1,
  revoked: 0,
  suspended: 0,
  expiringSoon: 1,
  byEdition: { STARTER: 3, PROFESSIONAL: 6, ENTERPRISE: 3 },
};

describe('LicenseDistribution', () => {
  it('shows empty state when no stats are provided', () => {
    render(<LicenseDistribution />);
    // Component returns null when stats is undefined
    expect(screen.queryByTestId('pie-chart')).not.toBeInTheDocument();
  });

  it('renders chart when stats are provided', () => {
    render(<LicenseDistribution stats={mockStats} />);
    expect(screen.getByTestId('pie-chart')).toBeInTheDocument();
  });

  it('pie receives entries for each non-zero edition', () => {
    render(<LicenseDistribution stats={mockStats} />);
    // All 3 editions have count > 0, so pie data length = 3
    const pie = screen.getByTestId('pie');
    expect(pie).toHaveAttribute('data-items', '3');
  });

  it('shows loading skeleton when isLoading is true', () => {
    const { container } = render(<LicenseDistribution isLoading={true} />);
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    expect(screen.queryByTestId('pie-chart')).not.toBeInTheDocument();
  });

  it('shows empty state when all edition counts are zero', () => {
    const emptyStats: LicenseStats = {
      ...mockStats,
      byEdition: { STARTER: 0, PROFESSIONAL: 0, ENTERPRISE: 0 },
    };
    render(<LicenseDistribution stats={emptyStats} />);
    expect(screen.getByText('No license data')).toBeInTheDocument();
  });
});
