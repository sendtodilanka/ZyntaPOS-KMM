import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import {
  useStores,
  useStore,
  useStoreHealth,
  useAllStoreHealth,
  useUpdateStoreConfig,
} from '@/api/stores';
import type { Store, StoreHealth, StoreConfig } from '@/types/store';
import type { PagedResponse } from '@/types/api';

const API_BASE = 'https://api.zyntapos.com';

const mockStore: Store = {
  id: 'store-1',
  name: 'Colombo Store',
  location: '123 Main St, Colombo',
  licenseKey: 'ZYNTA-ABCD-1234-EFGH',
  edition: 'PROFESSIONAL',
  status: 'HEALTHY',
  activeUsers: 3,
  lastSyncAt: '2024-06-01T12:00:00Z',
  lastHeartbeatAt: '2024-06-01T12:05:00Z',
  appVersion: '1.0.0',
  createdAt: '2024-01-01T00:00:00Z',
};

const mockPagedStores: PagedResponse<Store> = {
  data: [mockStore],
  total: 1,
  page: 1,
  size: 20,
  totalPages: 1,
};

const mockStoreHealth: StoreHealth = {
  storeId: 'store-1',
  status: 'HEALTHY',
  healthScore: 98,
  dbSizeBytes: 52428800,
  syncQueueDepth: 0,
  errorCount24h: 0,
  uptimeHours: 720,
  lastHeartbeatAt: '2024-06-01T12:05:00Z',
  responseTimeMs: 45,
  appVersion: '1.0.0',
  osInfo: 'Android 14',
};

const mockStoreConfig: StoreConfig = {
  storeId: 'store-1',
  taxRates: [
    {
      id: 'tax-1',
      name: 'VAT',
      rate: 15,
      isDefault: true,
      storeIds: null,
    },
  ],
  featureFlags: { 'pos.hold_orders': true },
  timezone: 'Asia/Colombo',
  currency: 'LKR',
  receiptFooter: 'Thank you for shopping with us!',
  syncIntervalSeconds: 30,
  updatedAt: '2024-06-01T00:00:00Z',
};

// ── useStores ────────────────────────────────────────────────────────────────

describe('useStores', () => {
  it('fetches the first page of stores with no filters', async () => {
    server.use(
      http.get(`${API_BASE}/admin/stores`, () => HttpResponse.json(mockPagedStores)),
    );

    const { result } = renderHook(() => useStores(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].id).toBe('store-1');
  });

  it('forwards status filter query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/stores`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedStores, data: [] });
      }),
    );

    const { result } = renderHook(() => useStores({ status: 'OFFLINE' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('status=OFFLINE');
  });

  it('forwards search query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/stores`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedStores, data: [] });
      }),
    );

    const { result } = renderHook(() => useStores({ search: 'galle' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('search=galle');
  });

  it('forwards page query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/stores`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedStores, data: [], page: 2 });
      }),
    );

    const { result } = renderHook(() => useStores({ page: 2 }), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('page=2');
  });

  it('forwards size query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/stores`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedStores, data: [], size: 50 });
      }),
    );

    const { result } = renderHook(() => useStores({ size: 50 }), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('size=50');
  });

  it('forwards multiple filters simultaneously', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/stores`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedStores, data: [] });
      }),
    );

    const { result } = renderHook(
      () => useStores({ status: 'WARNING', search: 'kandy', page: 2, size: 10 }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('status=WARNING');
    expect(capturedUrl).toContain('search=kandy');
    expect(capturedUrl).toContain('page=2');
    expect(capturedUrl).toContain('size=10');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/stores`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useStores(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useStore ─────────────────────────────────────────────────────────────────

describe('useStore', () => {
  it('fetches a single store by ID', async () => {
    server.use(
      http.get(`${API_BASE}/admin/stores/:storeId`, () => HttpResponse.json(mockStore)),
    );

    const { result } = renderHook(() => useStore('store-1'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockStore);
  });

  it('does NOT fetch when storeId is an empty string', () => {
    const { result } = renderHook(() => useStore(''), { wrapper: createWrapper() });

    // enabled: !!storeId — should remain idle
    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoading).toBe(false);
  });

  it('surfaces 404 for an unknown storeId', async () => {
    server.use(
      http.get(`${API_BASE}/admin/stores/:storeId`, () =>
        HttpResponse.json({ message: 'Store not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useStore('unknown-store'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useStoreHealth ───────────────────────────────────────────────────────────

describe('useStoreHealth', () => {
  it('fetches health data for a given storeId', async () => {
    server.use(
      http.get(`${API_BASE}/admin/stores/:storeId/health`, () =>
        HttpResponse.json(mockStoreHealth),
      ),
    );

    const { result } = renderHook(() => useStoreHealth('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockStoreHealth);
    expect(result.current.data?.storeId).toBe('store-1');
    expect(result.current.data?.healthScore).toBe(98);
  });

  it('does NOT fetch when storeId is an empty string', () => {
    const { result } = renderHook(() => useStoreHealth(''), { wrapper: createWrapper() });

    // enabled: !!storeId
    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoading).toBe(false);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/stores/:storeId/health`, () =>
        HttpResponse.json({ message: 'Service Unavailable' }, { status: 503 }),
      ),
    );

    const { result } = renderHook(() => useStoreHealth('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAllStoreHealth ────────────────────────────────────────────────────────

describe('useAllStoreHealth', () => {
  it('fetches health data for all stores', async () => {
    const healthList: StoreHealth[] = [
      mockStoreHealth,
      {
        ...mockStoreHealth,
        storeId: 'store-2',
        status: 'WARNING',
        healthScore: 72,
        errorCount24h: 5,
      },
    ];

    server.use(
      http.get(`${API_BASE}/admin/health/stores`, () => HttpResponse.json(healthList)),
    );

    const { result } = renderHook(() => useAllStoreHealth(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(2);
    expect(result.current.data![0].storeId).toBe('store-1');
    expect(result.current.data![1].storeId).toBe('store-2');
    expect(result.current.data![1].status).toBe('WARNING');
  });

  it('returns an empty array when there are no stores', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/stores`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useAllStoreHealth(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([]);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/stores`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useAllStoreHealth(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateStoreConfig ─────────────────────────────────────────────────────

describe('useUpdateStoreConfig', () => {
  it('sends PUT to the correct URL and returns the updated config', async () => {
    let capturedUrl = '';
    server.use(
      http.put(`${API_BASE}/admin/stores/:storeId/config`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockStoreConfig);
      }),
    );

    const { result } = renderHook(() => useUpdateStoreConfig(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({
        storeId: 'store-1',
        config: { timezone: 'Asia/Colombo', currency: 'LKR' },
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/stores/store-1/config');
    expect(result.current.data).toEqual(mockStoreConfig);
  });

  it('sends partial config updates correctly', async () => {
    let requestBody: Record<string, unknown> = {};
    server.use(
      http.put(`${API_BASE}/admin/stores/:storeId/config`, async ({ request }) => {
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(mockStoreConfig);
      }),
    );

    const { result } = renderHook(() => useUpdateStoreConfig(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ storeId: 'store-1', config: { syncIntervalSeconds: 60 } });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(requestBody['syncIntervalSeconds']).toBe(60);
  });

  it('invalidates [stores, storeId] cache on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/stores/:storeId`, () => HttpResponse.json(mockStore)),
      http.put(`${API_BASE}/admin/stores/:storeId/config`, () =>
        HttpResponse.json(mockStoreConfig),
      ),
    );

    const wrapper = createWrapper();

    const { result: storeResult } = renderHook(() => useStore('store-1'), { wrapper });
    await waitFor(() => expect(storeResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useUpdateStoreConfig(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/stores/:storeId`, () => {
        refetchCount++;
        return HttpResponse.json(mockStore);
      }),
    );

    act(() => {
      mutResult.current.mutate({ storeId: 'store-1', config: { currency: 'LKR' } });
    });

    await waitFor(() => expect(mutResult.current.isSuccess).toBe(true));
    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces server errors', async () => {
    server.use(
      http.put(`${API_BASE}/admin/stores/:storeId/config`, () =>
        HttpResponse.json({ message: 'Store not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useUpdateStoreConfig(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ storeId: 'unknown-store', config: { currency: 'USD' } });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('surfaces 422 validation errors', async () => {
    server.use(
      http.put(`${API_BASE}/admin/stores/:storeId/config`, () =>
        HttpResponse.json({ message: 'Invalid timezone' }, { status: 422 }),
      ),
    );

    const { result } = renderHook(() => useUpdateStoreConfig(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ storeId: 'store-1', config: { timezone: 'Invalid/Zone' } });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
