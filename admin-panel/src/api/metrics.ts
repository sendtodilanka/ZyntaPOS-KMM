import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { DASHBOARD_REFRESH_INTERVAL } from '@/lib/constants';
import type {
  DashboardKPIs,
  SalesChartData,
  StoreComparisonData,
  TimePeriod,
  ChartGranularity,
  SalesReportRow,
  ProductPerformanceRow,
} from '@/types/metrics';

export function useDashboardKPIs(period: TimePeriod = 'today') {
  return useQuery({
    queryKey: ['metrics', 'dashboard', period],
    queryFn: () =>
      apiClient.get(`admin/metrics/dashboard?period=${period}`).json<DashboardKPIs>(),
    refetchInterval: DASHBOARD_REFRESH_INTERVAL,
  });
}

export function useSalesChart(params: {
  storeId?: string;
  from: string;
  to: string;
  granularity?: ChartGranularity;
}) {
  return useQuery({
    queryKey: ['metrics', 'sales', params],
    queryFn: () => {
      const qs = new URLSearchParams({
        from: params.from,
        to: params.to,
        ...(params.storeId && { storeId: params.storeId }),
        ...(params.granularity && { granularity: params.granularity }),
      });
      return apiClient.get(`admin/metrics/sales?${qs}`).json<SalesChartData[]>();
    },
  });
}

export function useStoreComparison(period: TimePeriod = 'month') {
  return useQuery({
    queryKey: ['metrics', 'stores', period],
    queryFn: () =>
      apiClient.get(`admin/metrics/stores?period=${period}`).json<StoreComparisonData[]>(),
    refetchInterval: DASHBOARD_REFRESH_INTERVAL,
  });
}

function periodToDateRange(period: string): { from: string; to: string } {
  const to = new Date().toISOString().split('T')[0];
  const daysMap: Record<string, number> = { '7d': 7, '30d': 30, '90d': 90, '12m': 365 };
  const days = daysMap[period] ?? 30;
  const from = new Date(Date.now() - days * 86400000).toISOString().split('T')[0];
  return { from, to };
}

export function useSalesReport(params: {
  storeId?: string;
  period?: string;
  from?: string;
  to?: string;
  granularity?: ChartGranularity;
}) {
  const range = params.period ? periodToDateRange(params.period) : { from: params.from ?? '', to: params.to ?? '' };
  return useQuery({
    queryKey: ['reports', 'sales', params],
    queryFn: () => {
      const qs = new URLSearchParams({
        from: range.from,
        to: range.to,
        ...(params.storeId && { storeId: params.storeId }),
        ...(params.granularity && { granularity: params.granularity }),
      });
      return apiClient.get(`admin/reports/sales?${qs}`).json<SalesReportRow[]>();
    },
  });
}

export function useProductPerformance(params: {
  storeId?: string;
  period?: string;
  from?: string;
  to?: string;
  limit?: number;
}) {
  const range = params.period ? periodToDateRange(params.period) : { from: params.from ?? '', to: params.to ?? '' };
  return useQuery({
    queryKey: ['reports', 'products', params],
    queryFn: () => {
      const qs = new URLSearchParams({
        from: range.from,
        to: range.to,
        ...(params.storeId && { storeId: params.storeId }),
        ...(params.limit && { limit: String(params.limit) }),
      });
      return apiClient.get(`admin/reports/products?${qs}`).json<ProductPerformanceRow[]>();
    },
  });
}
