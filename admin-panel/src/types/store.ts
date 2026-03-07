export type StoreStatus = 'HEALTHY' | 'WARNING' | 'CRITICAL' | 'OFFLINE';

export interface Store {
  id: string;
  name: string;
  location: string;
  licenseKey: string;
  edition: string;
  status: StoreStatus;
  activeUsers: number;
  lastSyncAt: string | null;
  lastHeartbeatAt: string | null;
  appVersion: string;
  createdAt: string;
}

export interface StoreHealth {
  storeId: string;
  status: StoreStatus;
  healthScore: number;
  dbSizeBytes: number;
  syncQueueDepth: number;
  errorCount24h: number;
  uptimeHours: number;
  lastHeartbeatAt: string | null;
  responseTimeMs: number;
  appVersion: string;
  osInfo: string;
}

export interface StoreConfig {
  storeId: string;
  taxRates: TaxRate[];
  featureFlags: Record<string, boolean>;
  timezone: string;
  currency: string;
  receiptFooter: string;
  syncIntervalSeconds: number;
  updatedAt: string;
}

export interface TaxRate {
  id: string;
  name: string;
  rate: number;
  isDefault: boolean;
  storeIds: string[] | null;
}

export interface StoreFilter {
  status?: StoreStatus;
  search?: string;
  page?: number;
  size?: number;
}
