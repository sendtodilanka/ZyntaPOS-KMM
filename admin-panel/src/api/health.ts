import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { SystemHealth, StoreHealthSummary, StoreHealthDetail } from '@/types/health';

export const healthKeys = {
  all: ['health'] as const,
  system: () => [...healthKeys.all, 'system'] as const,
  stores: () => [...healthKeys.all, 'stores'] as const,
  store: (id: string) => [...healthKeys.all, 'store', id] as const,
};

export function useSystemHealth() {
  return useQuery({
    queryKey: healthKeys.system(),
    queryFn: () => apiClient.get('admin/health/system').json<SystemHealth>(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}

export function useAllStoreHealth() {
  return useQuery({
    queryKey: healthKeys.stores(),
    queryFn: () => apiClient.get('admin/health/stores').json<StoreHealthSummary[]>(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}

export function useStoreHealthDetail(storeId: string) {
  return useQuery({
    queryKey: healthKeys.store(storeId),
    queryFn: () => apiClient.get(`admin/health/stores/${storeId}`).json<StoreHealthDetail>(),
    refetchInterval: 60_000,
    staleTime: 30_000,
    enabled: !!storeId,
  });
}
