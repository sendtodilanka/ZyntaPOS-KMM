import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import { HEALTH_REFRESH_INTERVAL } from '@/lib/constants';
import type { Store, StoreHealth, StoreConfig, StoreFilter } from '@/types/store';
import type { PagedResponse } from '@/types/api';

export function useStores(filters: StoreFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.page !== undefined) qs.set('page', String(filters.page));
  if (filters.size !== undefined) qs.set('size', String(filters.size ?? 20));
  if (filters.status) qs.set('status', filters.status);
  if (filters.search) qs.set('search', filters.search);
  return useQuery({
    queryKey: ['stores', filters],
    queryFn: () => apiClient.get(`admin/stores?${qs}`).json<PagedResponse<Store>>(),
  });
}

export function useStore(storeId: string) {
  return useQuery({
    queryKey: ['stores', storeId],
    queryFn: () => apiClient.get(`admin/stores/${storeId}`).json<Store>(),
    enabled: !!storeId,
  });
}

export function useStoreHealth(storeId: string) {
  return useQuery({
    queryKey: ['stores', storeId, 'health'],
    queryFn: () => apiClient.get(`admin/stores/${storeId}/health`).json<StoreHealth>(),
    enabled: !!storeId,
    refetchInterval: HEALTH_REFRESH_INTERVAL,
  });
}

export function useAllStoreHealth() {
  return useQuery({
    queryKey: ['stores', 'health'],
    queryFn: () => apiClient.get('admin/health/stores').json<StoreHealth[]>(),
    refetchInterval: HEALTH_REFRESH_INTERVAL,
  });
}

export function useUpdateStoreConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ storeId, config }: { storeId: string; config: Partial<StoreConfig> }) =>
      apiClient.put(`admin/stores/${storeId}/config`, { json: config }).json<StoreConfig>(),
    onSuccess: (_, { storeId }) => {
      qc.invalidateQueries({ queryKey: ['stores', storeId] });
      toast.success('Store config updated', 'Configuration has been saved.');
    },
    onError: () => toast.error('Failed to update config'),
  });
}
