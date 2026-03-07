import { http, HttpResponse } from 'msw';

const API_BASE = 'https://api.zyntapos.com';
const LICENSE_BASE = 'https://license.zyntapos.com';

export const handlers = [
  http.get(`${API_BASE}/admin/metrics/dashboard`, () => {
    return HttpResponse.json({
      totalStores: 42,
      totalStoresTrend: 5.2,
      activeLicenses: 38,
      activeLicensesTrend: 2.1,
      revenueToday: 125000,
      revenueTodayTrend: -3.4,
      syncHealthPercent: 95.8,
      syncHealthTrend: 1.2,
      currency: 'LKR',
    });
  }),

  http.get(`${LICENSE_BASE}/admin/licenses`, () => {
    return HttpResponse.json({
      data: [],
      page: 0,
      size: 20,
      total: 0,
      totalPages: 0,
    });
  }),

  http.get(`${API_BASE}/admin/stores`, () => {
    return HttpResponse.json({
      data: [],
      page: 0,
      size: 20,
      total: 0,
      totalPages: 0,
    });
  }),

  http.get(`${API_BASE}/admin/health/services`, () => {
    return HttpResponse.json([]);
  }),
];
