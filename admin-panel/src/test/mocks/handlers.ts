import { http, HttpResponse } from 'msw';

const API_BASE = 'http://localhost:3000/api';

export const handlers = [
  // Dashboard KPIs
  http.get(`${API_BASE}/admin/metrics/kpis`, () => {
    return HttpResponse.json({
      totalRevenue: 4500000,
      revenueChange: 12.5,
      activeStores: 8,
      storesChange: 2,
      activeLicenses: 12,
      licensesChange: 1,
      syncHealth: 98.2,
      syncHealthChange: -0.3,
    });
  }),

  // Licenses
  http.get(`${API_BASE}/admin/licenses`, () => {
    return HttpResponse.json({
      items: [
        {
          licenseKey: 'ZYNTA-ABCD-1234-EFGH',
          storeId: 'store-1',
          storeName: 'Colombo Store',
          edition: 'PROFESSIONAL',
          status: 'ACTIVE',
          expiresAt: '2026-12-31T00:00:00Z',
          activatedAt: '2024-01-01T00:00:00Z',
          deviceCount: 3,
          maxDevices: 5,
          features: ['pos', 'inventory', 'reports'],
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    });
  }),

  // Stores
  http.get(`${API_BASE}/admin/stores`, () => {
    return HttpResponse.json({
      items: [
        {
          storeId: 'store-1',
          name: 'Colombo Store',
          address: '123 Main St, Colombo',
          phone: '+94711234567',
          email: 'colombo@example.com',
          status: 'ACTIVE',
          licenseKey: 'ZYNTA-ABCD-1234-EFGH',
          timezone: 'Asia/Colombo',
          currency: 'LKR',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-06-01T00:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    });
  }),

  // Users
  http.get(`${API_BASE}/admin/users`, () => {
    return HttpResponse.json({
      items: [
        {
          id: 'user-1',
          username: 'admin',
          email: 'admin@zyntapos.com',
          fullName: 'System Admin',
          role: 'ADMIN',
          storeId: null,
          active: true,
          lastLogin: new Date().toISOString(),
          createdAt: '2024-01-01T00:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    });
  }),

  // Health - System
  http.get(`${API_BASE}/admin/health/system`, () => {
    return HttpResponse.json({
      overall: 'healthy',
      checkedAt: new Date().toISOString(),
      services: [
        { name: 'API Server', status: 'healthy', latencyMs: 45, uptime: 99.9, lastChecked: new Date().toISOString(), version: '1.0.0' },
        { name: 'Database', status: 'healthy', latencyMs: 12, uptime: 99.99, lastChecked: new Date().toISOString() },
        { name: 'Redis Cache', status: 'healthy', latencyMs: 3, uptime: 100, lastChecked: new Date().toISOString() },
      ],
    });
  }),

  // Health - Stores
  http.get(`${API_BASE}/admin/health/stores`, () => {
    return HttpResponse.json([
      {
        storeId: 'store-1',
        storeName: 'Colombo Store',
        status: 'healthy',
        lastSync: new Date(Date.now() - 60000).toISOString(),
        pendingOperations: 0,
        appVersion: '1.0.0',
        androidVersion: '14',
        uptimePercent: 99.8,
      },
    ]);
  }),

  // Alerts
  http.get(`${API_BASE}/admin/alerts`, () => {
    return HttpResponse.json({
      items: [],
      total: 0,
      page: 1,
      pageSize: 20,
    });
  }),

  http.get(`${API_BASE}/admin/alerts/counts`, () => {
    return HttpResponse.json({ active: 0, critical: 0 });
  }),

  // Audit
  http.get(`${API_BASE}/admin/audit`, () => {
    return HttpResponse.json({
      items: [],
      total: 0,
      page: 1,
      pageSize: 50,
    });
  }),

  // Feature flags
  http.get(`${API_BASE}/admin/config/feature-flags`, () => {
    return HttpResponse.json([
      {
        key: 'pos.hold_orders',
        name: 'Hold Orders',
        description: 'Allow cashiers to hold orders',
        enabled: true,
        category: 'pos',
        editionsAvailable: ['PROFESSIONAL', 'ENTERPRISE'],
        lastModified: new Date().toISOString(),
        modifiedBy: 'admin',
      },
    ]);
  }),

  // Tax rates
  http.get(`${API_BASE}/admin/config/tax-rates`, () => {
    return HttpResponse.json([
      {
        id: 'tax-1',
        name: 'VAT',
        rate: 15,
        description: 'Value Added Tax',
        applicableTo: ['all'],
        isDefault: true,
        country: 'LK',
        active: true,
      },
    ]);
  }),

  // Sync status
  http.get(`${API_BASE}/admin/sync/status`, () => {
    return HttpResponse.json([
      {
        storeId: 'store-1',
        storeName: 'Colombo Store',
        status: 'synced',
        lastSync: new Date().toISOString(),
        pendingCount: 0,
        failedCount: 0,
        totalSynced: 1542,
        syncVersion: 100,
      },
    ]);
  }),
];
