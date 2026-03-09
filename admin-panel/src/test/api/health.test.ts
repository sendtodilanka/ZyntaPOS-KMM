import { describe, it, expect } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import {
  useSystemHealth,
  useAllStoreHealth,
  useStoreHealthDetail,
  healthKeys,
} from '@/api/health';
import type { SystemHealth, StoreHealthSummary } from '@/types/health';

const API_BASE = 'https://api.zyntapos.com';

const mockSystemHealth: SystemHealth = {
  overall: 'healthy',
  checkedAt: new Date().toISOString(),
  services: [],
};

const mockStoreHealthSummary: StoreHealthSummary = {
  storeId: 'store-1',
  storeName: 'Colombo Store',
  status: 'healthy',
  lastSync: new Date().toISOString(),
  pendingOperations: 0,
  appVersion: '1.0.0',
  androidVersion: '14',
  uptimePercent: 99.9,
};

// ── healthKeys ───────────────────────────────────────────────────────────────

describe('healthKeys', () => {
  it('all is ["health"]', () => {
    expect(healthKeys.all).toEqual(['health']);
  });

  it('system() returns ["health", "system"]', () => {
    expect(healthKeys.system()).toEqual(['health', 'system']);
  });

  it('stores() returns ["health", "stores"]', () => {
    expect(healthKeys.stores()).toEqual(['health', 'stores']);
  });

  it('store(id) returns ["health", "store", id]', () => {
    expect(healthKeys.store('store-1')).toEqual(['health', 'store', 'store-1']);
  });
});

// ── useSystemHealth ──────────────────────────────────────────────────────────

describe('useSystemHealth', () => {
  it('fetches system health status', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/system`, () =>
        HttpResponse.json(mockSystemHealth),
      ),
    );

    const { result } = renderHook(() => useSystemHealth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockSystemHealth);
    expect(result.current.data?.overall).toBe('healthy');
  });

  it('returns degraded status when a service is unhealthy', async () => {
    const degradedHealth: SystemHealth = {
      ...mockSystemHealth,
      overall: 'degraded',
      services: [{ name: 'database', status: 'unhealthy', latencyMs: 5000, uptime: 0, lastChecked: new Date().toISOString() }],
    };

    server.use(
      http.get(`${API_BASE}/admin/health/system`, () =>
        HttpResponse.json(degradedHealth),
      ),
    );

    const { result } = renderHook(() => useSystemHealth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.overall).toBe('degraded');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/system`, () =>
        HttpResponse.json({ message: 'Service Unavailable' }, { status: 503 }),
      ),
    );

    const { result } = renderHook(() => useSystemHealth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAllStoreHealth ────────────────────────────────────────────────────────

describe('useAllStoreHealth', () => {
  it('fetches health summaries for all stores', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/stores`, () =>
        HttpResponse.json([mockStoreHealthSummary]),
      ),
    );

    const { result } = renderHook(() => useAllStoreHealth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].storeId).toBe('store-1');
    expect(result.current.data![0].status).toBe('healthy');
  });

  it('returns an empty array when no stores are configured', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/stores`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useAllStoreHealth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('returns multiple stores with mixed health statuses', async () => {
    const stores: StoreHealthSummary[] = [
      mockStoreHealthSummary,
      {
        storeId: 'store-2',
        storeName: 'Galle Store',
        status: 'degraded',
        lastSync: new Date().toISOString(),
        pendingOperations: 150,
        appVersion: '1.0.0',
        androidVersion: '13',
        uptimePercent: 85.0,
      },
    ];

    server.use(
      http.get(`${API_BASE}/admin/health/stores`, () => HttpResponse.json(stores)),
    );

    const { result } = renderHook(() => useAllStoreHealth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(2);
    expect(result.current.data![1].status).toBe('degraded');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/stores`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useAllStoreHealth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useStoreHealthDetail ─────────────────────────────────────────────────────

describe('useStoreHealthDetail', () => {
  it('fetches detailed health for a specific store', async () => {
    let capturedUrl = '';
    const mockDetail = {
      ...mockStoreHealthSummary,
      syncQueueDepth: 0,
      lastSyncAt: new Date().toISOString(),
      dbSizeBytes: 1024000,
      uptimeSeconds: 86400,
    };

    server.use(
      http.get(`${API_BASE}/admin/health/stores/:storeId`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockDetail);
      }),
    );

    const { result } = renderHook(() => useStoreHealthDetail('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/health/stores/store-1');
    expect(result.current.data).toEqual(mockDetail);
  });

  it('does NOT fetch when storeId is an empty string', () => {
    const { result } = renderHook(() => useStoreHealthDetail(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoading).toBe(false);
  });

  it('remains idle when storeId is an empty string', () => {
    const { result } = renderHook(() => useStoreHealthDetail(''), {
      wrapper: createWrapper(),
    });

    // enabled: !!storeId — should not initiate a fetch
    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.data).toBeUndefined();
  });

  it('surfaces 404 for an unknown storeId', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/stores/:storeId`, () =>
        HttpResponse.json({ message: 'Store not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useStoreHealthDetail('store-unknown'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/health/stores/:storeId`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useStoreHealthDetail('store-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
