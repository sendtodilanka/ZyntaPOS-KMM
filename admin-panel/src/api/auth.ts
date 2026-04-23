import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { useEffect } from 'react';
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

  // S1-2: Pure queryFn — no side effects inside. Store sync via useEffect below.
  const query = useQuery({
    queryKey: ['admin', 'me'],
    queryFn: () => apiClient.get('admin/auth/me').json<AdminUser>(),
    retry: false,
    staleTime: 60_000,          // S1-11: 1 min (was 5 min) — faster deactivation propagation
    gcTime: 10 * 60 * 1000,
    refetchOnWindowFocus: true,  // S1-11: re-check auth on tab focus
    throwOnError: false,
  });

  // S1-2: Sync query result to Zustand store via useEffect (not inside queryFn)
  useEffect(() => {
    if (query.data) {
      setUser(query.data);
    } else if (query.isError) {
      clearUser();
    }
  }, [query.data, query.isError, setUser, clearUser]);

  return query;
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
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: () => apiClient.post('admin/auth/logout').json(),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['admin'] });
      clearUser();
      navigate({ to: '/login' });
    },
    onError: () => {
      // Force local clear even if server logout fails
      queryClient.removeQueries({ queryKey: ['admin'] });
      clearUser();
      toast.error('Logout failed', 'Session cleared locally.');
      navigate({ to: '/login' });
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

// S1-9: Added .json() to parse response
export function useAdminMfaDisable() {
  return useMutation({
    mutationFn: (code: string) =>
      apiClient.post('admin/auth/mfa/disable', { json: { code } }).json(),
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

// ── Forgot / reset password ───────────────────────────────────────────────

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

// POST /admin/auth/forgot-password — always returns 202 (no email enumeration)
export function useForgotPassword() {
  return useMutation({
    mutationFn: (data: ForgotPasswordRequest) =>
      apiClient.post('admin/auth/forgot-password', { json: data }),
    onError: () => toast.error('Failed to send reset email', 'Please try again in a moment.'),
  });
}

// POST /admin/auth/reset-password — returns 204 on success, 422 on invalid/expired token
export function useResetPassword() {
  return useMutation({
    mutationFn: (data: ResetPasswordRequest) =>
      apiClient.post('admin/auth/reset-password', { json: data }),
    onError: () => toast.error('Failed to reset password', 'The reset link may be invalid or expired.'),
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
