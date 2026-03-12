import { describe, it, expect, beforeEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useAuthStore } from '@/stores/auth-store';
import type { AdminUser } from '@/types/user';

const mockUser: AdminUser = {
  id: 'user-1',
  email: 'admin@zyntasolutions.com',
  name: 'Test Admin',
  role: 'ADMIN',
  mfaEnabled: false,
  isActive: true,
  lastLoginAt: null,
  createdAt: new Date('2024-01-01T00:00:00Z').getTime(),
};

const mockOperator: AdminUser = {
  id: 'user-2',
  email: 'operator@zyntasolutions.com',
  name: 'Test Operator',
  role: 'OPERATOR',
  mfaEnabled: true,
  isActive: true,
  lastLoginAt: Date.now(),
  createdAt: new Date('2024-06-01T00:00:00Z').getTime(),
};

describe('useAuthStore', () => {
  beforeEach(() => {
    // Reset to initial state before each test so tests are independent
    act(() => {
      useAuthStore.setState({ user: null });
    });
  });

  describe('initial state', () => {
    it('has null user', () => {
      const { result } = renderHook(() => useAuthStore());
      expect(result.current.user).toBeNull();
    });
  });

  describe('setUser', () => {
    it('sets the user', () => {
      const { result } = renderHook(() => useAuthStore());

      act(() => {
        result.current.setUser(mockUser);
      });

      expect(result.current.user).toEqual(mockUser);
    });

    it('replaces an existing user with a new one', () => {
      act(() => {
        useAuthStore.getState().setUser(mockUser);
      });

      const { result } = renderHook(() => useAuthStore());

      act(() => {
        result.current.setUser(mockOperator);
      });

      expect(result.current.user).toEqual(mockOperator);
      expect(result.current.user?.email).toBe('operator@zyntasolutions.com');
    });

    it('preserves all user fields', () => {
      const { result } = renderHook(() => useAuthStore());

      act(() => {
        result.current.setUser(mockOperator);
      });

      expect(result.current.user?.mfaEnabled).toBe(true);
      expect(result.current.user?.role).toBe('OPERATOR');
      expect(result.current.user?.lastLoginAt).not.toBeNull();
    });
  });

  describe('clearUser', () => {
    it('sets user to null', () => {
      act(() => {
        useAuthStore.getState().setUser(mockUser);
      });

      const { result } = renderHook(() => useAuthStore());

      act(() => {
        result.current.clearUser();
      });

      expect(result.current.user).toBeNull();
    });

    it('is idempotent when called on an already-null store', () => {
      const { result } = renderHook(() => useAuthStore());

      // user is already null from beforeEach reset
      act(() => {
        result.current.clearUser();
      });

      expect(result.current.user).toBeNull();
    });
  });

  describe('getState (direct store access)', () => {
    it('reflects mutations made via getState().setUser', () => {
      act(() => {
        useAuthStore.getState().setUser(mockUser);
      });

      expect(useAuthStore.getState().user).toEqual(mockUser);
    });

    it('reflects mutations made via getState().clearUser', () => {
      act(() => {
        useAuthStore.getState().setUser(mockUser);
        useAuthStore.getState().clearUser();
      });

      expect(useAuthStore.getState().user).toBeNull();
    });
  });
});
