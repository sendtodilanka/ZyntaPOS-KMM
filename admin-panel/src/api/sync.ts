import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import { SYNC_REFRESH_INTERVAL } from '@/lib/constants';
import type { StoreSyncStatus, SyncOperation, ForceSyncResult, SyncConflict, DeadLetterOperation } from '@/types/sync';

export function useSyncStatus() {
  return useQuery({
    queryKey: ['sync', 'status'],
    queryFn: () => apiClient.get('admin/sync/status').json<StoreSyncStatus[]>(),
    refetchInterval: SYNC_REFRESH_INTERVAL,
  });
}

export function useStoreSync(storeId: string) {
  return useQuery({
    queryKey: ['sync', storeId],
    queryFn: () => apiClient.get(`admin/sync/${storeId}`).json<StoreSyncStatus>(),
    enabled: !!storeId,
    refetchInterval: SYNC_REFRESH_INTERVAL,
  });
}

export function useSyncQueue(storeId: string) {
  return useQuery({
    queryKey: ['sync', storeId, 'queue'],
    queryFn: () => apiClient.get(`admin/sync/${storeId}/queue`).json<SyncOperation[]>(),
    enabled: !!storeId,
    refetchInterval: SYNC_REFRESH_INTERVAL,
  });
}

export function useConflictLog(storeId?: string) {
  const url = storeId ? `admin/sync/${storeId}/conflicts` : 'admin/sync/conflicts';
  return useQuery({
    queryKey: ['sync', 'conflicts', storeId],
    queryFn: () => apiClient.get(url).json<SyncConflict[]>(),
    staleTime: 30_000,
  });
}

export function useDeadLetters(storeId?: string) {
  const url = storeId ? `admin/sync/${storeId}/dead-letters` : 'admin/sync/dead-letters';
  return useQuery({
    queryKey: ['sync', 'dead-letters', storeId],
    queryFn: () => apiClient.get(url).json<DeadLetterOperation[]>(),
    staleTime: 30_000,
  });
}

export function useRetryDeadLetter() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.post(`admin/sync/dead-letters/${id}/retry`).json<void>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sync', 'dead-letters'] });
      toast.success('Retry queued', 'The operation has been queued for retry.');
    },
    onError: () => toast.error('Failed to retry operation'),
  });
}

export function useDiscardDeadLetter() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`admin/sync/dead-letters/${id}`).json<void>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sync', 'dead-letters'] });
      toast.success('Operation discarded');
    },
    onError: () => toast.error('Failed to discard operation'),
  });
}

export function useForceSync() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (storeId: string) =>
      apiClient.post(`admin/sync/${storeId}/force`).json<ForceSyncResult>(),
    onSuccess: (result) => {
      qc.invalidateQueries({ queryKey: ['sync'] });
      toast.success('Sync triggered', `${result.operationsQueued} operations queued for re-sync.`);
    },
    onError: () => toast.error('Failed to trigger sync'),
  });
}
