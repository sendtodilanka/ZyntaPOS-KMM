import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import {
  useAlerts,
  useAlertCounts,
  useAlertRules,
  useAcknowledgeAlert,
  useResolveAlert,
  useSilenceAlert,
  useToggleAlertRule,
  alertKeys,
} from '@/api/alerts';
import type { Alert, AlertRule, AlertsPage } from '@/types/alert';

const API_BASE = 'https://api.zyntapos.com';

const mockAlert: Alert = {
  id: 'alert-1',
  title: 'High Queue Depth',
  message: 'Sync queue above threshold',
  severity: 'high',
  status: 'active',
  category: 'sync',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

const mockAlertsPage: AlertsPage = {
  items: [mockAlert],
  total: 1,
  page: 1,
  pageSize: 20,
};

const mockAlertRule: AlertRule = {
  id: 'rule-1',
  name: 'Queue Depth Alert',
  description: 'Fires when queue depth exceeds threshold',
  severity: 'high',
  enabled: true,
  category: 'sync',
  conditions: { threshold: 100 },
  notifyChannels: ['email'],
};

// ── alertKeys ────────────────────────────────────────────────────────────────

describe('alertKeys', () => {
  it('all is ["alerts"]', () => {
    expect(alertKeys.all).toEqual(['alerts']);
  });

  it('list(filter) includes the filter in the key', () => {
    const filter = { status: 'active' };
    const key = alertKeys.list(filter);
    expect(key[0]).toBe('alerts');
    expect(key[1]).toBe('list');
    expect(key[2]).toEqual(filter);
  });

  it('rules() returns ["alerts", "rules"]', () => {
    expect(alertKeys.rules()).toEqual(['alerts', 'rules']);
  });

  it('counts() returns ["alerts", "counts"]', () => {
    expect(alertKeys.counts()).toEqual(['alerts', 'counts']);
  });
});

// ── useAlerts ────────────────────────────────────────────────────────────────

describe('useAlerts', () => {
  it('fetches alerts with no filter', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts`, () => HttpResponse.json(mockAlertsPage)),
    );

    const { result } = renderHook(() => useAlerts(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.items).toHaveLength(1);
    expect(result.current.data?.items[0].id).toBe('alert-1');
  });

  it('returns total, page, and pageSize in response', async () => {
    const paged: AlertsPage = {
      items: [mockAlert],
      total: 42,
      page: 2,
      pageSize: 10,
    };

    server.use(
      http.get(`${API_BASE}/admin/alerts`, () => HttpResponse.json(paged)),
    );

    const { result } = renderHook(() => useAlerts({ page: 2, pageSize: 10 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.total).toBe(42);
    expect(result.current.data?.page).toBe(2);
    expect(result.current.data?.pageSize).toBe(10);
  });

  it('forwards status filter as query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/alerts`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockAlertsPage, items: [] });
      }),
    );

    const { result } = renderHook(() => useAlerts({ status: 'active' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('status=active');
  });

  it('forwards severity filter as query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${API_BASE}/admin/alerts`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockAlertsPage, items: [] });
      }),
    );

    const { result } = renderHook(() => useAlerts({ severity: 'HIGH' } as Parameters<typeof useAlerts>[0]), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('severity=HIGH');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useAlerts(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAlertCounts ───────────────────────────────────────────────────────────

describe('useAlertCounts', () => {
  it('fetches alert counts', async () => {
    const mockCounts: Record<string, number> = { active: 5, critical: 2, warning: 3 };

    server.use(
      http.get(`${API_BASE}/admin/alerts/counts`, () => HttpResponse.json(mockCounts)),
    );

    const { result } = renderHook(() => useAlertCounts(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockCounts);
    expect(result.current.data?.active).toBe(5);
    expect(result.current.data?.critical).toBe(2);
  });

  it('returns zero counts when no alerts are active', async () => {
    const mockCounts: Record<string, number> = { active: 0, critical: 0 };

    server.use(
      http.get(`${API_BASE}/admin/alerts/counts`, () => HttpResponse.json(mockCounts)),
    );

    const { result } = renderHook(() => useAlertCounts(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.active).toBe(0);
    expect(result.current.data?.critical).toBe(0);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts/counts`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useAlertCounts(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAlertRules ────────────────────────────────────────────────────────────

describe('useAlertRules', () => {
  it('fetches alert rules', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts/rules`, () =>
        HttpResponse.json([mockAlertRule]),
      ),
    );

    const { result } = renderHook(() => useAlertRules(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].id).toBe('rule-1');
    expect(result.current.data![0].enabled).toBe(true);
  });

  it('returns an empty array when no rules are configured', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts/rules`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useAlertRules(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts/rules`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useAlertRules(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAcknowledgeAlert ──────────────────────────────────────────────────────

describe('useAcknowledgeAlert', () => {
  it('posts to the acknowledge endpoint for a given alert id', async () => {
    let capturedUrl = '';
    const acknowledgedAlert: Alert = { ...mockAlert, status: 'acknowledged' };

    server.use(
      http.post(`${API_BASE}/admin/alerts/:id/acknowledge`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(acknowledgedAlert);
      }),
    );

    const { result } = renderHook(() => useAcknowledgeAlert(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const returned = await result.current.mutateAsync('alert-1');
      expect(returned).toEqual(acknowledgedAlert);
    });

    expect(capturedUrl).toContain('/admin/alerts/alert-1/acknowledge');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('invalidates ["alerts"] query key on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts`, () => HttpResponse.json(mockAlertsPage)),
      http.post(`${API_BASE}/admin/alerts/:id/acknowledge`, () =>
        HttpResponse.json({ ...mockAlert, status: 'acknowledged' }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useAlerts(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useAcknowledgeAlert(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/alerts`, () => {
        refetchCount++;
        return HttpResponse.json(mockAlertsPage);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync('alert-1');
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('enters error state on server failure', async () => {
    server.use(
      http.post(`${API_BASE}/admin/alerts/:id/acknowledge`, () =>
        HttpResponse.json({ message: 'Alert not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useAcknowledgeAlert(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate('alert-unknown');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useResolveAlert ──────────────────────────────────────────────────────────

describe('useResolveAlert', () => {
  it('posts to the resolve endpoint for a given alert id', async () => {
    let capturedUrl = '';
    const resolvedAlert: Alert = { ...mockAlert, status: 'resolved' };

    server.use(
      http.post(`${API_BASE}/admin/alerts/:id/resolve`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(resolvedAlert);
      }),
    );

    const { result } = renderHook(() => useResolveAlert(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const returned = await result.current.mutateAsync('alert-1');
      expect(returned).toEqual(resolvedAlert);
    });

    expect(capturedUrl).toContain('/admin/alerts/alert-1/resolve');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('invalidates ["alerts"] query key on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts`, () => HttpResponse.json(mockAlertsPage)),
      http.post(`${API_BASE}/admin/alerts/:id/resolve`, () =>
        HttpResponse.json({ ...mockAlert, status: 'resolved' }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useAlerts(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useResolveAlert(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/alerts`, () => {
        refetchCount++;
        return HttpResponse.json(mockAlertsPage);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync('alert-1');
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('enters error state on server failure', async () => {
    server.use(
      http.post(`${API_BASE}/admin/alerts/:id/resolve`, () =>
        HttpResponse.json({ message: 'Alert not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useResolveAlert(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate('alert-unknown');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSilenceAlert ──────────────────────────────────────────────────────────

describe('useSilenceAlert', () => {
  it('posts to the silence endpoint with durationMinutes in the body', async () => {
    let capturedUrl = '';
    let requestBody: Record<string, unknown> = {};
    const silencedAlert: Alert = { ...mockAlert, status: 'silenced' };

    server.use(
      http.post(`${API_BASE}/admin/alerts/:id/silence`, async ({ request }) => {
        capturedUrl = request.url;
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(silencedAlert);
      }),
    );

    const { result } = renderHook(() => useSilenceAlert(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const returned = await result.current.mutateAsync({ id: 'alert-1', durationMinutes: 60 });
      expect(returned).toEqual(silencedAlert);
    });

    expect(capturedUrl).toContain('/admin/alerts/alert-1/silence');
    expect(requestBody['durationMinutes']).toBe(60);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('accepts various silence durations', async () => {
    let requestBody: Record<string, unknown> = {};

    server.use(
      http.post(`${API_BASE}/admin/alerts/:id/silence`, async ({ request }) => {
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...mockAlert, status: 'silenced' });
      }),
    );

    const { result } = renderHook(() => useSilenceAlert(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync({ id: 'alert-1', durationMinutes: 1440 });
    });

    expect(requestBody['durationMinutes']).toBe(1440);
  });

  it('invalidates ["alerts"] query key on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts`, () => HttpResponse.json(mockAlertsPage)),
      http.post(`${API_BASE}/admin/alerts/:id/silence`, () =>
        HttpResponse.json({ ...mockAlert, status: 'silenced' }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useAlerts(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useSilenceAlert(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/alerts`, () => {
        refetchCount++;
        return HttpResponse.json(mockAlertsPage);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync({ id: 'alert-1', durationMinutes: 30 });
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('enters error state on server failure', async () => {
    server.use(
      http.post(`${API_BASE}/admin/alerts/:id/silence`, () =>
        HttpResponse.json({ message: 'Alert not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useSilenceAlert(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ id: 'alert-unknown', durationMinutes: 60 });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useToggleAlertRule ───────────────────────────────────────────────────────

describe('useToggleAlertRule', () => {
  it('patches the rule endpoint with { enabled } in the body', async () => {
    let capturedUrl = '';
    let requestBody: Record<string, unknown> = {};
    const updatedRule: AlertRule = { ...mockAlertRule, enabled: false };

    server.use(
      http.patch(`${API_BASE}/admin/alerts/rules/:id`, async ({ request }) => {
        capturedUrl = request.url;
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(updatedRule);
      }),
    );

    const { result } = renderHook(() => useToggleAlertRule(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const returned = await result.current.mutateAsync({ id: 'rule-1', enabled: false });
      expect(returned).toEqual(updatedRule);
    });

    expect(capturedUrl).toContain('/admin/alerts/rules/rule-1');
    expect(requestBody['enabled']).toBe(false);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('can enable a previously disabled rule', async () => {
    let requestBody: Record<string, unknown> = {};

    server.use(
      http.patch(`${API_BASE}/admin/alerts/rules/:id`, async ({ request }) => {
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...mockAlertRule, enabled: true });
      }),
    );

    const { result } = renderHook(() => useToggleAlertRule(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync({ id: 'rule-1', enabled: true });
    });

    expect(requestBody['enabled']).toBe(true);
  });

  it('invalidates alertKeys.rules() query key on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/alerts/rules`, () =>
        HttpResponse.json([mockAlertRule]),
      ),
      http.patch(`${API_BASE}/admin/alerts/rules/:id`, () =>
        HttpResponse.json({ ...mockAlertRule, enabled: false }),
      ),
    );

    const wrapper = createWrapper();

    const { result: rulesResult } = renderHook(() => useAlertRules(), { wrapper });
    await waitFor(() => expect(rulesResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useToggleAlertRule(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/alerts/rules`, () => {
        refetchCount++;
        return HttpResponse.json([{ ...mockAlertRule, enabled: false }]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync({ id: 'rule-1', enabled: false });
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('enters error state on server failure', async () => {
    server.use(
      http.patch(`${API_BASE}/admin/alerts/rules/:id`, () =>
        HttpResponse.json({ message: 'Rule not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useToggleAlertRule(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ id: 'rule-unknown', enabled: false });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
