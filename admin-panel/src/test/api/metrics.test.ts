import { describe, it, expect } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import {
  useDashboardKPIs,
  useSalesChart,
  useStoreComparison,
  useSalesReport,
  useProductPerformance,
} from '@/api/metrics';
import type { DashboardKPIs, SalesChartData, StoreComparisonData, SalesReportRow, ProductPerformanceRow } from '@/types/metrics';

const API_BASE = 'https://api.zyntapos.com';

const mockDashboardKPIs: DashboardKPIs = {
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

const mockSalesChartData: SalesChartData[] = [
  { period: '2024-06-01', revenue: 150000, orders: 42, averageOrderValue: 3571 },
  { period: '2024-06-02', revenue: 180000, orders: 55, averageOrderValue: 3273 },
];

const mockStoreComparison: StoreComparisonData[] = [
  { storeId: 'store-1', storeName: 'Colombo', revenue: 250000, orders: 80, growth: 5.2 },
  { storeId: 'store-2', storeName: 'Galle', revenue: 180000, orders: 60, growth: 2.1 },
];

const mockSalesRows: SalesReportRow[] = [
  {
    date: '2024-06-01',
    revenue: 150000,
    orders: 42,
    averageOrderValue: 3571,
    refunds: 0,
    netRevenue: 150000,
  },
];

const mockProductRows: ProductPerformanceRow[] = [
  {
    productId: 'prod-1',
    productName: 'Rice',
    revenue: 50000,
    unitsSold: 200,
    category: 'Grocery',
    marginPercent: 22.5,
  },
];

// ── useDashboardKPIs ─────────────────────────────────────────────────────────

describe('useDashboardKPIs', () => {
  it('fetches dashboard KPIs with default period "today"', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/metrics/dashboard`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockDashboardKPIs);
      }),
    );

    const { result } = renderHook(() => useDashboardKPIs(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('period=today');
    expect(result.current.data).toEqual(mockDashboardKPIs);
  });

  it('forwards a custom period param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/metrics/dashboard`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockDashboardKPIs);
      }),
    );

    const { result } = renderHook(() => useDashboardKPIs('7d' as Parameters<typeof useDashboardKPIs>[0]), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('period=7d');
  });

  it('returns all expected KPI fields', async () => {
    server.use(
      http.get(`${API_BASE}/admin/metrics/dashboard`, () =>
        HttpResponse.json(mockDashboardKPIs),
      ),
    );

    const { result } = renderHook(() => useDashboardKPIs(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.revenueToday).toBe(4500000);
    expect(result.current.data?.totalStores).toBe(8);
    expect(result.current.data?.syncHealthPercent).toBe(98.2);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/metrics/dashboard`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useDashboardKPIs(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSalesChart ────────────────────────────────────────────────────────────

describe('useSalesChart', () => {
  it('fetches sales chart data with required from and to params', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/metrics/sales`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockSalesChartData);
      }),
    );

    const { result } = renderHook(
      () => useSalesChart({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('from=2024-06-01');
    expect(capturedUrl).toContain('to=2024-06-30');
    expect(result.current.data).toHaveLength(2);
  });

  it('forwards optional storeId param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/metrics/sales`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockSalesChartData);
      }),
    );

    const { result } = renderHook(
      () => useSalesChart({ from: '2024-06-01', to: '2024-06-30', storeId: 'store-1' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('storeId=store-1');
  });

  it('forwards optional granularity param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/metrics/sales`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockSalesChartData);
      }),
    );

    const { result } = renderHook(
      () => useSalesChart({ from: '2024-06-01', to: '2024-06-30', granularity: 'hour' as Parameters<typeof useSalesChart>[0]['granularity'] }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('granularity=hour');
  });

  it('forwards all params simultaneously', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/metrics/sales`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockSalesChartData);
      }),
    );

    const { result } = renderHook(
      () => useSalesChart({
        from: '2024-01-01',
        to: '2024-12-31',
        storeId: 'store-2',
        granularity: 'day' as Parameters<typeof useSalesChart>[0]['granularity'],
      }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('from=2024-01-01');
    expect(capturedUrl).toContain('to=2024-12-31');
    expect(capturedUrl).toContain('storeId=store-2');
    expect(capturedUrl).toContain('granularity=day');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/metrics/sales`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(
      () => useSalesChart({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useStoreComparison ───────────────────────────────────────────────────────

describe('useStoreComparison', () => {
  it('fetches store comparison data with default period "month"', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/metrics/stores`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockStoreComparison);
      }),
    );

    const { result } = renderHook(() => useStoreComparison(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('period=month');
    expect(result.current.data).toHaveLength(2);
  });

  it('forwards a custom period param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/metrics/stores`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockStoreComparison);
      }),
    );

    const { result } = renderHook(
      () => useStoreComparison('7d' as Parameters<typeof useStoreComparison>[0]),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('period=7d');
  });

  it('returns an array of store comparison entries', async () => {
    server.use(
      http.get(`${API_BASE}/admin/metrics/stores`, () =>
        HttpResponse.json(mockStoreComparison),
      ),
    );

    const { result } = renderHook(() => useStoreComparison(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data![0]).toHaveProperty('storeId');
    expect(result.current.data![0]).toHaveProperty('revenue');
  });

  it('returns an empty array when no stores are configured', async () => {
    server.use(
      http.get(`${API_BASE}/admin/metrics/stores`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useStoreComparison(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/metrics/stores`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useStoreComparison(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSalesReport ───────────────────────────────────────────────────────────

describe('useSalesReport', () => {
  it('returns data from { data: [...] } wrapped response format', async () => {
    server.use(
      http.get(`${API_BASE}/admin/reports/sales`, () =>
        HttpResponse.json({ data: mockSalesRows }),
      ),
    );

    const { result } = renderHook(
      () => useSalesReport({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0]).toHaveProperty('date', '2024-06-01');
  });

  it('returns data directly when response is a plain array', async () => {
    server.use(
      http.get(`${API_BASE}/admin/reports/sales`, () =>
        HttpResponse.json(mockSalesRows),
      ),
    );

    const { result } = renderHook(
      () => useSalesReport({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0]).toHaveProperty('date', '2024-06-01');
  });

  it('returns an empty array when { data: [] } is returned', async () => {
    server.use(
      http.get(`${API_BASE}/admin/reports/sales`, () =>
        HttpResponse.json({ data: [] }),
      ),
    );

    const { result } = renderHook(
      () => useSalesReport({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('forwards from and to params as query string', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/reports/sales`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ data: mockSalesRows });
      }),
    );

    const { result } = renderHook(
      () => useSalesReport({ from: '2024-01-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('from=2024-01-01');
    expect(capturedUrl).toContain('to=2024-06-30');
  });

  it('forwards storeId param when provided', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/reports/sales`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ data: mockSalesRows });
      }),
    );

    const { result } = renderHook(
      () => useSalesReport({ from: '2024-01-01', to: '2024-06-30', storeId: 'store-1' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('storeId=store-1');
  });

  it('derives from/to date range from period param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/reports/sales`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ data: mockSalesRows });
      }),
    );

    const { result } = renderHook(
      () => useSalesReport({ period: '30d' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    // period-derived range: both from and to should be present
    expect(capturedUrl).toContain('from=');
    expect(capturedUrl).toContain('to=');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/reports/sales`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(
      () => useSalesReport({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useProductPerformance ────────────────────────────────────────────────────

describe('useProductPerformance', () => {
  it('returns data from { data: [...] } wrapped response format', async () => {
    server.use(
      http.get(`${API_BASE}/admin/reports/products`, () =>
        HttpResponse.json({ data: mockProductRows }),
      ),
    );

    const { result } = renderHook(
      () => useProductPerformance({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0]).toHaveProperty('productId', 'prod-1');
  });

  it('returns data directly when response is a plain array', async () => {
    server.use(
      http.get(`${API_BASE}/admin/reports/products`, () =>
        HttpResponse.json(mockProductRows),
      ),
    );

    const { result } = renderHook(
      () => useProductPerformance({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0]).toHaveProperty('productName', 'Rice');
  });

  it('returns an empty array when { data: [] } is returned', async () => {
    server.use(
      http.get(`${API_BASE}/admin/reports/products`, () =>
        HttpResponse.json({ data: [] }),
      ),
    );

    const { result } = renderHook(
      () => useProductPerformance({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('forwards from and to params as query string', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/reports/products`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ data: mockProductRows });
      }),
    );

    const { result } = renderHook(
      () => useProductPerformance({ from: '2024-01-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('from=2024-01-01');
    expect(capturedUrl).toContain('to=2024-06-30');
  });

  it('forwards storeId when provided', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/reports/products`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ data: mockProductRows });
      }),
    );

    const { result } = renderHook(
      () => useProductPerformance({ from: '2024-01-01', to: '2024-06-30', storeId: 'store-2' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('storeId=store-2');
  });

  it('forwards limit param when provided', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/reports/products`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ data: mockProductRows });
      }),
    );

    const { result } = renderHook(
      () => useProductPerformance({ from: '2024-01-01', to: '2024-06-30', limit: 10 }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('limit=10');
  });

  it('derives from/to date range from period param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/reports/products`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ data: mockProductRows });
      }),
    );

    const { result } = renderHook(
      () => useProductPerformance({ period: '7d' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('from=');
    expect(capturedUrl).toContain('to=');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/reports/products`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(
      () => useProductPerformance({ from: '2024-06-01', to: '2024-06-30' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
