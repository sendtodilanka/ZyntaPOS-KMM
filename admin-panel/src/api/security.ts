import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { AuditEntry } from '@/types/audit';
import type { PagedResponse } from '@/types/api';

// ── Types ────────────────────────────────────────────────────────────────────

export interface SecurityMetrics {
  totalLoginAttempts24h: number;
  failedLogins24h: number;
  activeSessions: number;
  blockedIps: number;
}

export interface ActiveAdminSession {
  id: string;
  userId: string;
  userName: string;
  userEmail: string;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
  expiresAt: string;
}

export interface VulnerabilityScanStatus {
  lastScanDate: string | null;
  scanType: string;
  issuesFound: number;
  criticalCount: number;
  highCount: number;
  status: 'passed' | 'failed' | 'unknown';
}

// ── Hooks ────────────────────────────────────────────────────────────────────

export function useSecurityMetrics() {
  return useQuery({
    queryKey: ['security', 'metrics'],
    queryFn: () => apiClient.get('admin/security/metrics').json<SecurityMetrics>(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}

export function useSecurityEvents() {
  const qs = new URLSearchParams({
    page: '0',
    size: '20',
    category: 'AUTH',
  });
  return useQuery({
    queryKey: ['security', 'events'],
    queryFn: () => apiClient.get(`admin/audit?${qs}`).json<PagedResponse<AuditEntry>>(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}

export function useActiveAdminSessions() {
  return useQuery({
    queryKey: ['security', 'sessions'],
    queryFn: () => apiClient.get('admin/security/sessions').json<ActiveAdminSession[]>(),
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}

export function useVulnerabilityScan() {
  return useQuery({
    queryKey: ['security', 'vuln-scan'],
    queryFn: () => apiClient.get('admin/health/system').json<{ vulnerabilityScan?: VulnerabilityScanStatus }>(),
    staleTime: 60_000,
  });
}
