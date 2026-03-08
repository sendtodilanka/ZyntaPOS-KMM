import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAuth } from '@/hooks/use-auth';
import { useAuthStore } from '@/stores/auth-store';
import type { AdminUser } from '@/types/user';

const mockAdmin: AdminUser = {
  id: 'user-1',
  email: 'admin@zyntasolutions.com',
  name: 'Test Admin',
  role: 'ADMIN',
  mfaEnabled: false,
  isActive: true,
  lastLoginAt: null,
  createdAt: Date.now(),
};

describe('useAuth', () => {
  beforeEach(() => {
    act(() => {
      useAuthStore.getState().clearUser();
    });
  });

  it('returns unauthenticated state when store has no user', () => {
    const { result } = renderHook(() => useAuth());
    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.isAdmin).toBe(false);
  });

  it('returns authenticated state when store has a user', () => {
    act(() => {
      useAuthStore.getState().setUser(mockAdmin);
    });
    const { result } = renderHook(() => useAuth());
    expect(result.current.user).toEqual(mockAdmin);
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.isAdmin).toBe(true);
  });

  it('hasPermission returns true for granted permissions', () => {
    act(() => {
      useAuthStore.getState().setUser(mockAdmin);
    });
    const { result } = renderHook(() => useAuth());
    expect(result.current.hasPermission('dashboard:ops')).toBe(true);
    expect(result.current.hasPermission('users:write')).toBe(true);
    expect(result.current.hasPermission('audit:read')).toBe(true);
  });

  it('hasPermission returns false for denied permissions', () => {
    act(() => {
      useAuthStore.getState().setUser({ ...mockAdmin, role: 'AUDITOR' });
    });
    const { result } = renderHook(() => useAuth());
    expect(result.current.hasPermission('users:write')).toBe(false);
    expect(result.current.hasPermission('license:revoke')).toBe(false);
  });

  it('hasPermission returns false when unauthenticated', () => {
    const { result } = renderHook(() => useAuth());
    expect(result.current.hasPermission('dashboard:ops')).toBe(false);
  });

  it('FINANCE role has financial permissions only', () => {
    act(() => {
      useAuthStore.getState().setUser({ ...mockAdmin, role: 'FINANCE' });
    });
    const { result } = renderHook(() => useAuth());
    expect(result.current.hasPermission('dashboard:financial')).toBe(true);
    expect(result.current.hasPermission('reports:financial')).toBe(true);
    expect(result.current.hasPermission('dashboard:ops')).toBe(false);
    expect(result.current.hasPermission('tickets:create')).toBe(false);
  });

  it('HELPDESK role has support permissions', () => {
    act(() => {
      useAuthStore.getState().setUser({ ...mockAdmin, role: 'HELPDESK' });
    });
    const { result } = renderHook(() => useAuth());
    expect(result.current.hasPermission('tickets:read')).toBe(true);
    expect(result.current.hasPermission('dashboard:support')).toBe(true);
    expect(result.current.hasPermission('reports:financial')).toBe(false);
  });
});
