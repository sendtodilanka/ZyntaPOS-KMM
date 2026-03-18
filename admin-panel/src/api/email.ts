import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

export type EmailStatus = 'SENT' | 'DELIVERED' | 'BOUNCED' | 'FAILED';

export interface EmailDeliveryLog {
  id: string;
  to: string;
  subject: string;
  template: string;
  status: EmailStatus;
  sentAt: string;
  messageId?: string;
  error?: string;
}

export interface EmailDeliveryResponse {
  logs: EmailDeliveryLog[];
  total: number;
  page: number;
  pageSize: number;
}

export interface EmailLogFilters {
  status?: EmailStatus;
  startDate?: string;
  endDate?: string;
}

// Query keys
export const emailKeys = {
  all: ['email'] as const,
  deliveryLogs: (page: number, pageSize: number, filters?: EmailLogFilters) =>
    [...emailKeys.all, 'delivery-logs', page, pageSize, filters] as const,
};

/**
 * Fetches email delivery logs from the admin API.
 * Paginated with optional status and date range filters.
 */
export function useEmailDeliveryLogs(
  page = 1,
  pageSize = 20,
  filters?: EmailLogFilters,
) {
  return useQuery({
    queryKey: emailKeys.deliveryLogs(page, pageSize, filters),
    queryFn: () => {
      const params = new URLSearchParams({
        page: String(page),
        pageSize: String(pageSize),
      });
      if (filters?.status) params.set('status', filters.status);
      if (filters?.startDate) params.set('startDate', filters.startDate);
      if (filters?.endDate) params.set('endDate', filters.endDate);

      return apiClient
        .get(`admin/email/delivery-logs?${params.toString()}`)
        .json<EmailDeliveryResponse>();
    },
    staleTime: 30_000,
  });
}
