import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import { SYNC_REFRESH_INTERVAL } from '@/lib/constants';
import type { StoreSyncStatus, SyncOperation, ForceSyncResult } from '@/types/sync';

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
