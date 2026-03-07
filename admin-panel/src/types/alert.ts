export type AlertSeverity = 'critical' | 'high' | 'medium' | 'low' | 'info';
export type AlertStatus = 'active' | 'acknowledged' | 'resolved' | 'silenced';
export type AlertCategory = 'sync' | 'license' | 'payment' | 'security' | 'system' | 'store';

export interface Alert {
  id: string;
  title: string;
  message: string;
  severity: AlertSeverity;
  status: AlertStatus;
  category: AlertCategory;
  storeId?: string;
  storeName?: string;
  createdAt: string;
  updatedAt: string;
  acknowledgedBy?: string;
  resolvedAt?: string;
  metadata?: Record<string, unknown>;
}

export interface AlertFilter {
  severity?: AlertSeverity;
  status?: AlertStatus;
  category?: AlertCategory;
  storeId?: string;
  search?: string;
  page?: number;
  pageSize?: number;
}

export interface AlertsPage {
  items: Alert[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AlertRule {
  id: string;
  name: string;
  description: string;
  category: AlertCategory;
  severity: AlertSeverity;
  enabled: boolean;
  conditions: Record<string, unknown>;
  notifyChannels: string[];
}
