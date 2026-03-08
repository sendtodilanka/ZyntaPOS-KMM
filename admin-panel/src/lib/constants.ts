export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'https://api.zyntapos.com';
export const LICENSE_BASE_URL = import.meta.env.VITE_LICENSE_URL ?? 'https://license.zyntapos.com';
export const SYNC_BASE_URL = import.meta.env.VITE_SYNC_URL ?? 'https://sync.zyntapos.com';

export const DEFAULT_PAGE_SIZE = 20;
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export const QUERY_STALE_TIME = 30_000; // 30 seconds
export const QUERY_RETRY_COUNT = 2;

export const DASHBOARD_REFRESH_INTERVAL = 30_000; // 30 seconds
export const HEALTH_REFRESH_INTERVAL = 15_000; // 15 seconds
export const SYNC_REFRESH_INTERVAL = 10_000; // 10 seconds

export const ROUTES = {
  LOGIN: '/login',
  DASHBOARD: '/',
  LICENSES: '/licenses',
  LICENSE_DETAIL: '/licenses/$licenseKey',
  STORES: '/stores',
  STORE_DETAIL: '/stores/$storeId',
  USERS: '/users',
  AUDIT: '/audit',
  SYNC: '/sync',
  CONFIG: '/config',
  REPORTS: '/reports',
  HEALTH: '/health',
  ALERTS: '/alerts',
} as const;
