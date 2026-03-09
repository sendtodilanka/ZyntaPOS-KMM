import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import {
  useLicenses,
  useLicense,
  useLicenseStats,
  useLicenseDevices,
  useCreateLicense,
  useUpdateLicense,
  useRevokeLicense,
  useDeregisterDevice,
} from '@/api/licenses';
import type { License, LicenseDevice, LicenseStats } from '@/types/license';
import type { PagedResponse } from '@/types/api';

// licenseClient uses VITE_LICENSE_URL ?? 'https://license.zyntapos.com'
const LICENSE_BASE = 'https://license.zyntapos.com';

const mockLicense: License = {
  id: 'lic-1',
  key: 'ZYNTA-ABCD-1234-EFGH',
  customerId: 'cust-1',
  customerName: 'Colombo Store',
  edition: 'PROFESSIONAL',
  status: 'ACTIVE',
  maxDevices: 5,
  activeDevices: 3,
  activatedAt: '2024-01-01T00:00:00Z',
  expiresAt: '2026-12-31T00:00:00Z',
  lastHeartbeatAt: '2024-06-01T12:00:00Z',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-06-01T00:00:00Z',
};

const mockPagedLicenses: PagedResponse<License> = {
  data: [mockLicense],
  total: 1,
  page: 1,
  size: 20,
  totalPages: 1,
};

const mockDevice: LicenseDevice = {
  id: 'dev-1',
  licenseKey: 'ZYNTA-ABCD-1234-EFGH',
  deviceId: 'android-device-001',
  deviceName: 'Samsung Galaxy Tab S9',
  appVersion: '1.0.0',
  os: 'Android',
  osVersion: '14',
  firstSeenAt: '2024-01-15T00:00:00Z',
  lastSeenAt: '2024-06-01T12:00:00Z',
};

const mockStats: LicenseStats = {
  total: 10,
  active: 7,
  expired: 1,
  revoked: 1,
  suspended: 0,
  expiringSoon: 1,
  byEdition: { STARTER: 2, PROFESSIONAL: 5, ENTERPRISE: 3 },
};

// ── useLicenses ──────────────────────────────────────────────────────────────

describe('useLicenses', () => {
  it('fetches the first page of licenses with no filters', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, () => HttpResponse.json(mockPagedLicenses)),
    );

    const { result } = renderHook(() => useLicenses(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].key).toBe('ZYNTA-ABCD-1234-EFGH');
  });

  it('forwards status filter query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedLicenses, data: [] });
      }),
    );

    const { result } = renderHook(() => useLicenses({ status: 'EXPIRED' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('status=EXPIRED');
  });

  it('forwards edition filter query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedLicenses, data: [] });
      }),
    );

    const { result } = renderHook(() => useLicenses({ edition: 'ENTERPRISE' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('edition=ENTERPRISE');
  });

  it('forwards search query param', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedLicenses, data: [] });
      }),
    );

    const { result } = renderHook(() => useLicenses({ search: 'colombo' }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('search=colombo');
  });

  it('forwards page and size query params', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedLicenses, data: [] });
      }),
    );

    const { result } = renderHook(() => useLicenses({ page: 2, size: 50 }), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('page=2');
    expect(capturedUrl).toContain('size=50');
  });

  it('forwards multiple filters simultaneously', async () => {
    let capturedUrl = '';
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ ...mockPagedLicenses, data: [] });
      }),
    );

    const { result } = renderHook(
      () =>
        useLicenses({
          status: 'ACTIVE',
          edition: 'PROFESSIONAL',
          search: 'galle',
          page: 1,
          size: 10,
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('status=ACTIVE');
    expect(capturedUrl).toContain('edition=PROFESSIONAL');
    expect(capturedUrl).toContain('search=galle');
    expect(capturedUrl).toContain('page=1');
    expect(capturedUrl).toContain('size=10');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useLicenses(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useLicense ───────────────────────────────────────────────────────────────

describe('useLicense', () => {
  it('fetches a single license by key', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses/:key`, () => HttpResponse.json(mockLicense)),
    );

    const { result } = renderHook(() => useLicense('ZYNTA-ABCD-1234-EFGH'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockLicense);
  });

  it('does NOT fetch when key is an empty string', () => {
    const { result } = renderHook(() => useLicense(''), { wrapper: createWrapper() });

    // enabled: !!key — should remain idle
    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoading).toBe(false);
  });

  it('surfaces 404 for an unknown key', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses/:key`, () =>
        HttpResponse.json({ message: 'License not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useLicense('ZYNTA-XXXX-9999-ZZZZ'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useLicenseStats ──────────────────────────────────────────────────────────

describe('useLicenseStats', () => {
  it('fetches license stats', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses/stats`, () => HttpResponse.json(mockStats)),
    );

    const { result } = renderHook(() => useLicenseStats(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockStats);
    expect(result.current.data?.total).toBe(10);
    expect(result.current.data?.active).toBe(7);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses/stats`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useLicenseStats(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useLicenseDevices ────────────────────────────────────────────────────────

describe('useLicenseDevices', () => {
  it('fetches devices for a given license key', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses/:key/devices`, () =>
        HttpResponse.json([mockDevice]),
      ),
    );

    const { result } = renderHook(() => useLicenseDevices('ZYNTA-ABCD-1234-EFGH'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].deviceId).toBe('android-device-001');
  });

  it('does NOT fetch when key is an empty string', () => {
    const { result } = renderHook(() => useLicenseDevices(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoading).toBe(false);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses/:key/devices`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useLicenseDevices('ZYNTA-ABCD-1234-EFGH'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useCreateLicense ─────────────────────────────────────────────────────────

describe('useCreateLicense', () => {
  it('returns the newly created license on success', async () => {
    server.use(
      http.post(`${LICENSE_BASE}/admin/licenses`, () =>
        HttpResponse.json(mockLicense, { status: 201 }),
      ),
    );

    const { result } = renderHook(() => useCreateLicense(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({
        customerId: 'cust-1',
        edition: 'PROFESSIONAL',
        maxDevices: 5,
        expiresAt: '2026-12-31T00:00:00Z',
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockLicense);
  });

  it('invalidates [licenses] cache on success', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, () => HttpResponse.json(mockPagedLicenses)),
      http.post(`${LICENSE_BASE}/admin/licenses`, () =>
        HttpResponse.json(mockLicense, { status: 201 }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useLicenses(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useCreateLicense(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, () => {
        refetchCount++;
        return HttpResponse.json(mockPagedLicenses);
      }),
    );

    act(() => {
      mutResult.current.mutate({
        customerId: 'cust-2',
        edition: 'STARTER',
        maxDevices: 2,
      });
    });

    await waitFor(() => expect(mutResult.current.isSuccess).toBe(true));
    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces validation errors', async () => {
    server.use(
      http.post(`${LICENSE_BASE}/admin/licenses`, () =>
        HttpResponse.json({ message: 'Validation failed' }, { status: 422 }),
      ),
    );

    const { result } = renderHook(() => useCreateLicense(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ customerId: '', edition: 'STARTER', maxDevices: 0 });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateLicense ─────────────────────────────────────────────────────────

describe('useUpdateLicense', () => {
  it('sends PUT to the correct URL and returns the updated license', async () => {
    let capturedUrl = '';
    const updatedLicense: License = { ...mockLicense, maxDevices: 10 };

    server.use(
      http.put(`${LICENSE_BASE}/admin/licenses/:key`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(updatedLicense);
      }),
    );

    const { result } = renderHook(() => useUpdateLicense(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ key: 'ZYNTA-ABCD-1234-EFGH', data: { maxDevices: 10 } });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/licenses/ZYNTA-ABCD-1234-EFGH');
    expect(result.current.data).toEqual(updatedLicense);
  });

  it('invalidates [licenses, key] and [licenses] caches on success', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, () => HttpResponse.json(mockPagedLicenses)),
      http.put(`${LICENSE_BASE}/admin/licenses/:key`, () =>
        HttpResponse.json({ ...mockLicense, maxDevices: 10 }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useLicenses(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useUpdateLicense(), { wrapper });

    let listRefetchCount = 0;
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, () => {
        listRefetchCount++;
        return HttpResponse.json(mockPagedLicenses);
      }),
    );

    act(() => {
      mutResult.current.mutate({ key: 'ZYNTA-ABCD-1234-EFGH', data: { maxDevices: 10 } });
    });

    await waitFor(() => expect(mutResult.current.isSuccess).toBe(true));
    await waitFor(() => expect(listRefetchCount).toBeGreaterThan(0));
  });

  it('surfaces server errors', async () => {
    server.use(
      http.put(`${LICENSE_BASE}/admin/licenses/:key`, () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useUpdateLicense(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ key: 'ZYNTA-XXXX-9999-ZZZZ', data: { maxDevices: 1 } });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useRevokeLicense ─────────────────────────────────────────────────────────

describe('useRevokeLicense', () => {
  it('calls DELETE on the correct URL', async () => {
    let capturedUrl = '';
    server.use(
      http.delete(`${LICENSE_BASE}/admin/licenses/:key`, ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({ revoked: true });
      }),
    );

    const { result } = renderHook(() => useRevokeLicense(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('ZYNTA-ABCD-1234-EFGH');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain('/admin/licenses/ZYNTA-ABCD-1234-EFGH');
  });

  it('invalidates [licenses] cache on success', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, () => HttpResponse.json(mockPagedLicenses)),
      http.delete(`${LICENSE_BASE}/admin/licenses/:key`, () =>
        HttpResponse.json({ revoked: true }),
      ),
    );

    const wrapper = createWrapper();

    const { result: listResult } = renderHook(() => useLicenses(), { wrapper });
    await waitFor(() => expect(listResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useRevokeLicense(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses`, () => {
        refetchCount++;
        return HttpResponse.json(mockPagedLicenses);
      }),
    );

    act(() => {
      mutResult.current.mutate('ZYNTA-ABCD-1234-EFGH');
    });

    await waitFor(() => expect(mutResult.current.isSuccess).toBe(true));
    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces server errors', async () => {
    server.use(
      http.delete(`${LICENSE_BASE}/admin/licenses/:key`, () =>
        HttpResponse.json({ message: 'License already revoked' }, { status: 409 }),
      ),
    );

    const { result } = renderHook(() => useRevokeLicense(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('ZYNTA-ABCD-1234-EFGH');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeregisterDevice ──────────────────────────────────────────────────────

describe('useDeregisterDevice', () => {
  it('calls DELETE on the correct device URL', async () => {
    let capturedUrl = '';
    server.use(
      http.delete(
        `${LICENSE_BASE}/admin/licenses/:key/devices/:deviceId`,
        ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json({ deregistered: true });
        },
      ),
    );

    const { result } = renderHook(() => useDeregisterDevice(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ key: 'ZYNTA-ABCD-1234-EFGH', deviceId: 'android-device-001' });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(capturedUrl).toContain(
      '/admin/licenses/ZYNTA-ABCD-1234-EFGH/devices/android-device-001',
    );
  });

  it('invalidates [licenses, key, devices] cache on success', async () => {
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses/:key/devices`, () =>
        HttpResponse.json([mockDevice]),
      ),
      http.delete(
        `${LICENSE_BASE}/admin/licenses/:key/devices/:deviceId`,
        () => HttpResponse.json({ deregistered: true }),
      ),
    );

    const wrapper = createWrapper();

    const { result: devicesResult } = renderHook(
      () => useLicenseDevices('ZYNTA-ABCD-1234-EFGH'),
      { wrapper },
    );
    await waitFor(() => expect(devicesResult.current.isSuccess).toBe(true));

    const { result: mutResult } = renderHook(() => useDeregisterDevice(), { wrapper });

    let refetchCount = 0;
    server.use(
      http.get(`${LICENSE_BASE}/admin/licenses/:key/devices`, () => {
        refetchCount++;
        return HttpResponse.json([]);
      }),
    );

    act(() => {
      mutResult.current.mutate({ key: 'ZYNTA-ABCD-1234-EFGH', deviceId: 'android-device-001' });
    });

    await waitFor(() => expect(mutResult.current.isSuccess).toBe(true));
    await waitFor(() => expect(refetchCount).toBeGreaterThan(0));
  });

  it('surfaces server errors', async () => {
    server.use(
      http.delete(
        `${LICENSE_BASE}/admin/licenses/:key/devices/:deviceId`,
        () => HttpResponse.json({ message: 'Device not found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useDeregisterDevice(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ key: 'ZYNTA-ABCD-1234-EFGH', deviceId: 'unknown-device' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
