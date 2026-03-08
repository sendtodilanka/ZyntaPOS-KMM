import { useQuery, useMutation } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { useAuthStore } from '@/stores/auth-store';
import { toast } from '@/stores/ui-store';
import type { AdminUser } from '@/types/user';

export interface AdminLoginRequest {
  email: string;
  password: string;
}

export interface AdminLoginResponse {
  user: AdminUser;
  expiresIn: number;
  mfaRequired: boolean;
}

export interface AdminStatusResponse {
  needsBootstrap: boolean;
}

export interface AdminBootstrapRequest {
  email: string;
  name: string;
  password: string;
}

// ── GET /admin/auth/me ────────────────────────────────────────────────────
// Called on every page load to hydrate auth state from the httpOnly cookie.
// If the cookie is missing or expired, the request returns 401.

export function useCurrentUser() {
  const { setUser, clearUser } = useAuthStore();

  return useQuery({
    queryKey: ['admin', 'me'],
    queryFn: async () => {
      try {
        const user = await apiClient.get('admin/auth/me').json<AdminUser>();
        setUser(user);
        return user;
      } catch (error) {
        // On 401/network error, clear isLoading so the spinner doesn't hang forever
        clearUser();
        throw error;
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000,   // 5 min — backend cookie is 15 min
    gcTime: 10 * 60 * 1000,
    throwOnError: false,
  });
}

// ── POST /admin/auth/login ────────────────────────────────────────────────

export function useAdminLogin() {
  const { setUser } = useAuthStore();

  return useMutation({
    mutationFn: (data: AdminLoginRequest) =>
      apiClient.post('admin/auth/login', { json: data }).json<AdminLoginResponse>(),
    onSuccess: (response) => {
      setUser(response.user);
    },
    onError: () => {
      // Error handling done in the login form component
    },
  });
}

// ── GET /admin/auth/status ────────────────────────────────────────────────
// Called before auth check to detect first-run bootstrap state.

export function useAdminStatus() {
  return useQuery({
    queryKey: ['admin', 'status'],
    queryFn: () => apiClient.get('admin/auth/status').json<AdminStatusResponse>(),
    retry: false,
    staleTime: 60 * 1000,
  });
}

// ── POST /admin/auth/bootstrap ────────────────────────────────────────────

export function useAdminBootstrap() {
  return useMutation({
    mutationFn: (data: AdminBootstrapRequest) =>
      apiClient.post('admin/auth/bootstrap', { json: data }).json<AdminUser>(),
  });
}

// ── POST /admin/auth/logout ───────────────────────────────────────────────

export function useAdminLogout() {
  const { clearUser } = useAuthStore();

  return useMutation({
    mutationFn: () => apiClient.post('admin/auth/logout').json(),
    onSuccess: () => {
      clearUser();
      window.location.href = '/login';
    },
    onError: () => {
      // Force local clear even if server logout fails
      clearUser();
      toast.error('Logout failed', 'Session cleared locally.');
      window.location.href = '/login';
    },
  });
}
