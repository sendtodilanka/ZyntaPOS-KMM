export type TimePeriod = 'today' | 'week' | 'month' | 'quarter' | 'year';
export type ChartGranularity = 'hour' | 'day' | 'week' | 'month';

export interface DashboardKPIs {
  totalStores: number;
  totalStoresTrend: number;
  activeLicenses: number;
  activeLicensesTrend: number;
  revenueToday: number;
  revenueTodayTrend: number;
  syncHealthPercent: number;
  syncHealthTrend: number;
  currency: string;
}

export interface TimeSeriesPoint {
  timestamp: string;
  value: number;
  label?: string;
}

export interface SalesChartData {
  period: string;
  revenue: number;
  orders: number;
  averageOrderValue: number;
}

export interface StoreComparisonData {
  storeId: string;
  storeName: string;
  revenue: number;
  orders: number;
  growth: number;
}

export interface SalesReportRow {
  date: string;
  revenue: number;
  orders: number;
  averageOrderValue: number;
  refunds: number;
  netRevenue: number;
  storeId?: string;
  storeName?: string;
}

export interface ProductPerformanceRow {
  productId: string;
  productName: string;
  name?: string;
  category: string;
  unitsSold: number;
  revenue: number;
  marginPercent: number;
  returns?: number;
  storeId?: string;
  storeName?: string;
}
