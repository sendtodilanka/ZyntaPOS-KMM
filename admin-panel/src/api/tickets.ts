import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { buildQueryString } from '@/lib/utils';
import { toast } from '@/stores/ui-store';
import type {
  Ticket,
  TicketComment,
  TicketsPage,
  TicketFilter,
  CreateTicketRequest,
  UpdateTicketRequest,
  AssignTicketRequest,
  ResolveTicketRequest,
  AddCommentRequest,
  EmailThread,
  BulkAssignRequest,
  BulkResolveRequest,
  BulkOperationResult,
  TicketMetrics,
} from '@/types/ticket';

export const ticketKeys = {
  all: ['tickets'] as const,
  list: (f: TicketFilter) => [...ticketKeys.all, 'list', f] as const,
  detail: (id: string) => [...ticketKeys.all, 'detail', id] as const,
  comments: (id: string) => [...ticketKeys.all, 'comments', id] as const,
  emailThreads: (id: string) => [...ticketKeys.all, 'email-threads', id] as const,
  metrics: () => [...ticketKeys.all, 'metrics'] as const,
};

export function useTickets(filter: TicketFilter = {}) {
  const qs = buildQueryString(filter);
  return useQuery({
    queryKey: ticketKeys.list(filter),
    queryFn: () => apiClient.get(`admin/tickets${qs ? `?${qs}` : ''}`).json<TicketsPage>(),
    staleTime: 15_000,
  });
}

export function useTicket(id: string) {
  return useQuery({
    queryKey: ticketKeys.detail(id),
    queryFn: () => apiClient.get(`admin/tickets/${id}`).json<Ticket>(),
    staleTime: 10_000,
    enabled: !!id,
  });
}

export function useTicketComments(ticketId: string) {
  return useQuery({
    queryKey: ticketKeys.comments(ticketId),
    queryFn: () => apiClient.get(`admin/tickets/${ticketId}/comments`).json<TicketComment[]>(),
    staleTime: 10_000,
    enabled: !!ticketId,
  });
}

export function useCreateTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateTicketRequest) =>
      apiClient.post('admin/tickets', { json: body }).json<Ticket>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
    onError: () => toast.error('Failed to create ticket'),
  });
}

export function useUpdateTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateTicketRequest }) =>
      apiClient.patch(`admin/tickets/${id}`, { json: body }).json<Ticket>(),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ticketKeys.detail(id) });
      qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
    onError: () => toast.error('Failed to update ticket'),
  });
}

export function useAssignTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: AssignTicketRequest }) =>
      apiClient.post(`admin/tickets/${id}/assign`, { json: body }).json<Ticket>(),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ticketKeys.detail(id) });
      qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
    onError: () => toast.error('Failed to assign ticket'),
  });
}

export function useResolveTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ResolveTicketRequest }) =>
      apiClient.post(`admin/tickets/${id}/resolve`, { json: body }).json<Ticket>(),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: ticketKeys.detail(id) });
      qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
    onError: () => toast.error('Failed to resolve ticket'),
  });
}

export function useCloseTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post(`admin/tickets/${id}/close`).json<Ticket>(),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: ticketKeys.detail(id) });
      qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
    onError: () => toast.error('Failed to close ticket'),
  });
}

export function useAddComment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ ticketId, body }: { ticketId: string; body: AddCommentRequest }) =>
      apiClient.post(`admin/tickets/${ticketId}/comments`, { json: body }).json<TicketComment>(),
    onSuccess: (_data, { ticketId }) => {
      qc.invalidateQueries({ queryKey: ticketKeys.comments(ticketId) });
      qc.invalidateQueries({ queryKey: ticketKeys.detail(ticketId) });
    },
    onError: () => toast.error('Failed to add comment'),
  });
}

// ── Email threads ─────────────────────────────────────────────────────────

export function useEmailThreads(ticketId: string) {
  return useQuery({
    queryKey: ticketKeys.emailThreads(ticketId),
    queryFn: () => apiClient.get(`admin/tickets/${ticketId}/email-threads`).json<EmailThread[]>(),
    staleTime: 15_000,
    enabled: !!ticketId,
  });
}

// ── Ticket metrics ────────────────────────────────────────────────────────

export function useTicketMetrics() {
  return useQuery({
    queryKey: ticketKeys.metrics(),
    queryFn: () => apiClient.get('admin/tickets/metrics').json<TicketMetrics>(),
    staleTime: 30_000,
  });
}

// ── Bulk operations ───────────────────────────────────────────────────────

export function useBulkAssignTickets() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: BulkAssignRequest) =>
      apiClient.post('admin/tickets/bulk-assign', { json: body }).json<BulkOperationResult>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
    onError: () => toast.error('Failed to bulk-assign tickets'),
  });
}

// GET /admin/tickets/export — streams CSV directly (not JSON). Downloads the
// response body as a .csv file via an anchor click. (A-003)
export async function exportTickets(filter: TicketFilter = {}): Promise<void> {
  const qs = new URLSearchParams();
  if (filter.status) qs.set('status', filter.status);
  if (filter.priority) qs.set('priority', filter.priority);
  if (filter.category) qs.set('category', filter.category);
  if (filter.assignedTo) qs.set('assignedTo', filter.assignedTo);
  if (filter.storeId) qs.set('storeId', filter.storeId);
  if (filter.search) qs.set('search', filter.search);
  // Backend responds text/csv; skip the .json() parse and read raw body.
  const csv = await apiClient
    .get(`admin/tickets/export${qs.toString() ? `?${qs}` : ''}`)
    .text();

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `tickets-${new Date().toISOString().split('T')[0]}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export function useBulkResolveTickets() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: BulkResolveRequest) =>
      apiClient.post('admin/tickets/bulk-resolve', { json: body }).json<BulkOperationResult>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
    onError: () => toast.error('Failed to bulk-resolve tickets'),
  });
}
