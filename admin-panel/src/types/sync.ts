export type SyncStatus = 'SYNCED' | 'PENDING' | 'SYNCING' | 'FAILED' | 'STALE';
export type SyncOperationType = 'CREATE' | 'UPDATE' | 'DELETE';

export interface StoreSyncStatus {
  storeId: string;
  storeName: string;
  status: SyncStatus;
  queueDepth: number;
  lastSyncAt: string | null;
  lastSyncDurationMs: number | null;
  errorCount: number;
  pendingOperations: number;
}

export interface SyncOperation {
  id: string;
  storeId: string;
  entityType: string;
  entityId: string;
  operationType: SyncOperationType;
  payload: Record<string, unknown>;
  clientTimestamp: string;
  retryCount: number;
  lastErrorMessage: string | null;
  createdAt: string;
}

export interface ForceSyncResult {
  storeId: string;
  operationsQueued: number;
  triggeredAt: string;
}

export interface SyncConflict {
  id: string;
  storeId: string;
  entityType: string;
  entityId: string;
  clientData: Record<string, unknown>;
  serverData: Record<string, unknown>;
  resolvedBy: 'lww' | 'server' | 'client' | 'manual';
  createdAt: number;
  resolvedAt: number | null;
}

export interface DeadLetterOperation {
  id: string;
  storeId: string;
  operationType: string;
  entityType: string;
  entityId: string;
  payload: Record<string, unknown>;
  failureReason: string;
  retryCount: number;
  createdAt: number;
  lastAttemptAt: number | null;
}
