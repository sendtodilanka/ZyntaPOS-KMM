import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import {
  useFeatureFlags,
  useUpdateFeatureFlag,
  useTaxRates,
  useCreateTaxRate,
  useUpdateTaxRate,
  useDeleteTaxRate,
  useSystemConfig,
  useUpdateSystemConfig,
  configKeys,
} from '@/api/config';
import type { FeatureFlag, TaxRate, SystemConfig } from '@/types/config';

const API_BASE = 'https://api.zyntapos.com';

const mockFeatureFlag: FeatureFlag = {
  key: 'multi_store',
  name: 'Multi-Store Support',
  enabled: true,
  description: 'Enable multi-store support',
  category: 'store',
  editionsAvailable: ['PROFESSIONAL', 'ENTERPRISE'],
  lastModified: new Date().toISOString(),
  modifiedBy: 'admin@zyntapos.com',
};

const mockTaxRate: TaxRate = {
  id: 'tax-1',
  name: 'VAT',
  rate: 15,
  description: 'Value Added Tax',
  applicableTo: ['products', 'services'],
  isDefault: true,
  country: 'LK',
  active: true,
};

const mockSystemConfig: SystemConfig = {
  key: 'max_login_attempts',
  value: '5',
  type: 'string',
  description: 'Maximum failed login attempts before lockout',
  category: 'security',
  editable: true,
  sensitive: false,
};

// ── configKeys ───────────────────────────────────────────────────────────────

describe('configKeys', () => {
  it('all is ["config"]', () => {
    expect(configKeys.all).toEqual(['config']);
  });

  it('flags() returns ["config", "flags"]', () => {
    expect(configKeys.flags()).toEqual(['config', 'flags']);
  });

  it('taxRates() returns ["config", "tax-rates"]', () => {
    expect(configKeys.taxRates()).toEqual(['config', 'tax-rates']);
  });

  it('system() returns ["config", "system"]', () => {
    expect(configKeys.system()).toEqual(['config', 'system']);
  });
});

// ── useFeatureFlags ──────────────────────────────────────────────────────────

describe('useFeatureFlags', () => {
  it('fetches all feature flags', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/feature-flags`, () =>
        HttpResponse.json([mockFeatureFlag]),
      ),
    );

    const { result } = renderHook(() => useFeatureFlags(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].key).toBe('multi_store');
    expect(result.current.data![0].enabled).toBe(true);
  });

  it('returns an empty array when no flags are configured', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/feature-flags`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useFeatureFlags(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/feature-flags`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useFeatureFlags(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateFeatureFlag ─────────────────────────────────────────────────────

describe('useUpdateFeatureFlag', () => {
  it('patches the correct flag key URL with { enabled } in the body', async () => {
    let capturedUrl = '';
    let requestBody: Record<string, unknown> = {};
    const updatedFlag: FeatureFlag = { ...mockFeatureFlag, enabled: false };

    server.use(
      http.patch(`${API_BASE}/admin/config/feature-flags/:key`, async ({ request }) => {
        capturedUrl = request.url;
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(updatedFlag);
      }),
    );

    const { result } = renderHook(() => useUpdateFeatureFlag(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const returned = await result.current.mutateAsync({ key: 'multi_store', enabled: false });
      expect(returned).toEqual(updatedFlag);
    });

    expect(capturedUrl).toContain('/admin/config/feature-flags/multi_store');
    expect(requestBody['enabled']).toBe(false);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('can enable a previously disabled flag', async () => {
    let requestBody: Record<string, unknown> = {};

    server.use(
      http.patch(`${API_BASE}/admin/config/feature-flags/:key`, async ({ request }) => {
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...mockFeatureFlag, enabled: true });
      }),
    );

    const { result } = renderHook(() => useUpdateFeatureFlag(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync({ key: 'multi_store', enabled: true });
    });

    expect(requestBody['enabled']).toBe(true);
  });

  it('invalidates configKeys.flags() query key on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/feature-flags`, () =>
        HttpResponse.json([mockFeatureFlag]),
      ),
      http.patch(`${API_BASE}/admin/config/feature-flags/:key`, () =>
        HttpResponse.json({ ...mockFeatureFlag, enabled: false }),
      ),
    );

    const wrapper = createWrapper();

    const { result: flagsResult } = renderHook(() => useFeatureFlags(), { wrapper });
    await waitFor(() => expect(flagsResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useUpdateFeatureFlag(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/config/feature-flags`, () => {
        refetchCount++;
        return HttpResponse.json([{ ...mockFeatureFlag, enabled: false }]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync({ key: 'multi_store', enabled: false });
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces server errors', async () => {
    server.use(
      http.patch(`${API_BASE}/admin/config/feature-flags/:key`, () =>
        HttpResponse.json({ message: 'Flag not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useUpdateFeatureFlag(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ key: 'unknown_flag', enabled: false });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useTaxRates ──────────────────────────────────────────────────────────────

describe('useTaxRates', () => {
  it('fetches all tax rates', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () =>
        HttpResponse.json([mockTaxRate]),
      ),
    );

    const { result } = renderHook(() => useTaxRates(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].id).toBe('tax-1');
    expect(result.current.data![0].rate).toBe(15);
  });

  it('returns an empty array when no tax rates are configured', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useTaxRates(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useTaxRates(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useCreateTaxRate ─────────────────────────────────────────────────────────

describe('useCreateTaxRate', () => {
  it('creates a new tax rate and returns it', async () => {
    server.use(
      http.post(`${API_BASE}/admin/config/tax-rates`, () =>
        HttpResponse.json(mockTaxRate, { status: 201 }),
      ),
    );

    const { result } = renderHook(() => useCreateTaxRate(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const created = await result.current.mutateAsync({
        name: 'VAT',
        rate: 15,
        description: 'Value Added Tax',
        applicableTo: ['products'],
        isDefault: true,
        country: 'LK',
      });
      expect(created).toEqual(mockTaxRate);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('invalidates configKeys.taxRates() on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () =>
        HttpResponse.json([mockTaxRate]),
      ),
      http.post(`${API_BASE}/admin/config/tax-rates`, () =>
        HttpResponse.json(mockTaxRate, { status: 201 }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useTaxRates(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useCreateTaxRate(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () => {
        refetchCount++;
        return HttpResponse.json([mockTaxRate]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync({
        name: 'VAT',
        rate: 15,
        description: 'Value Added Tax',
        applicableTo: ['products'],
        isDefault: true,
        country: 'LK',
      });
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces validation errors', async () => {
    server.use(
      http.post(`${API_BASE}/admin/config/tax-rates`, () =>
        HttpResponse.json({ message: 'Validation failed' }, { status: 422 }),
      ),
    );

    const { result } = renderHook(() => useCreateTaxRate(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ name: '', rate: -1, description: '', applicableTo: [], isDefault: false, country: '' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateTaxRate ─────────────────────────────────────────────────────────

describe('useUpdateTaxRate', () => {
  it('sends PUT to the correct URL and returns the updated tax rate', async () => {
    let capturedUrl = '';
    const updatedTaxRate: TaxRate = { ...mockTaxRate, rate: 20 };

    server.use(
      http.put(`${API_BASE}/admin/config/tax-rates/:id`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(updatedTaxRate);
      }),
    );

    const { result } = renderHook(() => useUpdateTaxRate(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const returned = await result.current.mutateAsync({ id: 'tax-1', rate: 20 });
      expect(returned).toEqual(updatedTaxRate);
    });

    expect(capturedUrl).toContain('/admin/config/tax-rates/tax-1');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('invalidates configKeys.taxRates() on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () =>
        HttpResponse.json([mockTaxRate]),
      ),
      http.put(`${API_BASE}/admin/config/tax-rates/:id`, () =>
        HttpResponse.json({ ...mockTaxRate, rate: 20 }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useTaxRates(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useUpdateTaxRate(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () => {
        refetchCount++;
        return HttpResponse.json([{ ...mockTaxRate, rate: 20 }]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync({ id: 'tax-1', rate: 20 });
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces 404 errors for unknown id', async () => {
    server.use(
      http.put(`${API_BASE}/admin/config/tax-rates/:id`, () =>
        HttpResponse.json({ message: 'Tax rate not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useUpdateTaxRate(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ id: 'tax-unknown', rate: 10 });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeleteTaxRate ─────────────────────────────────────────────────────────

describe('useDeleteTaxRate', () => {
  it('sends DELETE to the correct tax rate URL', async () => {
    let capturedUrl = '';
    server.use(
      http.delete(`${API_BASE}/admin/config/tax-rates/:id`, ({ request }) => {
        capturedUrl = request.url;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const { result } = renderHook(() => useDeleteTaxRate(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync('tax-1');
    });

    expect(capturedUrl).toContain('/admin/config/tax-rates/tax-1');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('invalidates configKeys.taxRates() on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () =>
        HttpResponse.json([mockTaxRate]),
      ),
      http.delete(`${API_BASE}/admin/config/tax-rates/:id`, () =>
        new HttpResponse(null, { status: 204 }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useTaxRates(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useDeleteTaxRate(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/config/tax-rates`, () => {
        refetchCount++;
        return HttpResponse.json([]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync('tax-1');
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces server errors', async () => {
    server.use(
      http.delete(`${API_BASE}/admin/config/tax-rates/:id`, () =>
        HttpResponse.json({ message: 'Cannot delete default tax rate' }, { status: 409 }),
      ),
    );

    const { result } = renderHook(() => useDeleteTaxRate(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate('tax-1');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSystemConfig ──────────────────────────────────────────────────────────

describe('useSystemConfig', () => {
  it('fetches all system config entries', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/system`, () =>
        HttpResponse.json([mockSystemConfig]),
      ),
    );

    const { result } = renderHook(() => useSystemConfig(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].key).toBe('max_login_attempts');
    expect(result.current.data![0].value).toBe('5');
  });

  it('returns an empty array when no config is available', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/system`, () => HttpResponse.json([])),
    );

    const { result } = renderHook(() => useSystemConfig(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/system`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useSystemConfig(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateSystemConfig ────────────────────────────────────────────────────

describe('useUpdateSystemConfig', () => {
  it('patches the correct config key URL with { value } in the body', async () => {
    let capturedUrl = '';
    let requestBody: Record<string, unknown> = {};
    const updatedConfig: SystemConfig = { ...mockSystemConfig, value: '10' };

    server.use(
      http.patch(`${API_BASE}/admin/config/system/:key`, async ({ request }) => {
        capturedUrl = request.url;
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(updatedConfig);
      }),
    );

    const { result } = renderHook(() => useUpdateSystemConfig(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      const returned = await result.current.mutateAsync({
        key: 'max_login_attempts',
        value: '10',
      });
      expect(returned).toEqual(updatedConfig);
    });

    expect(capturedUrl).toContain('/admin/config/system/max_login_attempts');
    expect(requestBody['value']).toBe('10');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('supports updating a config key with a numeric value', async () => {
    let requestBody: Record<string, unknown> = {};

    server.use(
      http.patch(`${API_BASE}/admin/config/system/:key`, async ({ request }) => {
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...mockSystemConfig, value: '30' });
      }),
    );

    const { result } = renderHook(() => useUpdateSystemConfig(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.mutateAsync({ key: 'session_timeout_minutes', value: '30' });
    });

    expect(requestBody['value']).toBe('30');
  });

  it('invalidates configKeys.system() on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/config/system`, () =>
        HttpResponse.json([mockSystemConfig]),
      ),
      http.patch(`${API_BASE}/admin/config/system/:key`, () =>
        HttpResponse.json({ ...mockSystemConfig, value: '10' }),
      ),
    );

    const wrapper = createWrapper();

    const { result: configResult } = renderHook(() => useSystemConfig(), { wrapper });
    await waitFor(() => expect(configResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useUpdateSystemConfig(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/config/system`, () => {
        refetchCount++;
        return HttpResponse.json([{ ...mockSystemConfig, value: '10' }]);
      }),
    );

    await act(async () => {
      await mutResult.current.mutateAsync({ key: 'max_login_attempts', value: '10' });
    });

    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces server errors', async () => {
    server.use(
      http.patch(`${API_BASE}/admin/config/system/:key`, () =>
        HttpResponse.json({ message: 'Config key not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useUpdateSystemConfig(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ key: 'unknown_config', value: 'test' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
