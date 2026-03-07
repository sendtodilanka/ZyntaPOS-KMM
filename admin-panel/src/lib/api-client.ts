import ky, { type KyInstance, type Options } from 'ky';
import { API_BASE_URL, CF_COOKIE_NAME } from './constants';

function getAuthHeaders(): Record<string, string> {
  const cookies = document.cookie.split(';');
  const cfCookie = cookies
    .map((c) => c.trim())
    .find((c) => c.startsWith(`${CF_COOKIE_NAME}=`));
  const cfToken = cfCookie ? cfCookie.split('=').slice(1).join('=') : '';

  const headers: Record<string, string> = {};
  if (cfToken) {
    headers['CF-Access-Token'] = cfToken;
  }
  return headers;
}

let adminToken: string | null = null;
let adminTokenExpiry: number | null = null;

export function setAdminToken(token: string, expiresInSeconds: number) {
  adminToken = token;
  adminTokenExpiry = Date.now() + expiresInSeconds * 1000;
}

export function clearAdminToken() {
  adminToken = null;
  adminTokenExpiry = null;
}

function isAdminTokenValid(): boolean {
  if (!adminToken || !adminTokenExpiry) return false;
  return Date.now() < adminTokenExpiry - 30_000; // 30s buffer
}

const baseOptions: Options = {
  prefixUrl: API_BASE_URL,
  timeout: 30_000,
  retry: {
    limit: 2,
    methods: ['get'],
    statusCodes: [408, 429, 500, 502, 503, 504],
    backoffLimit: 4_000,
  },
  hooks: {
    beforeRequest: [
      (request) => {
        const authHeaders = getAuthHeaders();
        Object.entries(authHeaders).forEach(([k, v]) => request.headers.set(k, v));

        if (isAdminTokenValid() && adminToken) {
          request.headers.set('Authorization', `Bearer ${adminToken}`);
        }
      },
    ],
    afterResponse: [
      async (_request, _options, response) => {
        if (response.status === 401) {
          clearAdminToken();
          window.location.href = '/';
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
