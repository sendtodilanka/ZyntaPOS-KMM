export type ServiceStatus = 'healthy' | 'degraded' | 'unhealthy' | 'unknown';

export interface ServiceHealth {
  name: string;
  status: ServiceStatus;
  latencyMs: number;
  uptime: number; // percentage 0-100
  lastChecked: string; // ISO
  version?: string;
  details?: Record<string, unknown>;
}

export interface SystemHealth {
  overall: ServiceStatus;
  services: ServiceHealth[];
  checkedAt: string;
}

export interface StoreHealthSummary {
  storeId: string;
  storeName: string;
  status: ServiceStatus;
  lastSync: string;
  pendingOperations: number;
  appVersion: string;
  androidVersion: string;
  uptimePercent: number;
}

export interface HealthTimeSeries {
  timestamp: string;
  latencyMs: number;
  status: ServiceStatus;
}

export interface StoreHealthDetail {
  storeId: string;
  storeName: string;
  status: ServiceStatus;
  lastSync: string;
  pendingOperations: number;
  appVersion: string;
  androidVersion: string;
  uptimePercent: number;
  recentActivity: HealthTimeSeries[];
  errorLog: { timestamp: string; message: string; severity: 'error' | 'warn' | 'info' }[];
}
