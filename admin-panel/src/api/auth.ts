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
  mfaRequired?: boolean;
}

export interface MfaPendingResponse {
  mfaRequired: true;
  pendingToken: string;
}

export type LoginResponse = AdminLoginResponse | MfaPendingResponse;

export interface MfaSetupResponse {
  secret: string;
  qrCodeUrl: string;
  backupCodes: string[];
}

export interface MfaEnableRequest {
  secret: string;
  code: string;
}

export interface MfaVerifyRequest {
  code: string;
  pendingToken: string;
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
    refetchOnWindowFocus: false,
    throwOnError: false,
  });
}

// ── POST /admin/auth/login ────────────────────────────────────────────────

export function useAdminLogin() {
  const { setUser } = useAuthStore();

  return useMutation({
    mutationFn: (data: AdminLoginRequest) =>
      apiClient.post('admin/auth/login', { json: data }).json<LoginResponse>(),
    onSuccess: (response) => {
      if (!('pendingToken' in response)) {
        setUser(response.user);
      }
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
    staleTime: Infinity,   // Bootstrap state never changes mid-session
    gcTime: Infinity,
    refetchOnWindowFocus: false,
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

// ── MFA hooks ─────────────────────────────────────────────────────────────

export function useAdminMfaSetup() {
  return useMutation({
    mutationFn: () => apiClient.post('admin/auth/mfa/setup').json<MfaSetupResponse>(),
  });
}

export function useAdminMfaEnable() {
  return useMutation({
    mutationFn: (data: MfaEnableRequest) =>
      apiClient.post('admin/auth/mfa/enable', { json: data }).json(),
    onSuccess: () => toast.success('MFA enabled', 'Two-factor authentication is now active.'),
    onError: () => toast.error('Failed to enable MFA', 'Invalid code. Try again.'),
  });
}

export function useAdminMfaDisable() {
  return useMutation({
    mutationFn: (code: string) =>
      apiClient.post('admin/auth/mfa/disable', { json: { code } }),
    onSuccess: () => toast.success('MFA disabled'),
    onError: () => toast.error('Failed to disable MFA', 'Invalid code. Try again.'),
  });
}

export function useAdminMfaVerify() {
  const { setUser } = useAuthStore();

  return useMutation({
    mutationFn: (data: MfaVerifyRequest) =>
      apiClient.post('admin/auth/mfa/verify', { json: data }).json<AdminLoginResponse>(),
    onSuccess: (response) => {
      setUser(response.user);
    },
  });
}

// ── Change password ───────────────────────────────────────────────────────

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (data: ChangePasswordRequest) =>
      apiClient.post('admin/auth/change-password', { json: data }),
    onSuccess: () => toast.success('Password changed', 'You have been logged out of all other sessions.'),
    onError: () => toast.error('Failed to change password', 'Check your current password and try again.'),
  });
}

// ── Active sessions ───────────────────────────────────────────────────────

export interface AdminSession {
  id: string;
  userAgent: string | null;
  ipAddress: string | null;
  createdAt: number;
  expiresAt: number;
}

export function useListSessions(userId: string | undefined) {
  return useQuery({
    queryKey: ['users', userId, 'sessions'],
    queryFn: () => apiClient.get(`admin/users/${userId}/sessions`).json<AdminSession[]>(),
    enabled: !!userId,
    staleTime: 30_000,
  });
}
