import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { licenseClient } from '@/lib/api-client';
import { toast } from '@/stores/ui-store';
import type {
  License, LicenseDevice, LicenseStats, LicenseFilter, LicenseWithDevices,
  CreateLicenseRequest, UpdateLicenseRequest,
} from '@/types/license';
import type { PagedResponse } from '@/types/api';

export function useLicenses(filters: LicenseFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.page !== undefined) qs.set('page', String(filters.page));
  if (filters.size !== undefined) qs.set('size', String(filters.size ?? 20));
  if (filters.status) qs.set('status', filters.status);
  if (filters.edition) qs.set('edition', filters.edition);
  if (filters.search) qs.set('search', filters.search);
  return useQuery({
    queryKey: ['licenses', filters],
    queryFn: () => licenseClient.get(`admin/licenses?${qs}`).json<PagedResponse<License>>(),
  });
}

/** Fetches a single license together with its registered devices. */
export function useLicense(key: string) {
  return useQuery({
    queryKey: ['licenses', key],
    queryFn: () => licenseClient.get(`admin/licenses/${key}`).json<LicenseWithDevices>(),
    enabled: !!key,
  });
}

export function useLicenseStats() {
  return useQuery({
    queryKey: ['licenses', 'stats'],
    queryFn: () => licenseClient.get('admin/licenses/stats').json<LicenseStats>(),
  });
}

export function useLicenseDevices(key: string) {
  return useQuery({
    queryKey: ['licenses', key, 'devices'],
    queryFn: () => licenseClient.get(`admin/licenses/${key}/devices`).json<LicenseDevice[]>(),
    enabled: !!key,
  });
}

export function useCreateLicense() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateLicenseRequest) =>
      licenseClient.post('admin/licenses', { json: data }).json<License>(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['licenses'] });
      toast.success('License created', 'New license has been issued successfully.');
    },
    onError: () => toast.error('Failed to create license', 'Please check the details and try again.'),
  });
}

export function useUpdateLicense() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ key, data }: { key: string; data: UpdateLicenseRequest }) =>
      licenseClient.put(`admin/licenses/${key}`, { json: data }).json<License>(),
    onSuccess: (_, { key }) => {
      qc.invalidateQueries({ queryKey: ['licenses', key] });
      qc.invalidateQueries({ queryKey: ['licenses'] });
      toast.success('License updated');
    },
    onError: () => toast.error('Failed to update license'),
  });
}

export function useRevokeLicense() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (key: string) => licenseClient.delete(`admin/licenses/${key}`).json(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['licenses'] });
      toast.success('License revoked', 'The license has been revoked.');
    },
    onError: () => toast.error('Failed to revoke license'),
  });
}

export function useDeregisterDevice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ key, deviceId }: { key: string; deviceId: string }) =>
      licenseClient.delete(`admin/licenses/${key}/devices/${deviceId}`).json(),
    onSuccess: (_, { key }) => {
      qc.invalidateQueries({ queryKey: ['licenses', key, 'devices'] });
      qc.invalidateQueries({ queryKey: ['licenses', key] });
      toast.success('Device deregistered');
    },
    onError: () => toast.error('Failed to deregister device'),
  });
}

/** Requests an immediate sync on the next heartbeat from this device. */
export function useForceSyncLicense() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (key: string) =>
      licenseClient.put(`admin/licenses/${key}`, { json: { forceSync: true } as UpdateLicenseRequest }).json<License>(),
    onSuccess: (_, key) => {
      qc.invalidateQueries({ queryKey: ['licenses', key] });
      toast.success('Force sync requested', 'The device will perform a full sync on its next heartbeat.');
    },
    onError: () => toast.error('Failed to request force sync'),
  });
}
