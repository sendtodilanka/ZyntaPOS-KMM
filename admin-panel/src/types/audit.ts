export type AuditEventCategory =
  | 'AUTH'
  | 'LICENSE'
  | 'INVENTORY'
  | 'ORDER'
  | 'PAYMENT'
  | 'USER'
  | 'SETTINGS'
  | 'SYNC'
  | 'SYSTEM';

export interface AuditEntry {
  id: string;
  eventType: string;
  category: AuditEventCategory;
  userId: string | null;
  userName: string | null;
  storeId: string | null;
  storeName: string | null;
  entityType: string | null;
  entityId: string | null;
  previousValues: Record<string, unknown> | null;
  newValues: Record<string, unknown> | null;
  ipAddress: string | null;
  userAgent: string | null;
  success: boolean;
  errorMessage: string | null;
  hashChain: string;
  createdAt: string;
}

export interface AuditFilter {
  category?: AuditEventCategory;
  eventType?: string;
  userId?: string;
  storeId?: string;
  entityType?: string;
  success?: boolean;
  from?: string;
  to?: string;
  search?: string;
  page?: number;
  size?: number;
}
