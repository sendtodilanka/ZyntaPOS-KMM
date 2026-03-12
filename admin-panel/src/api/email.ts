import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

export interface EmailDeliveryLog {
  id: string;
  to: string;
  subject: string;
  template: string;
  status: 'SENT' | 'DELIVERED' | 'BOUNCED' | 'FAILED';
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

// Query keys
export const emailKeys = {
  all: ['email'] as const,
  deliveryLogs: (page: number, pageSize: number) =>
    [...emailKeys.all, 'delivery-logs', page, pageSize] as const,
};

/**
 * Fetches email delivery logs from the admin API.
 * Paginated — returns logs sorted by sentAt descending.
 */
export function useEmailDeliveryLogs(page = 1, pageSize = 20) {
  return useQuery({
    queryKey: emailKeys.deliveryLogs(page, pageSize),
    queryFn: () =>
      apiClient
        .get(`admin/email/delivery-logs?page=${page}&pageSize=${pageSize}`)
        .json<EmailDeliveryResponse>(),
    staleTime: 30_000,
  });
}
