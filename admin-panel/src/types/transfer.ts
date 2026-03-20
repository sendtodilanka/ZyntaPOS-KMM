// ── Transfer status ───────────────────────────────────────────────────────────

export type TransferStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'IN_TRANSIT'
  | 'RECEIVED'
  | 'COMMITTED'
  | 'CANCELLED';

// ── DTOs (mirror AdminTransferService.kt) ─────────────────────────────────────

export interface StockTransfer {
  id: string;
  sourceWarehouseId: string;
  destWarehouseId: string;
  sourceStoreId: string | null;
  destStoreId: string | null;
  productId: string;
  quantity: number;
  status: TransferStatus;
  notes: string | null;
  createdBy: string | null;
  approvedBy: string | null;
  approvedAt: number | null;   // epoch-ms
  dispatchedBy: string | null;
  dispatchedAt: number | null; // epoch-ms
  receivedBy: string | null;
  receivedAt: number | null;   // epoch-ms
  createdAt: number;           // epoch-ms
  updatedAt: number;           // epoch-ms
}

export interface TransferListResponse {
  transfers: StockTransfer[];
  total: number;
}

export interface CreateTransferRequest {
  sourceWarehouseId: string;
  destWarehouseId: string;
  sourceStoreId?: string;
  destStoreId?: string;
  productId: string;
  quantity: number;
  notes?: string;
}

export interface ApproveTransferRequest {
  approvedBy: string;
}

export interface DispatchTransferRequest {
  dispatchedBy: string;
}

export interface ReceiveTransferRequest {
  receivedBy: string;
}

// ── Filter ────────────────────────────────────────────────────────────────────

export interface TransferFilter {
  storeId?: string;
  status?: TransferStatus;
  page?: number;
  size?: number;
}
