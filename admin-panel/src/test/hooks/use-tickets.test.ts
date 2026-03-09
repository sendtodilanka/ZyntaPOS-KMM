import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import { useTickets, useCreateTicket, ticketKeys } from '@/api/tickets';
import type { TicketsPage, Ticket } from '@/types/ticket';

// Matches apiClient prefixUrl default (VITE_API_URL ?? 'https://api.zyntapos.com')
const API_BASE = 'https://api.zyntapos.com';

const mockTicket: Ticket = {
  id: 'ticket-1',
  ticketNumber: 'TK-001',
  storeId: 'store-1',
  licenseId: null,
  createdBy: 'user-1',
  createdByName: 'Test User',
  customerName: 'John Doe',
  customerEmail: 'john@example.com',
  customerPhone: null,
  assignedTo: null,
  assignedToName: null,
  assignedAt: null,
  title: 'Test Ticket',
  description: 'Test description',
  category: 'SOFTWARE',
  priority: 'MEDIUM',
  status: 'OPEN',
  resolvedBy: null,
  resolvedAt: null,
  resolutionNote: null,
  timeSpentMin: null,
  slaDueAt: null,
  slaBreached: false,
  createdAt: Date.now(),
  updatedAt: Date.now(),
};

const mockTicketsPage: TicketsPage = {
  items: [mockTicket],
  total: 1,
  page: 1,
  size: 20,
};

describe('useTickets', () => {
  it('fetches tickets and returns items', async () => {
    server.use(
      http.get(`${API_BASE}/admin/tickets`, () => HttpResponse.json(mockTicketsPage)),
    );

    const { result } = renderHook(() => useTickets(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.items).toHaveLength(1);
    expect(result.current.data?.items[0].id).toBe('ticket-1');
  });

  it('forwards status filter as query string', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/tickets`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ items: [], total: 0, page: 1, size: 20 });
      }),
    );

    const { result } = renderHook(() => useTickets({ status: 'OPEN' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('status=OPEN');
  });

  it('forwards multiple filters as query string', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/tickets`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ items: [], total: 0, page: 1, size: 20 });
      }),
    );

    const { result } = renderHook(
      () => useTickets({ status: 'ASSIGNED', priority: 'HIGH', page: 2 }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('status=ASSIGNED');
    expect(capturedUrl).toContain('priority=HIGH');
    expect(capturedUrl).toContain('page=2');
  });

  it('surfaces 403 errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/tickets`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useTickets(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useCreateTicket', () => {
  it('invalidates tickets cache on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/tickets`, () => HttpResponse.json(mockTicketsPage)),
      http.post(`${API_BASE}/admin/tickets`, () => HttpResponse.json(mockTicket, { status: 201 })),
    );

    const wrapper = createWrapper();
    const { result: listResult } = renderHook(() => useTickets(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutationResult } = renderHook(() => useCreateTicket(), { wrapper });

    let fetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/tickets`, () => {
        fetchCount++;
        return HttpResponse.json(mockTicketsPage);
      }),
    );

    await act(async () => {
      await mutationResult.current.mutateAsync({
        customerName: 'Jane Doe',
        title: 'New Ticket',
        description: 'Description',
        category: 'SOFTWARE',
        priority: 'LOW',
      });
    });

    // After mutation, cache for tickets:all should be invalidated (triggering a refetch)
    await waitFor(() => expect(fetchCount).toBeGreaterThan(0));
  });

  it('surfaces 422 validation errors', async () => {
    server.use(
      http.post(`${API_BASE}/admin/tickets`, () =>
        HttpResponse.json({ message: 'Validation failed' }, { status: 422 }),
      ),
    );

    const { result } = renderHook(() => useCreateTicket(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({
        customerName: '',
        title: '',
        description: '',
        category: 'OTHER',
        priority: 'LOW',
      });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('ticketKeys', () => {
  it('all key is stable', () => {
    expect(ticketKeys.all).toEqual(['tickets']);
  });

  it('list key includes filter', () => {
    const filter = { status: 'OPEN' as const };
    expect(ticketKeys.list(filter)).toEqual(['tickets', 'list', filter]);
  });

  it('detail key includes id', () => {
    expect(ticketKeys.detail('ticket-1')).toEqual(['tickets', 'detail', 'ticket-1']);
  });
});
