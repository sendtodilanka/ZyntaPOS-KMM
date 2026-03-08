import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import type { AdminUser, UserFilter, CreateUserRequest, UpdateUserRequest } from '@/types/user';
import type { PagedResponse } from '@/types/api';

export function useAdminUsers(filters: UserFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.page !== undefined) qs.set('page', String(filters.page));
  if (filters.size !== undefined) qs.set('size', String(filters.size ?? 20));
  if (filters.role) qs.set('role', filters.role);
  if (filters.status) qs.set('status', filters.status);
  if (filters.search) qs.set('search', filters.search);
  return useQuery({
    queryKey: ['users', filters],
    queryFn: () => apiClient.get(`admin/users?${qs}`).json<PagedResponse<AdminUser>>(),
  });
}

export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateUserRequest) =>
      apiClient.post('admin/users', { json: data }).json<AdminUser>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      toast.success('User created', 'New admin user has been created.');
    },
    onError: () => toast.error('Failed to create user'),
  });
}

export function useUpdateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, data }: { userId: string; data: UpdateUserRequest }) =>
      apiClient.patch(`admin/users/${userId}`, { json: data }).json<AdminUser>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      toast.success('User updated');
    },
    onError: () => toast.error('Failed to update user'),
  });
}

export function useDeactivateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      apiClient.patch(`admin/users/${userId}`, { json: { isActive: false } }).json<AdminUser>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      toast.success('User deactivated');
    },
    onError: () => toast.error('Failed to deactivate user'),
  });
}

export function useRevokeSessions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      apiClient.delete(`admin/users/${userId}/sessions`),
    onSuccess: (_data, userId) => {
      qc.invalidateQueries({ queryKey: ['users', userId, 'sessions'] });
      toast.success('Sessions revoked', 'All active sessions for this user have been terminated.');
    },
    onError: () => toast.error('Failed to revoke sessions'),
  });
}
