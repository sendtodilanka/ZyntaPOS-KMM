import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import {
  useAdminUsers,
  useCreateUser,
  useUpdateUser,
  useDeactivateUser,
  useRevokeSessions,
} from '@/api/users';
import type { AdminUser } from '@/types/user';
import type { PagedResponse } from '@/types/api';

const API_BASE = 'https://api.zyntapos.com';

const mockUser: AdminUser = {
  id: 'user-1',
  email: 'admin@zyntapos.com',
  name: 'System Admin',
  role: 'ADMIN',
  mfaEnabled: false,
  isActive: true,
  lastLoginAt: null,
  createdAt: new Date('2024-01-01T00:00:00Z').getTime(),
};

const mockPagedUsers: PagedResponse<AdminUser> = {
  data: [mockUser],
  total: 1,
  page: 1,
  size: 20,
  totalPages: 1,
};

// ── useAdminUsers ────────────────────────────────────────────────────────────

describe('useAdminUsers', () => {
  it('fetches the first page of users with no filters', async () => {
    server.use(
      http.get(`${API_BASE}/admin/users`, () => HttpResponse.json(mockPagedUsers)),
    );

    const { result } = renderHook(() => useAdminUsers(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].id).toBe('user-1');
  });

  it('forwards page query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/users`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedUsers, page: 2 });
      }),
    );

    const { result } = renderHook(() => useAdminUsers({ page: 2 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('page=2');
  });

  it('forwards size query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/users`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedUsers, size: 50 });
      }),
    );

    const { result } = renderHook(() => useAdminUsers({ size: 50 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('size=50');
  });

  it('forwards role filter query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/users`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedUsers, data: [] });
      }),
    );

    const { result } = renderHook(() => useAdminUsers({ role: 'OPERATOR' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('role=OPERATOR');
  });

  it('forwards status filter query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/users`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedUsers, data: [] });
      }),
    );

    const { result } = renderHook(() => useAdminUsers({ status: 'ACTIVE' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('status=ACTIVE');
  });

  it('forwards search query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/users`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedUsers, data: [] });
      }),
    );

    const { result } = renderHook(() => useAdminUsers({ search: 'alice' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('search=alice');
  });

  it('forwards multiple filters simultaneously', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/users`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedUsers, data: [] });
      }),
    );

    const { result } = renderHook(
      () => useAdminUsers({ role: 'FINANCE', status: 'ACTIVE', page: 3, size: 10 }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('role=FINANCE');
    expect(capturedUrl).toContain('status=ACTIVE');
    expect(capturedUrl).toContain('page=3');
    expect(capturedUrl).toContain('size=10');
  });

  it('surfaces 403 errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/users`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useAdminUsers(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useCreateUser ────────────────────────────────────────────────────────────

describe('useCreateUser', () => {
  it('returns the created user on success', async () => {
    const newUser: AdminUser = {
      ...mockUser,
      id: 'user-new',
      email: 'operator@zyntapos.com',
      name: 'New Operator',
      role: 'OPERATOR',
    };

    server.use(
      http.post(`${API_BASE}/admin/users`, () => HttpResponse.json(newUser, { status: 201 })),
    );

    const { result } = renderHook(() => useCreateUser(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({
        email: 'operator@zyntapos.com',
        name: 'New Operator',
        role: 'OPERATOR',
        password: 'StrongP@ss1!',
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(newUser);
  });

  it('invalidates the [users] query cache on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/users`, () => HttpResponse.json(mockPagedUsers)),
      http.post(`${API_BASE}/admin/users`, () =>
        HttpResponse.json({ ...mockUser, id: 'user-new' }, { status: 201 }),
      ),
    );

    const wrapper = createWrapper();

    // Pre-populate the users list cache
    const { result: listResult } = renderHook(() => useAdminUsers(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useCreateUser(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/users`, () => {
        refetchCount++;
        return HttpResponse.json(mockPagedUsers);
      }),
    );

    act(() => {
      mutResult.current.mutate({
        email: 'new@zyntapos.com',
        name: 'New User',
        role: 'HELPDESK',
        password: 'Pass@1234',
      });
    });

    await waitFor(() => expect(mutResult.current.isSuccess).toBe(true));
    // invalidateQueries should trigger a background refetch
    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces 422 validation errors', async () => {
    server.use(
      http.post(`${API_BASE}/admin/users`, () =>
        HttpResponse.json({ message: 'Email already exists' }, { status: 422 }),
      ),
    );

    const { result } = renderHook(() => useCreateUser(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({
        email: 'admin@zyntapos.com',
        name: 'Dup',
        role: 'ADMIN',
        password: 'Pass@1',
      });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateUser ────────────────────────────────────────────────────────────

describe('useUpdateUser', () => {
  it('patches the correct URL and returns the updated user', async () => {
    let capturedUrl = '';
    const updatedUser: AdminUser = { ...mockUser, name: 'Updated Name' };

    server.use(
      http.patch(`${API_BASE}/admin/users/:userId`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(updatedUser);
      }),
    );

    const { result } = renderHook(() => useUpdateUser(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ userId: 'user-1', data: { name: 'Updated Name' } });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/users/user-1');
    expect(result.current.data).toEqual(updatedUser);
  });

  it('surfaces 404 errors for unknown userId', async () => {
    server.use(
      http.patch(`${API_BASE}/admin/users/:userId`, () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useUpdateUser(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ userId: 'unknown', data: { name: 'X' } });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeactivateUser ────────────────────────────────────────────────────────

describe('useDeactivateUser', () => {
  it('sends isActive: false and returns the deactivated user', async () => {
    let requestBody: Record<string, unknown> = {};
    const deactivatedUser: AdminUser = { ...mockUser, isActive: false };

    server.use(
      http.patch(`${API_BASE}/admin/users/:userId`, async ({ request }) => {
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(deactivatedUser);
      }),
    );

    const { result } = renderHook(() => useDeactivateUser(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('user-1');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requestBody['isActive']).toBe(false);
    expect(result.current.data).toEqual(deactivatedUser);
  });

  it('invalidates [users] cache on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/users`, () => HttpResponse.json(mockPagedUsers)),
      http.patch(`${API_BASE}/admin/users/:userId`, () =>
        HttpResponse.json({ ...mockUser, isActive: false }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useAdminUsers(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useDeactivateUser(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/users`, () => {
        refetchCount++;
        return HttpResponse.json(mockPagedUsers);
      }),
    );

    act(() => {
      mutResult.current.mutate('user-1');
    });

    await waitFor(() => expect(mutResult.current.isSuccess).toBe(true));
    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces errors', async () => {
    server.use(
      http.patch(`${API_BASE}/admin/users/:userId`, () =>
        HttpResponse.json({ message: 'Cannot deactivate last admin' }, { status: 409 }),
      ),
    );

    const { result } = renderHook(() => useDeactivateUser(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('user-1');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useRevokeSessions ────────────────────────────────────────────────────────

describe('useRevokeSessions', () => {
  it('calls DELETE on the correct sessions URL', async () => {
    let capturedUrl = '';
    server.use(
      http.delete(`${API_BASE}/admin/users/:userId/sessions`, ({ request }) => {
        capturedUrl = request.url;
        return new HttpResponse(null, { status: 200 });
      }),
    );

    const { result } = renderHook(() => useRevokeSessions(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('user-1');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/users/user-1/sessions');
  });

  it('invalidates [users, userId, sessions] cache on success', async () => {
    const mockSessions = [
      {
        id: 'session-1',
        userAgent: 'Mozilla/5.0',
        ipAddress: '10.0.0.1',
        createdAt: Date.now() - 1000,
        expiresAt: Date.now() + 3600_000,
      },
    ];

    server.use(
      http.get(`${API_BASE}/admin/users/:userId/sessions`, () =>
        HttpResponse.json(mockSessions),
      ),
      http.delete(`${API_BASE}/admin/users/:userId/sessions`, () =>
        new HttpResponse(null, { status: 200 }),
      ),
    );

    // Import useListSessions here to seed the sessions cache
    const { useListSessions } = await import('@/api/auth');
    const wrapper = createWrapper();

    const { result: sessionsResult } = renderHook(() => useListSessions('user-1'), {
      wrapper,
    });
    await waitFor(() => expect(sessionsResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useRevokeSessions(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/users/:userId/sessions`, () => {
        refetchCount++;
        return HttpResponse.json([]);
      }),
    );

    act(() => {
      mutResult.current.mutate('user-1');
    });

    await waitFor(() => expect(mutResult.current.isSuccess).toBe(true));
    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('enters error state on server failure', async () => {
    server.use(
      http.delete(`${API_BASE}/admin/users/:userId/sessions`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useRevokeSessions(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('user-1');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
