import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '../utils';
import React from 'react';
import type { SystemHealth, StoreHealthSummary } from '@/types/health';

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => opts,
  useNavigate: () => vi.fn(),
  Link: ({ children, to, params }: { children: React.ReactNode; to: string; params?: Record<string, string> }) => (
    <a href={params ? to.replace('$storeId', params.storeId ?? '') : to}>{children}</a>
  ),
}));

vi.mock('@/api/health');
import { useSystemHealth, useAllStoreHealth } from '@/api/health';
import { Route } from '@/routes/health/index';

const HealthPage = (Route as { component: React.FC }).component;

const mockHealth: SystemHealth = {
  overall: 'healthy',
  checkedAt: new Date().toISOString(),
  services: [
    {
      name: 'API',
      status: 'healthy',
      latencyMs: 12,
      uptime: 99.98,
      version: '1.0.0',
      lastChecked: new Date().toISOString(),
    },
    {
      name: 'Database',
      status: 'healthy',
      latencyMs: 5,
      uptime: 99.99,
      lastChecked: new Date().toISOString(),
    },
    {
      name: 'Redis',
      status: 'degraded',
      latencyMs: 320,
      uptime: 97.50,
      lastChecked: new Date().toISOString(),
    },
  ],
};

const mockStoreHealth: StoreHealthSummary[] = [
  {
    storeId: 'store-1',
    storeName: 'Colombo Store',
    status: 'healthy',
    lastSync: new Date().toISOString(),
    pendingOperations: 0,
    appVersion: '1.0.0',
    androidVersion: '14',
    uptimePercent: 99.5,
  },
];

describe('HealthPage', () => {
  beforeEach(() => {
    vi.mocked(useSystemHealth).mockReturnValue({
      data: mockHealth,
      isLoading: false,
      refetch: vi.fn(),
      isFetching: false,
    } as ReturnType<typeof useSystemHealth>);

    vi.mocked(useAllStoreHealth).mockReturnValue({
      data: mockStoreHealth,
      isLoading: false,
    } as ReturnType<typeof useAllStoreHealth>);
  });

  it('renders page heading', () => {
    render(<HealthPage />);
    expect(screen.getByRole('heading', { name: /system health/i })).toBeInTheDocument();
  });

  it('renders service cards for each service', () => {
    render(<HealthPage />);
    expect(screen.getByText('API')).toBeInTheDocument();
    expect(screen.getByText('Database')).toBeInTheDocument();
    expect(screen.getByText('Redis')).toBeInTheDocument();
  });

  it('renders healthy status badge for healthy service', () => {
    render(<HealthPage />);
    const healthyBadges = screen.getAllByText('healthy');
    expect(healthyBadges.length).toBeGreaterThan(0);
  });

  it('renders degraded status for degraded service', () => {
    render(<HealthPage />);
    expect(screen.getByText('degraded')).toBeInTheDocument();
  });

  it('renders latency values', () => {
    render(<HealthPage />);
    expect(screen.getByText('12ms')).toBeInTheDocument();
    expect(screen.getByText('5ms')).toBeInTheDocument();
    expect(screen.getByText('320ms')).toBeInTheDocument();
  });

  it('renders uptime percentages', () => {
    render(<HealthPage />);
    expect(screen.getByText('99.98%')).toBeInTheDocument();
    expect(screen.getByText('97.50%')).toBeInTheDocument();
  });

  it('renders store health section', () => {
    render(<HealthPage />);
    expect(screen.getByText('Colombo Store')).toBeInTheDocument();
  });

  it('shows loading state when health data is loading', () => {
    vi.mocked(useSystemHealth).mockReturnValue({
      data: undefined,
      isLoading: true,
      refetch: vi.fn(),
      isFetching: false,
    } as ReturnType<typeof useSystemHealth>);
    render(<HealthPage />);
    expect(screen.getByText(/system health/i)).toBeInTheDocument();
  });

  it('renders refresh button', () => {
    render(<HealthPage />);
    expect(screen.getByRole('button', { name: /refresh/i })).toBeInTheDocument();
  });

  it('renders version tag when version provided', () => {
    render(<HealthPage />);
    const versionTags = screen.getAllByText(/v1\.0\.0/i);
    expect(versionTags.length).toBeGreaterThan(0);
  });
});
