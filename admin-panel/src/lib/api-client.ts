import ky, { type KyInstance, type Options, HTTPError } from 'ky';
import { API_BASE_URL } from './constants';

/** Reads the XSRF-TOKEN cookie set by the backend CSRF plugin. */
function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

const STATE_CHANGING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

// ── Token refresh queue (S1-1) ───────────────────────────────────────────
// When multiple requests hit 401 simultaneously, only one refresh call fires.
// All other requests wait for the result, then replay automatically.
let refreshPromise: Promise<boolean> | null = null;

const AUTH_ENDPOINTS = /\/admin\/auth\/(me|login|logout|mfa\/verify|refresh|status|bootstrap)/;

async function attemptTokenRefresh(): Promise<boolean> {
  try {
    const res = await ky.post('admin/auth/refresh', {
      prefixUrl: API_BASE_URL,
      credentials: 'include',
      timeout: 10_000,
      retry: 0,
    });
    return res.ok;
  } catch {
    return false;
  }
}

function getRefreshPromise(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = attemptTokenRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

const baseOptions: Options = {
  prefixUrl: API_BASE_URL,
  timeout: 30_000,
  credentials: 'include',   // sends httpOnly admin_access_token + admin_refresh_token cookies
  retry: {
    limit: 2,
    methods: ['get'],
    statusCodes: [408, 429, 500, 502, 503, 504],
    backoffLimit: 4_000,
  },
  hooks: {
    beforeRequest: [
      (request) => {
        // Attach CSRF token on state-changing requests (double-submit cookie pattern)
        if (STATE_CHANGING_METHODS.has(request.method)) {
          const token = getCsrfToken();
          if (token) {
            request.headers.set('X-XSRF-Token', token);
          }
        }
      },
    ],
    afterResponse: [
      async (request, options, response) => {
        if (response.status === 401) {
          // Auth endpoints manage their own 401 flow — skip refresh
          if (AUTH_ENDPOINTS.test(request.url)) {
            return response;
          }

          // Attempt silent token refresh, then replay the original request
          const refreshed = await getRefreshPromise();
          if (refreshed) {
            return ky(request, { ...options, hooks: {} });
          }

          // Refresh failed — redirect to login
          if (!window.location.pathname.startsWith('/login')) {
            window.location.href = '/login';
          }
        }
        return response;
      },
    ],
  },
};

export const apiClient: KyInstance = ky.create(baseOptions);

export const licenseClient: KyInstance = ky.create({
  ...baseOptions,
  prefixUrl: import.meta.env.VITE_LICENSE_URL ?? 'https://license.zyntapos.com',
});

export const syncClient: KyInstance = ky.create({
  ...baseOptions,
  prefixUrl: import.meta.env.VITE_SYNC_URL ?? 'https://sync.zyntapos.com',
});

export { HTTPError };
