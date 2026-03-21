import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import type {
  DiagnosticSession,
  CreateDiagnosticSessionRequest,
} from '@/types/diagnostic';

// ── Query keys ────────────────────────────────────────────────────────────────

export const diagnosticKeys = {
  all: ['diagnostic-sessions'] as const,
  active: (storeId: string) => [...diagnosticKeys.all, 'active', storeId] as const,
};

// ── Queries ───────────────────────────────────────────────────────────────────

/**
 * Fetches the active diagnostic session for a given store (if any).
 * Auto-refreshes every 15 s to reflect session expiry / consent status changes.
 */
export function useActiveDiagnosticSession(storeId: string, enabled = true) {
  return useQuery({
    queryKey: diagnosticKeys.active(storeId),
    queryFn: () =>
      apiClient
        .get(`admin/diagnostic/sessions/${storeId}`)
        .json<DiagnosticSession | null>()
        .catch(() => null),
    staleTime: 15_000,
    refetchInterval: 15_000,
    enabled: enabled && !!storeId,
  });
}

// ── Mutations ─────────────────────────────────────────────────────────────────

/**
 * Creates a new JIT diagnostic session for a store.
 * Returns the session including the one-time raw token.
 */
export function useCreateDiagnosticSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateDiagnosticSessionRequest) =>
      apiClient
        .post('admin/diagnostic/sessions', { json: body })
        .json<DiagnosticSession>(),
    onSuccess: (session) => {
      qc.invalidateQueries({ queryKey: diagnosticKeys.active(session.storeId) });
      toast.success('Session created', 'Diagnostic session token generated. Deliver to store operator.');
    },
    onError: () => toast.error('Failed to create diagnostic session'),
  });
}

/**
 * Revokes an active or pending diagnostic session.
 */
export function useRevokeDiagnosticSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      apiClient.delete(`admin/diagnostic/sessions/${sessionId}`).json(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: diagnosticKeys.all });
      toast.success('Session revoked', 'The diagnostic session has been terminated.');
    },
    onError: () => toast.error('Failed to revoke diagnostic session'),
  });
}
