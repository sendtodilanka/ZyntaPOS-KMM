import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import type {
  ReplenishmentRulesResponse,
  ReplenishmentSuggestionsResponse,
  UpsertReplenishmentRuleRequest,
} from '@/types/replenishment';

// ── Queries ───────────────────────────────────────────────────────────────────

export function useReplenishmentRules(warehouseId?: string) {
  const qs = new URLSearchParams();
  if (warehouseId) qs.set('warehouseId', warehouseId);

  return useQuery({
    queryKey: ['replenishment-rules', warehouseId],
    queryFn: () =>
      apiClient
        .get(`admin/replenishment/rules${qs.toString() ? `?${qs}` : ''}`)
        .json<ReplenishmentRulesResponse>(),
    staleTime: 30_000,
  });
}

export function useReplenishmentSuggestions(warehouseId?: string) {
  const qs = new URLSearchParams();
  if (warehouseId) qs.set('warehouseId', warehouseId);

  return useQuery({
    queryKey: ['replenishment-suggestions', warehouseId],
    queryFn: () =>
      apiClient
        .get(`admin/replenishment/suggestions${qs.toString() ? `?${qs}` : ''}`)
        .json<ReplenishmentSuggestionsResponse>(),
    staleTime: 30_000,
  });
}

// ── Mutations ─────────────────────────────────────────────────────────────────

export function useUpsertReplenishmentRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpsertReplenishmentRuleRequest) =>
      apiClient.post('admin/replenishment/rules', { json: body }).json(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['replenishment-rules'] });
      qc.invalidateQueries({ queryKey: ['replenishment-suggestions'] });
      toast.success('Rule saved', 'Replenishment rule updated successfully.');
    },
    onError: () => toast.error('Failed to save replenishment rule'),
  });
}

export function useDeleteReplenishmentRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.delete(`admin/replenishment/rules/${id}`).json(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['replenishment-rules'] });
      qc.invalidateQueries({ queryKey: ['replenishment-suggestions'] });
      toast.success('Rule deleted');
    },
    onError: () => toast.error('Failed to delete replenishment rule'),
  });
}
