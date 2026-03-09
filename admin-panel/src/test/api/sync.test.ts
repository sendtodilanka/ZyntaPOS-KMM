import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import {
  useSyncStatus,
  useStoreSync,
  useSyncQueue,
  useConflictLog,
  useDeadLetters,
  useRetryDeadLetter,
  useDiscardDeadLetter,
  useForceSync,
} from '@/api/sync';
import type { StoreSyncStatus, SyncOperation, SyncConflict, DeadLetterOperation } from '@/types/sync';

const API_BASE = 'https://api.zyntapos.com';

const mockStoreSyncStatus: StoreSyncStatus = {
  storeId: 'store-1',
  storeName: 'Colombo',
  status: 'SYNCED',
  queueDepth: 0,
  lastSyncAt: null,
  lastSyncDurationMs: null,
  errorCount: 0,
  pendingOperations: 0,
};

const mockSyncOperation: SyncOperation = {
  id: 'op-1',
  storeId: 'store-1',
  entityType: 'Product',
  entityId: 'prod-1',
  operation: 'UPDATE',
  payload: null,
  status: 'PENDING',
  createdAt: new Date().toISOString(),
  processedAt: null,
  errorMessage: null,
};

const mockConflict: SyncConflict = {
  id: 'conflict-1',
  storeId: 'store-1',
  entityType: 'Product',
  entityId: 'prod-1',
  localVersion: 2,
  serverVersion: 3,
  conflictType: 'UPDATE_UPDATE',
  resolvedAt: null,
  createdAt: new Date().toISOString(),
};

const mockDeadLetter: DeadLetterOperation = {
  id: 'dead-1',
  storeId: 'store-1',
  entityType: 'Order',
  entityId: 'order-1',
  operation: 'CREATE',
  payload: null,
  errorMessage: 'Timeout',
  failedAt: new Date().toISOString(),
  retryCount: 3,
};

// ── useSyncStatus ────────────────────────────────────────────────────────────

describe('useSyncStatus', () => {
  it('fetches the list of store sync statuses', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/status`, () =>
        HttpResponse.json([mockStoreSyncStatus]),
      ),
    );

    const { result } = renderHook(() => useSyncStatus(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].storeId).toBe('store-1');
  });

  it('returns an empty array when no stores are configured', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/status`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useSyncStatus(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/status`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useSyncStatus(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useStoreSync ─────────────────────────────────────────────────────────────

describe('useStoreSync', () => {
  it('fetches the sync status for a specific store', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/:storeId`, () =>
        HttpResponse.json(mockStoreSyncStatus),
      ),
    );

    const { result } = renderHook(() => useStoreSync('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.storeId).toBe('store-1');
    expect(result.current.data?.status).toBe('SYNCED');
  });

  it('does NOT fetch when storeId is an empty string', () => {
    const { result } = renderHook(() => useStoreSync(''), { wrapper: createWrapper() });

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoading).toBe(false);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/:storeId`, () =>
        HttpResponse.json({ message: 'Store not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useStoreSync('store-unknown'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSyncQueue ─────────────────────────────────────────────────────────────

describe('useSyncQueue', () => {
  it('fetches the sync queue for a specific store', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/:storeId/queue`, () =>
        HttpResponse.json([mockSyncOperation]),
      ),
    );

    const { result } = renderHook(() => useSyncQueue('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].id).toBe('op-1');
  });

  it('does NOT fetch when storeId is an empty string', () => {
    const { result } = renderHook(() => useSyncQueue(''), { wrapper: createWrapper() });

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoading).toBe(false);
  });

  it('returns an empty array when queue is empty', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/:storeId/queue`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useSyncQueue('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });
});

// ── useConflictLog ───────────────────────────────────────────────────────────

describe('useConflictLog', () => {
  it('uses the global conflicts endpoint when storeId is not provided', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/sync/conflicts`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json([mockConflict]);
      }),
    );

    const { result } = renderHook(() => useConflictLog(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/sync/conflicts');
    expect(capturedUrl).not.toContain('/admin/sync/store-');
    expect(result.current.data).toHaveLength(1);
  });

  it('uses the store-specific conflicts endpoint when storeId is provided', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/sync/:storeId/conflicts`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json([mockConflict]);
      }),
    );

    const { result } = renderHook(() => useConflictLog('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/sync/store-1/conflicts');
  });

  it('returns an empty array when there are no conflicts', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/conflicts`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useConflictLog(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });
});

// ── useDeadLetters ───────────────────────────────────────────────────────────

describe('useDeadLetters', () => {
  it('fetches dead letters from global endpoint when storeId is not provided', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/dead-letters`, () =>
        HttpResponse.json([mockDeadLetter]),
      ),
    );

    const { result } = renderHook(() => useDeadLetters(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].id).toBe('dead-1');
  });

  it('fetches dead letters from store-specific endpoint when storeId is provided', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/sync/:storeId/dead-letters`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json([mockDeadLetter]);
      }),
    );

    const { result } = renderHook(() => useDeadLetters('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/sync/store-1/dead-letters');
  });

  it('returns an empty array when there are no dead letters', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/dead-letters`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useDeadLetters(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });
});

// ── useRetryDeadLetter ───────────────────────────────────────────────────────

describe('useRetryDeadLetter', () => {
  it('posts to the retry endpoint for a given dead letter id', async () => {
    let capturedUrl = '';
    server.use(
      http.post(`${API_BASE}/admin/sync/dead-letters/:id/retry`, ({ request }) => {
        capturedUrl = request.url;
        return new HttpResponse(null, { status: 200 });
      }),
    );

    const { result } = renderHook(() => useRetryDeadLetter(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync('dead-1');
    });

    expect(capturedUrl).toContain('/admin/sync/dead-letters/dead-1/retry');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('invalidates [sync, dead-letters] query key on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/dead-letters`, () =>
        HttpResponse.json([mockDeadLetter]),
      ),
      http.post(`${API_BASE}/admin/sync/dead-letters/:id/retry`, () =>
        new HttpResponse(null, { status: 200 }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useDeadLetters(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useRetryDeadLetter(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/sync/dead-letters`, () => {
        refetchCount++;
        return HttpResponse.json([]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync('dead-1');
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('enters error state on server failure', async () => {
    server.use(
      http.post(`${API_BASE}/admin/sync/dead-letters/:id/retry`, () =>
        HttpResponse.json({ message: 'Operation not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useRetryDeadLetter(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate('dead-unknown');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDiscardDeadLetter ─────────────────────────────────────────────────────

describe('useDiscardDeadLetter', () => {
  it('sends DELETE to the dead letter endpoint', async () => {
    let capturedUrl = '';
    server.use(
      http.delete(`${API_BASE}/admin/sync/dead-letters/:id`, ({ request }) => {
        capturedUrl = request.url;
        return new HttpResponse(null, { status: 200 });
      }),
    );

    const { result } = renderHook(() => useDiscardDeadLetter(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync('dead-1');
    });

    expect(capturedUrl).toContain('/admin/sync/dead-letters/dead-1');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('invalidates [sync, dead-letters] query key on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/dead-letters`, () =>
        HttpResponse.json([mockDeadLetter]),
      ),
      http.delete(`${API_BASE}/admin/sync/dead-letters/:id`, () =>
        new HttpResponse(null, { status: 200 }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useDeadLetters(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useDiscardDeadLetter(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/sync/dead-letters`, () => {
        refetchCount++;
        return HttpResponse.json([]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync('dead-1');
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('enters error state when the operation cannot be discarded', async () => {
    server.use(
      http.delete(`${API_BASE}/admin/sync/dead-letters/:id`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useDiscardDeadLetter(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate('dead-1');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useForceSync ─────────────────────────────────────────────────────────────

describe('useForceSync', () => {
  it('posts to the force sync endpoint for a given storeId', async () => {
    let capturedUrl = '';
    server.use(
      http.post(`${API_BASE}/admin/sync/:storeId/force`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ operationsQueued: 5 });
      }),
    );

    const { result } = renderHook(() => useForceSync(), { wrapper: createWrapper() });

    await act(async () => {
      await result.current.mutateAsync('store-1');
    });

    expect(capturedUrl).toContain('/admin/sync/store-1/force');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('invalidates [sync] query key on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/sync/status`, () =>
        HttpResponse.json([mockStoreSyncStatus]),
      ),
      http.post(`${API_BASE}/admin/sync/:storeId/force`, () =>
        HttpResponse.json({ operationsQueued: 3 }),
      ),
    );

    const wrapper = createWrapper();

    const { result: statusResult } = renderHook(() => useSyncStatus(), { wrapper });
    await waitFor(() => expect(statusResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useForceSync(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/sync/status`, () => {
        refetchCount++;
        return HttpResponse.json([mockStoreSyncStatus]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync('store-1');
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('enters error state when force sync fails', async () => {
    server.use(
      http.post(`${API_BASE}/admin/sync/:storeId/force`, () =>
        HttpResponse.json({ message: 'Store offline' }, { status: 503 }),
      ),
    );

    const { result } = renderHook(() => useForceSync(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('store-offline');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
