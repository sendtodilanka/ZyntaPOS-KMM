import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import React from 'react';
import type { Alert, AlertRule } from '@/types/alert';

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => opts,
  useNavigate: () => vi.fn(),
}));

vi.mock('@/api/alerts');
import {
  useAlerts, useAlertCounts, useAlertRules,
  useAcknowledgeAlert, useResolveAlert, useToggleAlertRule,
} from '@/api/alerts';
import { Route } from '@/routes/alerts/index';

const AlertsPage = (Route as unknown as { component: React.FC }).component;

const mockAlert: Alert = {
  id: 'alert-1',
  title: 'Low stock detected',
  message: 'Product X has 2 units remaining',
  severity: 'medium',
  status: 'active',
  category: 'system',
  storeId: 'store-1',
  storeName: 'Colombo Store',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

const mockCounts: Record<string, number> = {
  active: 3,
  acknowledged: 1,
  resolved: 10,
  silenced: 0,
  critical: 1,
  high: 1,
  medium: 1,
  low: 0,
  info: 0,
};

const mockRule: AlertRule = {
  id: 'rule-1',
  name: 'Low Stock Alert',
  description: 'Triggers when stock drops below reorder point',
  category: 'system',
  severity: 'medium',
  enabled: true,
  conditions: {},
  notifyChannels: [],
};

describe('AlertsPage', () => {
  beforeEach(() => {
    vi.mocked(useAlerts).mockReturnValue({
      data: { items: [mockAlert], total: 1, page: 0, pageSize: 20 },
      isLoading: false,
    } as unknown as ReturnType<typeof useAlerts>);

    vi.mocked(useAlertCounts).mockReturnValue({
      data: mockCounts,
      isLoading: false,
    } as unknown as ReturnType<typeof useAlertCounts>);

    vi.mocked(useAlertRules).mockReturnValue({
      data: [mockRule],
      isLoading: false,
    } as unknown as ReturnType<typeof useAlertRules>);

    vi.mocked(useAcknowledgeAlert).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useAcknowledgeAlert>);

    vi.mocked(useResolveAlert).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useResolveAlert>);

    vi.mocked(useToggleAlertRule).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useToggleAlertRule>);
  });

  it('renders page heading', () => {
    render(<AlertsPage />);
    expect(screen.getByRole('heading', { name: 'Alerts' })).toBeInTheDocument();
  });

  it('renders alert count summary', () => {
    render(<AlertsPage />);
    // The component renders "{counts['active']} active" and "{counts['critical']} critical"
    expect(screen.getByText(/3 active/)).toBeInTheDocument();
  });

  it('renders the active alert title', () => {
    render(<AlertsPage />);
    expect(screen.getByText('Low stock detected')).toBeInTheDocument();
  });

  it('renders alert store name', () => {
    render(<AlertsPage />);
    expect(screen.getByText(/Colombo Store/)).toBeInTheDocument();
  });

  it('renders alert severity badge', () => {
    render(<AlertsPage />);
    const mediumBadges = screen.getAllByText('medium');
    expect(mediumBadges.length).toBeGreaterThan(0);
  });

  it('renders tab navigation', () => {
    render(<AlertsPage />);
    expect(screen.getByRole('button', { name: /active/i })).toBeInTheDocument();
  });

  it('renders alert rules tab', () => {
    render(<AlertsPage />);
    const rulesTab = screen.getByRole('button', { name: /rules/i });
    expect(rulesTab).toBeInTheDocument();
  });

  it('switches to rules view on tab click', () => {
    render(<AlertsPage />);
    const rulesTab = screen.getByRole('button', { name: /rules/i });
    fireEvent.click(rulesTab);
    expect(screen.getByText('Low Stock Alert')).toBeInTheDocument();
  });

  it('shows empty state when no alerts', () => {
    vi.mocked(useAlerts).mockReturnValue({
      data: { items: [], total: 0, page: 0, pageSize: 20 },
      isLoading: false,
    } as unknown as ReturnType<typeof useAlerts>);
    render(<AlertsPage />);
    expect(screen.getByText(/no active alerts/i)).toBeInTheDocument();
  });

  it('renders acknowledge button for active alert', () => {
    render(<AlertsPage />);
    expect(screen.getByRole('button', { name: /acknowledge/i })).toBeInTheDocument();
  });

  it('calls acknowledgeAlert mutate when clicked', () => {
    const mockMutate = vi.fn();
    vi.mocked(useAcknowledgeAlert).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useAcknowledgeAlert>);
    render(<AlertsPage />);
    fireEvent.click(screen.getByRole('button', { name: /acknowledge/i }));
    expect(mockMutate).toHaveBeenCalledWith('alert-1');
  });
});
