import ky, { type KyInstance, type Options, HTTPError } from 'ky';
import { API_BASE_URL } from './constants';

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
    afterResponse: [
      async (_request, _options, response) => {
        if (response.status === 401) {
          // Token expired or missing — redirect to login (skip if already there)
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
