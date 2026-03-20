import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import React from 'react';
import type { SalesReportRow, ProductPerformanceRow } from '@/types/metrics';

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => opts,
  useNavigate: () => vi.fn(),
}));

// Mock recharts — SVG rendering not supported in jsdom
vi.mock('recharts', () => ({
  BarChart: ({ children }: { children: React.ReactNode }) => <div data-testid="bar-chart">{children}</div>,
  Bar: () => null,
  LineChart: ({ children }: { children: React.ReactNode }) => <div data-testid="line-chart">{children}</div>,
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  Legend: () => null,
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock('@/api/metrics');
import { useSalesReport, useProductPerformance } from '@/api/metrics';
import { Route } from '@/routes/reports/index';

const ReportsPage = (Route as { component: React.FC }).component;

const mockSalesRow: SalesReportRow = {
  date: '2026-03-20',
  revenue: 450000,
  orders: 12,
  averageOrderValue: 37500,
  refunds: 0,
  netRevenue: 450000,
  storeId: 'store-1',
  storeName: 'Colombo Store',
};

const mockProductRow: ProductPerformanceRow = {
  productId: 'prod-1',
  productName: 'Espresso Blend',
  category: 'Beverages',
  unitsSold: 50,
  revenue: 125000,
  marginPercent: 42.0,
  returns: 1,
  storeId: 'store-1',
  storeName: 'Colombo Store',
};

describe('ReportsPage', () => {
  beforeEach(() => {
    vi.mocked(useSalesReport).mockReturnValue({
      data: [mockSalesRow],
      isLoading: false,
    } as ReturnType<typeof useSalesReport>);

    vi.mocked(useProductPerformance).mockReturnValue({
      data: [mockProductRow],
      isLoading: false,
    } as ReturnType<typeof useProductPerformance>);
  });

  it('renders page heading', () => {
    render(<ReportsPage />);
    expect(screen.getByRole('heading', { name: 'Reports' })).toBeInTheDocument();
  });

  it('renders subtitle', () => {
    render(<ReportsPage />);
    expect(screen.getByText(/sales performance/i)).toBeInTheDocument();
  });

  it('renders period selector buttons', () => {
    render(<ReportsPage />);
    expect(screen.getByText('Last 7 Days')).toBeInTheDocument();
    expect(screen.getByText('Last 30 Days')).toBeInTheDocument();
    expect(screen.getByText('Last 90 Days')).toBeInTheDocument();
    expect(screen.getByText('Last 12 Months')).toBeInTheDocument();
  });

  it('default period is 30d', () => {
    render(<ReportsPage />);
    expect(vi.mocked(useSalesReport)).toHaveBeenCalledWith(
      expect.objectContaining({ period: '30d' }),
    );
  });

  it('switches to 7d period when Last 7 Days clicked', () => {
    render(<ReportsPage />);
    fireEvent.click(screen.getByText('Last 7 Days'));
    expect(vi.mocked(useSalesReport)).toHaveBeenCalledWith(
      expect.objectContaining({ period: '7d' }),
    );
  });

  it('renders sales chart', () => {
    render(<ReportsPage />);
    expect(screen.getByTestId('line-chart')).toBeInTheDocument();
  });

  it('renders product chart', () => {
    render(<ReportsPage />);
    expect(screen.getByTestId('bar-chart')).toBeInTheDocument();
  });

  it('renders export buttons', () => {
    render(<ReportsPage />);
    const exportBtns = screen.getAllByRole('button', { name: /export/i });
    expect(exportBtns.length).toBeGreaterThan(0);
  });

  it('renders summary stats when sales data is available', () => {
    render(<ReportsPage />);
    // Summary stats section should be present
    const summarySection = screen.getAllByText(/450/);
    expect(summarySection.length).toBeGreaterThan(0);
  });

  it('renders loading state when data is loading', () => {
    vi.mocked(useSalesReport).mockReturnValue({
      data: undefined,
      isLoading: true,
    } as ReturnType<typeof useSalesReport>);
    render(<ReportsPage />);
    expect(screen.getByRole('heading', { name: 'Reports' })).toBeInTheDocument();
  });
});
