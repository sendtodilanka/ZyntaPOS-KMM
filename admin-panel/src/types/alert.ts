export type AlertMetric =
  | 'sync_queue_depth'
  | 'error_rate'
  | 'heartbeat_age_minutes'
  | 'db_size_mb'
  | 'response_time_ms'
  | 'active_licenses'
  | 'failed_payments';

export type AlertOperator = '>' | '<' | '>=' | '<=' | '==' | '!=';
export type AlertStatus = 'OK' | 'FIRING' | 'RESOLVED';
export type NotificationChannelType = 'slack' | 'email' | 'webhook';

export interface AlertRule {
  id: string;
  name: string;
  metric: AlertMetric;
  operator: AlertOperator;
  threshold: number;
  channelIds: string[];
  enabled: boolean;
  cooldownMinutes: number;
  storeIds: string[] | null;
  lastFiredAt: string | null;
  createdAt: string;
}

export interface AlertHistoryEntry {
  id: string;
  ruleId: string;
  ruleName: string;
  metric: AlertMetric;
  metricValue: number;
  threshold: number;
  storeId: string | null;
  storeName: string | null;
  status: AlertStatus;
  firedAt: string;
  resolvedAt: string | null;
  acknowledged: boolean;
}

export interface NotificationChannel {
  id: string;
  name: string;
  type: NotificationChannelType;
  config: Record<string, string>;
  enabled: boolean;
  createdAt: string;
}

export interface CreateAlertRuleRequest {
  name: string;
  metric: AlertMetric;
  operator: AlertOperator;
  threshold: number;
  channelIds: string[];
  cooldownMinutes: number;
  storeIds?: string[];
}

export interface CreateChannelRequest {
  name: string;
  type: NotificationChannelType;
  config: Record<string, string>;
}
