import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { exportToCsv } from '@/lib/export';
import type { AuditEntry, AuditFilter } from '@/types/audit';
import type { PagedResponse } from '@/types/api';

export function useAuditLogs(filters: AuditFilter = {}) {
  const qs = new URLSearchParams();
  if (filters.page !== undefined) qs.set('page', String(filters.page));
  if (filters.size !== undefined) qs.set('size', String(filters.size ?? 50));
  if (filters.category) qs.set('category', filters.category);
  if (filters.eventType) qs.set('eventType', filters.eventType);
  if (filters.userId) qs.set('userId', filters.userId);
  if (filters.storeId) qs.set('storeId', filters.storeId);
  if (filters.entityType) qs.set('entityType', filters.entityType);
  if (filters.success !== undefined) qs.set('success', String(filters.success));
  if (filters.from) qs.set('from', filters.from);
  if (filters.to) qs.set('to', filters.to);
  if (filters.search) qs.set('search', filters.search);

  return useQuery({
    queryKey: ['audit', filters],
    queryFn: () => apiClient.get(`admin/audit?${qs}`).json<PagedResponse<AuditEntry>>(),
  });
}

export async function exportAuditLogs(filters: AuditFilter): Promise<void> {
  const qs = new URLSearchParams();
  if (filters.category) qs.set('category', filters.category);
  if (filters.from) qs.set('from', filters.from);
  if (filters.to) qs.set('to', filters.to);
  if (filters.storeId) qs.set('storeId', filters.storeId);
  if (filters.success !== undefined) qs.set('success', String(filters.success));

  const data = await apiClient.get(`admin/audit/export?${qs}`).json<AuditEntry[]>();
  exportToCsv(data, 'audit-log', [
    { key: 'createdAt', header: 'Timestamp' },
    { key: 'eventType', header: 'Event Type' },
    { key: 'category', header: 'Category' },
    { key: 'userName', header: 'User' },
    { key: 'storeName', header: 'Store' },
    { key: 'entityType', header: 'Entity Type' },
    { key: 'entityId', header: 'Entity ID' },
    { key: 'success', header: 'Success' },
    { key: 'errorMessage', header: 'Error' },
  ]);
}
