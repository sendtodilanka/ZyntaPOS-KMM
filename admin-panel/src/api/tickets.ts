import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { buildQueryString } from '@/lib/utils';
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
} from '@/types/ticket';

export const ticketKeys = {
  all: ['tickets'] as const,
  list: (f: TicketFilter) => [...ticketKeys.all, 'list', f] as const,
  detail: (id: string) => [...ticketKeys.all, 'detail', id] as const,
  comments: (id: string) => [...ticketKeys.all, 'comments', id] as const,
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
  });
}
