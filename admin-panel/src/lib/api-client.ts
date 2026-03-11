import ky, { type KyInstance, type Options, HTTPError } from 'ky';
import { API_BASE_URL } from './constants';

/** Reads the XSRF-TOKEN cookie set by the backend CSRF plugin. */
function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

const STATE_CHANGING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

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
      async (request, _options, response) => {
        if (response.status === 401) {
          // Auth check and login endpoints manage their own 401 flow via the root
          // layout effect — skip hard redirect for those to avoid racing with the
          // soft navigation that __root.tsx performs after clearUser() resolves.
          const isAuthEndpoint = /\/admin\/auth\/(me|login|mfa\/verify)/.test(request.url);
          if (!isAuthEndpoint && !window.location.pathname.startsWith('/login')) {
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
