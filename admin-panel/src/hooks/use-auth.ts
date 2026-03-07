import { CF_COOKIE_NAME } from '@/lib/constants';

export interface AdminUser {
  email: string;
  name: string;
  role: 'ADMIN' | 'SUPPORT' | 'VIEWER';
  avatarUrl?: string;
  sub?: string;
}

interface CfJwtPayload {
  email?: string;
  name?: string;
  sub?: string;
  picture?: string;
  'custom:role'?: string;
}

function decodeCfJwt(token: string): CfJwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const rem = payload.length % 4;
    const padded = rem > 0 ? payload + '='.repeat(4 - rem) : payload;
    const decoded = atob(padded);
    return JSON.parse(decoded) as CfJwtPayload;
  } catch {
    return null;
  }
}

function getCfToken(): string | null {
  const cookies = document.cookie.split(';');
  const cfCookie = cookies
    .map((c) => c.trim())
    .find((c) => c.startsWith(`${CF_COOKIE_NAME}=`));
  return cfCookie ? cfCookie.split('=').slice(1).join('=') : null;
}

const ROLE_PERMISSIONS: Record<string, string[]> = {
  ADMIN: ['license:write', 'license:read', 'store:write', 'store:read', 'user:write', 'user:read', 'audit:read', 'sync:write', 'sync:read', 'config:write', 'config:read', 'reports:read', 'health:read', 'alerts:write', 'alerts:read'],
  SUPPORT: ['license:read', 'store:read', 'user:read', 'audit:read', 'sync:write', 'sync:read', 'config:read', 'reports:read', 'health:read', 'alerts:read'],
  VIEWER: ['license:read', 'store:read', 'audit:read', 'reports:read', 'health:read'],
};

function resolveUser(): AdminUser | null {
  const token = getCfToken();
  if (!token) {
    if (import.meta.env.DEV) {
      return { email: 'dev@zyntapos.com', name: 'Dev Admin', role: 'ADMIN' };
    }
    return null;
  }
  const payload = decodeCfJwt(token);
  if (!payload) return null;
  const rawRole = payload['custom:role'] ?? 'VIEWER';
  const role = ['ADMIN', 'SUPPORT', 'VIEWER'].includes(rawRole)
    ? (rawRole as AdminUser['role'])
    : 'VIEWER';
  return {
    email: payload.email ?? '',
    name: payload.name ?? payload.email ?? 'Admin',
    role,
    avatarUrl: payload.picture,
    sub: payload.sub,
  };
}

export function useAuth() {
  const user = resolveUser();

  const hasPermission = (permission: string): boolean => {
    if (!user) return false;
    return ROLE_PERMISSIONS[user.role]?.includes(permission) ?? false;
  };

  const signOut = () => {
    document.cookie = `${CF_COOKIE_NAME}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    window.location.href = '/';
  };

  return { user, isAuthenticated: !!user, hasPermission, signOut, ...user };
}
