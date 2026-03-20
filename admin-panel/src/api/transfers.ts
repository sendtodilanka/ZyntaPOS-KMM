import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import type {
  StockTransfer,
  TransferListResponse,
  TransferFilter,
  ApproveTransferRequest,
  DispatchTransferRequest,
  ReceiveTransferRequest,
} from '@/types/transfer';

// ── Queries ───────────────────────────────────────────────────────────────────

export function useTransfers(filters: TransferFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.page  !== undefined) qs.set('page',    String(filters.page));
  if (filters.size  !== undefined) qs.set('size',    String(filters.size ?? 20));
  if (filters.storeId)             qs.set('storeId', filters.storeId);
  if (filters.status)              qs.set('status',  filters.status);

  return useQuery({
    queryKey: ['transfers', filters],
    queryFn:  () => apiClient.get(`admin/transfers?${qs}`).json<TransferListResponse>(),
    staleTime: 30_000,
  });
}

export function useTransfer(id: string) {
  return useQuery({
    queryKey: ['transfers', id],
    queryFn:  () => apiClient.get(`admin/transfers/${id}`).json<StockTransfer>(),
    enabled:  !!id,
  });
}

// ── Mutations ─────────────────────────────────────────────────────────────────

export function useApproveTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ApproveTransferRequest }) =>
      apiClient.post(`admin/transfers/${id}/approve`, { json: body }).json<StockTransfer>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transfers'] });
      toast.success('Transfer approved', 'Status updated to APPROVED.');
    },
    onError: () => toast.error('Failed to approve transfer'),
  });
}

export function useDispatchTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: DispatchTransferRequest }) =>
      apiClient.post(`admin/transfers/${id}/dispatch`, { json: body }).json<StockTransfer>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transfers'] });
      toast.success('Transfer dispatched', 'Status updated to IN_TRANSIT.');
    },
    onError: () => toast.error('Failed to dispatch transfer'),
  });
}

export function useReceiveTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ReceiveTransferRequest }) =>
      apiClient.post(`admin/transfers/${id}/receive`, { json: body }).json<StockTransfer>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transfers'] });
      toast.success('Transfer received', 'Status updated to RECEIVED.');
    },
    onError: () => toast.error('Failed to mark transfer received'),
  });
}

export function useCancelTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, cancelledBy }: { id: string; cancelledBy: string }) =>
      apiClient.post(`admin/transfers/${id}/cancel`, { json: { cancelledBy } }).json<StockTransfer>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transfers'] });
      toast.success('Transfer cancelled');
    },
    onError: () => toast.error('Failed to cancel transfer'),
  });
}
