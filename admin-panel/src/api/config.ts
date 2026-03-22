import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { FeatureFlag, SystemConfig, ConfigUpdatePayload } from '@/types/config';

// Query keys
export const configKeys = {
  all: ['config'] as const,
  flags: () => [...configKeys.all, 'flags'] as const,
  system: () => [...configKeys.all, 'system'] as const,
};

// Feature Flags
export function useFeatureFlags() {
  return useQuery({
    queryKey: configKeys.flags(),
    queryFn: () => apiClient.get('admin/config/feature-flags').json<FeatureFlag[]>(),
    staleTime: 60_000,
  });
}

export function useUpdateFeatureFlag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      apiClient.patch(`admin/config/feature-flags/${key}`, { json: { enabled } }).json<FeatureFlag>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: configKeys.flags() });
    },
  });
}

// System Config
export function useSystemConfig() {
  return useQuery({
    queryKey: configKeys.system(),
    queryFn: () => apiClient.get('admin/config/system').json<SystemConfig[]>(),
    staleTime: 30_000,
  });
}

export function useUpdateSystemConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ConfigUpdatePayload) =>
      apiClient.patch(`admin/config/system/${payload.key}`, { json: { value: payload.value } }).json<SystemConfig>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: configKeys.system() });
    },
  });
}
