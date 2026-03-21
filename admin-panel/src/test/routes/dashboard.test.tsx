import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../utils';
import React from 'react';

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => opts,
  useNavigate: () => vi.fn(),
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}));

// Mock chart components (they render SVG which jsdom doesn't handle well)
vi.mock('@/components/charts/SalesChart', () => ({
  SalesChart: () => <div data-testid="sales-chart" />,
}));
vi.mock('@/components/charts/StoreComparisonChart', () => ({
  StoreComparisonChart: () => <div data-testid="store-comparison-chart" />,
}));
vi.mock('@/components/charts/LicenseDistribution', () => ({
  LicenseDistribution: () => <div data-testid="license-distribution-chart" />,
}));
vi.mock('@/components/charts/UptimeChart', () => ({
  UptimeChart: () => <div data-testid="uptime-chart" />,
}));

vi.mock('@/api/metrics');
vi.mock('@/api/alerts');
vi.mock('@/api/health');

import { useDashboardKPIs, useSalesChart, useStoreComparison } from '@/api/metrics';
import { useAlerts } from '@/api/alerts';
import { useSystemHealth } from '@/api/health';
import { Route } from '@/routes/index';

const DashboardPage = (Route as { component: React.FC }).component;

const mockKPIs = {
  totalStores: 8,
  totalStoresTrend: 2,
  activeLicenses: 12,
  activeLicensesTrend: 1,
  revenueToday: 4500000,
  revenueTodayTrend: 12.5,
  syncHealthPercent: 98.2,
  syncHealthTrend: -0.3,
  currency: 'LKR',
};

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.mocked(useDashboardKPIs).mockReturnValue({
      data: mockKPIs,
      isLoading: false,
    } as ReturnType<typeof useDashboardKPIs>);

    vi.mocked(useSalesChart).mockReturnValue({
      data: { labels: [], values: [] },
      isLoading: false,
    } as ReturnType<typeof useSalesChart>);

    vi.mocked(useStoreComparison).mockReturnValue({
      data: { stores: [] },
      isLoading: false,
    } as ReturnType<typeof useStoreComparison>);

    vi.mocked(useAlerts).mockReturnValue({
      data: { items: [], total: 0, page: 0, pageSize: 5 },
      isLoading: false,
    } as ReturnType<typeof useAlerts>);

    vi.mocked(useSystemHealth).mockReturnValue({
      data: { overall: 'healthy', services: [], checkedAt: new Date().toISOString() },
      isLoading: false,
      refetch: vi.fn(),
    } as ReturnType<typeof useSystemHealth>);
  });

  it('renders dashboard heading', () => {
    render(<DashboardPage />);
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });

  it('renders subtitle', () => {
    render(<DashboardPage />);
    expect(screen.getByText(/overview of all zyntapos deployments/i)).toBeInTheDocument();
  });

  it('renders period selector', () => {
    render(<DashboardPage />);
    expect(screen.getByText('Today')).toBeInTheDocument();
    expect(screen.getByText('This Week')).toBeInTheDocument();
    expect(screen.getByText('This Month')).toBeInTheDocument();
  });

  it('renders all chart placeholders', () => {
    render(<DashboardPage />);
    expect(screen.getByTestId('sales-chart')).toBeInTheDocument();
    expect(screen.getByTestId('store-comparison-chart')).toBeInTheDocument();
    expect(screen.getByTestId('license-distribution-chart')).toBeInTheDocument();
    expect(screen.getByTestId('uptime-chart')).toBeInTheDocument();
  });

  it('passes "today" period by default to KPI hook', () => {
    render(<DashboardPage />);
    expect(vi.mocked(useDashboardKPIs)).toHaveBeenCalledWith('today');
  });

  it('switches to week period when This Week clicked', async () => {
    render(<DashboardPage />);
    fireEvent.click(screen.getByText('This Week'));
    await waitFor(() => {
      expect(vi.mocked(useDashboardKPIs)).toHaveBeenCalledWith('week');
    });
  });

  it('switches to month period when This Month clicked', async () => {
    render(<DashboardPage />);
    fireEvent.click(screen.getByText('This Month'));
    await waitFor(() => {
      expect(vi.mocked(useDashboardKPIs)).toHaveBeenCalledWith('month');
    });
  });

  it('renders KPI cards with data from API', () => {
    render(<DashboardPage />);
    // Store count rendered
    expect(screen.getByText('8')).toBeInTheDocument();
  });

  it('shows loading skeleton for KPI cards when loading', () => {
    vi.mocked(useDashboardKPIs).mockReturnValue({
      data: undefined,
      isLoading: true,
    } as ReturnType<typeof useDashboardKPIs>);
    render(<DashboardPage />);
    // Page heading still visible
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });

  it('shows alerts section heading', () => {
    render(<DashboardPage />);
    expect(screen.getByText(/recent alerts/i)).toBeInTheDocument();
  });

  it('shows no active alerts message when alerts empty', () => {
    vi.mocked(useAlerts).mockReturnValue({
      data: { items: [], total: 0, page: 0, pageSize: 5 },
      isLoading: false,
    } as ReturnType<typeof useAlerts>);
    render(<DashboardPage />);
    expect(screen.getByText(/no active alerts/i)).toBeInTheDocument();
  });
});
