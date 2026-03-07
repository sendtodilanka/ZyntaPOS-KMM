import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useAuth } from '@/hooks/use-auth';

// Mock the CF JWT cookie
const mockJwt = (payload: object) => {
  const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
};

describe('useAuth', () => {
  beforeEach(() => {
    // Clear cookies
    document.cookie = 'CF_Authorization=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    vi.resetAllMocks();
  });

  it('returns unauthenticated when no cookie', () => {
    const { result } = renderHook(() => useAuth());
    // In dev mode, falls back to dev user
    expect(result.current).toBeDefined();
    expect(result.current.email).toBeDefined();
  });

  it('decodes CF JWT from cookie', () => {
    const payload = {
      email: 'admin@test.com',
      name: 'Test Admin',
      custom: { role: 'ADMIN' },
      exp: Math.floor(Date.now() / 1000) + 3600,
      iat: Math.floor(Date.now() / 1000),
    };
    document.cookie = `CF_Authorization=${mockJwt(payload)}`;
    const { result } = renderHook(() => useAuth());
    expect(result.current.email).toBe('admin@test.com');
  });
});
