import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import { useAuditLogs, exportAuditLogs } from '@/api/audit';
import type { AuditEntry } from '@/types/audit';
import type { PagedResponse } from '@/types/api';

vi.mock('@/lib/export', () => ({ exportToCsv: vi.fn() }));

const API_BASE = 'https://api.zyntapos.com';

const mockEntry: AuditEntry = {
  id: 'audit-1',
  eventType: 'PRODUCT_CREATED',
  category: 'inventory',
  userId: 'user-1',
  userName: 'Admin User',
  storeId: 'store-1',
  storeName: 'Colombo',
  entityType: 'Product',
  entityId: 'prod-1',
  success: true,
  errorMessage: null,
  metadata: null,
  createdAt: new Date('2024-06-01T10:00:00Z').toISOString(),
};

const mockPagedAudit: PagedResponse<AuditEntry> = {
  data: [mockEntry],
  total: 1,
  page: 1,
  size: 50,
  totalPages: 1,
};

// ── useAuditLogs ─────────────────────────────────────────────────────────────

describe('useAuditLogs', () => {
  it('fetches audit logs with no filters', async () => {
    server.use(
      http.get(`${API_BASE}/admin/audit`, () => HttpResponse.json(mockPagedAudit)),
    );

    const { result } = renderHook(() => useAuditLogs(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].id).toBe('audit-1');
  });

  it('returns paged data with page, size, total, totalPages', async () => {
    const paged: PagedResponse<AuditEntry> = {
      data: [mockEntry],
      total: 100,
      page: 2,
      size: 50,
      totalPages: 2,
    };

    server.use(
      http.get(`${API_BASE}/admin/audit`, () => HttpResponse.json(paged)),
    );

    const { result } = renderHook(() => useAuditLogs({ page: 2, size: 50 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.total).toBe(100);
    expect(result.current.data?.page).toBe(2);
    expect(result.current.data?.size).toBe(50);
    expect(result.current.data?.totalPages).toBe(2);
  });

  it('forwards page query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockPagedAudit);
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ page: 3 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('page=3');
  });

  it('forwards size query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockPagedAudit);
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ size: 100 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('size=100');
  });

  it('forwards category filter query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ category: 'inventory' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('category=inventory');
  });

  it('forwards eventType query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ eventType: 'PRODUCT_CREATED' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('eventType=PRODUCT_CREATED');
  });

  it('forwards userId query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ userId: 'user-42' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('userId=user-42');
  });

  it('forwards storeId query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ storeId: 'store-5' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('storeId=store-5');
  });

  it('forwards entityType query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ entityType: 'Order' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('entityType=Order');
  });

  it('forwards success=true filter query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ success: true }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('success=true');
  });

  it('forwards success=false filter query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ success: false }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('success=false');
  });

  it('forwards from and to date filters', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(
      () => useAuditLogs({ from: '2024-01-01', to: '2024-12-31' }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('from=2024-01-01');
    expect(capturedUrl).toContain('to=2024-12-31');
  });

  it('forwards search query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedAudit, data: [] });
      }),
    );

    const { result } = renderHook(() => useAuditLogs({ search: 'product update' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('search=product+update');
  });

  it('surfaces 403 errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/audit`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useAuditLogs(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── exportAuditLogs ──────────────────────────────────────────────────────────

describe('exportAuditLogs', () => {
  it('calls the export endpoint and triggers exportToCsv', async () => {
    const { exportToCsv } = await import('@/lib/export');
    const mockExportData: AuditEntry[] = [mockEntry];

    server.use(
      http.get(`${API_BASE}/admin/audit/export`, () =>
        HttpResponse.json(mockExportData),
      ),
    );

    await exportAuditLogs({});

    expect(exportToCsv).toHaveBeenCalledWith(
      mockExportData,
      'audit-log',
      expect.arrayContaining([
        expect.objectContaining({ key: 'eventType' }),
        expect.objectContaining({ key: 'category' }),
      ]),
    );
  });

  it('forwards category filter to the export endpoint', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit/export`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json([]);
      }),
    );

    await exportAuditLogs({ category: 'pos' });

    expect(capturedUrl).toContain('category=pos');
  });

  it('forwards success filter to the export endpoint', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit/export`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json([]);
      }),
    );

    await exportAuditLogs({ success: false });

    expect(capturedUrl).toContain('success=false');
  });

  it('forwards from, to, and storeId filters to the export endpoint', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/audit/export`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json([]);
      }),
    );

    await exportAuditLogs({ from: '2024-01-01', to: '2024-06-30', storeId: 'store-2' });

    expect(capturedUrl).toContain('from=2024-01-01');
    expect(capturedUrl).toContain('to=2024-06-30');
    expect(capturedUrl).toContain('storeId=store-2');
  });
});
