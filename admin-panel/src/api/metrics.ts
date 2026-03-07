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

export function useSalesReport(params: {
  storeId?: string;
  from: string;
  to: string;
  granularity?: ChartGranularity;
}) {
  return useQuery({
    queryKey: ['reports', 'sales', params],
    queryFn: () => {
      const qs = new URLSearchParams({
        from: params.from,
        to: params.to,
        ...(params.storeId && { storeId: params.storeId }),
        ...(params.granularity && { granularity: params.granularity }),
      });
      return apiClient.get(`admin/reports/sales?${qs}`).json<SalesReportRow[]>();
    },
  });
}

export function useProductPerformance(params: { storeId?: string; from: string; to: string }) {
  return useQuery({
    queryKey: ['reports', 'products', params],
    queryFn: () => {
      const qs = new URLSearchParams({
        from: params.from,
        to: params.to,
        ...(params.storeId && { storeId: params.storeId }),
      });
      return apiClient.get(`admin/reports/products?${qs}`).json<ProductPerformanceRow[]>();
    },
  });
}
