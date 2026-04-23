import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { buildQueryString } from '@/lib/utils';
import { toast } from '@/stores/ui-store';
import type { Alert, AlertFilter, AlertsPage, AlertRule } from '@/types/alert';

export const alertKeys = {
  all: ['alerts'] as const,
  list: (f: AlertFilter) => [...alertKeys.all, 'list', f] as const,
  rules: () => [...alertKeys.all, 'rules'] as const,
  counts: () => [...alertKeys.all, 'counts'] as const,
};

export function useAlerts(filter: AlertFilter = {}) {
  const qs = buildQueryString(filter);
  return useQuery({
    queryKey: alertKeys.list(filter),
    queryFn: () => apiClient.get(`admin/alerts${qs ? `?${qs}` : ''}`).json<AlertsPage>(),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useAlertCounts() {
  return useQuery({
    queryKey: alertKeys.counts(),
    queryFn: () => apiClient.get('admin/alerts/counts').json<Record<string, number>>(),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useAlertRules() {
  return useQuery({
    queryKey: alertKeys.rules(),
    queryFn: () => apiClient.get('admin/alerts/rules').json<AlertRule[]>(),
    staleTime: 60_000,
  });
}

export function useAcknowledgeAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.post(`admin/alerts/${id}/acknowledge`).json<Alert>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: alertKeys.all });
    },
    onError: () => toast.error('Failed to acknowledge alert'),
  });
}

export function useResolveAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.post(`admin/alerts/${id}/resolve`).json<Alert>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: alertKeys.all });
    },
    onError: () => toast.error('Failed to resolve alert'),
  });
}

export function useSilenceAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, durationMinutes }: { id: string; durationMinutes: number }) =>
      apiClient.post(`admin/alerts/${id}/silence`, { json: { durationMinutes } }).json<Alert>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: alertKeys.all });
    },
    onError: () => toast.error('Failed to silence alert'),
  });
}

export function useToggleAlertRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      apiClient.patch(`admin/alerts/rules/${id}`, { json: { enabled } }).json<AlertRule>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: alertKeys.rules() });
    },
    onError: () => toast.error('Failed to update alert rule'),
  });
}
