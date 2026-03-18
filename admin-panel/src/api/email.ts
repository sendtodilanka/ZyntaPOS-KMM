import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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

export interface EmailTemplate {
  slug: string;
  name: string;
  subject: string;
  htmlBody: string;
  updatedAt: string;
}

export interface EmailPreferences {
  marketingEmails: boolean;
  ticketNotifications: boolean;
  unsubscribed: boolean;
}

// Query keys
export const emailKeys = {
  all: ['email'] as const,
  deliveryLogs: (page: number, pageSize: number, filters?: EmailLogFilters) =>
    [...emailKeys.all, 'delivery-logs', page, pageSize, filters] as const,
  templates: () => [...emailKeys.all, 'templates'] as const,
  template: (slug: string) => [...emailKeys.all, 'templates', slug] as const,
  preferences: () => [...emailKeys.all, 'preferences'] as const,
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

/**
 * Fetches all email templates.
 */
export function useEmailTemplates() {
  return useQuery({
    queryKey: emailKeys.templates(),
    queryFn: () =>
      apiClient
        .get('admin/email/templates')
        .json<{ templates: EmailTemplate[] }>()
        .then((r) => r.templates),
    staleTime: 60_000,
  });
}

/**
 * Fetches a single email template by slug.
 */
export function useEmailTemplate(slug: string) {
  return useQuery({
    queryKey: emailKeys.template(slug),
    queryFn: () =>
      apiClient.get(`admin/email/templates/${slug}`).json<EmailTemplate>(),
    enabled: !!slug,
  });
}

/**
 * Updates an email template (subject + body).
 */
export function useUpdateEmailTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      slug,
      subject,
      htmlBody,
    }: {
      slug: string;
      subject: string;
      htmlBody: string;
    }) =>
      apiClient
        .put(`admin/email/templates/${slug}`, { json: { subject, htmlBody } })
        .json(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: emailKeys.templates() });
    },
  });
}

/**
 * Fetches email preferences for the current admin user.
 */
export function useEmailPreferences() {
  return useQuery({
    queryKey: emailKeys.preferences(),
    queryFn: () =>
      apiClient.get('admin/email/preferences').json<EmailPreferences>(),
    staleTime: 60_000,
  });
}

/**
 * Updates email preferences for the current admin user.
 */
export function useUpdateEmailPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (prefs: { marketingEmails: boolean; ticketNotifications: boolean }) =>
      apiClient.put('admin/email/preferences', { json: prefs }).json(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: emailKeys.preferences() });
    },
  });
}
