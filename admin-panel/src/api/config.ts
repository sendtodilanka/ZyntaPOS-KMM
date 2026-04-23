import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import type {
  FeatureFlag,
  SystemConfig,
  ConfigUpdatePayload,
  TaxRate,
  TaxRateCreateRequest,
  TaxRateUpdateRequest,
} from '@/types/config';

// Query keys
export const configKeys = {
  all: ['config'] as const,
  flags: () => [...configKeys.all, 'flags'] as const,
  system: () => [...configKeys.all, 'system'] as const,
  taxRates: () => [...configKeys.all, 'tax-rates'] as const,
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
    onError: () => toast.error('Failed to update feature flag'),
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
    onError: () => toast.error('Failed to update system config'),
  });
}

// Tax Rates (A-002)
export function useTaxRates() {
  return useQuery({
    queryKey: configKeys.taxRates(),
    queryFn: () => apiClient.get('admin/config/tax-rates').json<TaxRate[]>(),
    staleTime: 60_000,
  });
}

export function useCreateTaxRate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: TaxRateCreateRequest) =>
      apiClient.post('admin/config/tax-rates', { json: data }).json<TaxRate>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: configKeys.taxRates() });
    },
    onError: () => toast.error('Failed to create tax rate'),
  });
}

export function useUpdateTaxRate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: TaxRateUpdateRequest }) =>
      apiClient.put(`admin/config/tax-rates/${id}`, { json: data }).json<TaxRate>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: configKeys.taxRates() });
    },
    onError: () => toast.error('Failed to update tax rate'),
  });
}

export function useDeleteTaxRate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.delete(`admin/config/tax-rates/${id}`).json(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: configKeys.taxRates() });
    },
    onError: () => toast.error('Failed to delete tax rate'),
  });
}
