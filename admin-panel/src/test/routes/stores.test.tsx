import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import React from 'react';
import type { Store } from '@/types/store';

// ── Router mock (must be before route import) ───────────────────────────────
vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => opts,
  useNavigate: () => vi.fn(),
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}));

// ── Debounce passthrough ────────────────────────────────────────────────────
vi.mock('@/hooks/use-debounce', () => ({
  useDebounce: (val: unknown) => val,
}));

// ── API mock ────────────────────────────────────────────────────────────────
vi.mock('@/api/stores');
import { useStores } from '@/api/stores';

import { Route } from '@/routes/stores/index';

const StoresPage = (Route as { component: React.FC }).component;

const mockStore: Store = {
  id: 'store-1',
  name: 'Colombo Store',
  location: 'Colombo, Sri Lanka',
  licenseKey: 'ZYNTA-ABCD-1234-EFGH',
  edition: 'PROFESSIONAL',
  status: 'HEALTHY',
  activeUsers: 3,
  lastSyncAt: new Date().toISOString(),
  lastHeartbeatAt: new Date().toISOString(),
  appVersion: '1.0.0',
  createdAt: new Date().toISOString(),
};

const mockData = { data: [mockStore], total: 1, totalPages: 1 };

describe('StoresPage', () => {
  beforeEach(() => {
    vi.mocked(useStores).mockReturnValue({
      data: mockData,
      isLoading: false,
    } as ReturnType<typeof useStores>);
  });

  it('renders page heading', () => {
    render(<StoresPage />);
    expect(screen.getByText('Stores')).toBeInTheDocument();
  });

  it('renders total deployment count', () => {
    render(<StoresPage />);
    expect(screen.getByText(/1 total/i)).toBeInTheDocument();
  });

  it('renders search input', () => {
    render(<StoresPage />);
    expect(screen.getByPlaceholderText(/search stores/i)).toBeInTheDocument();
  });

  it('renders status filter dropdown', () => {
    render(<StoresPage />);
    expect(screen.getByRole('combobox', { name: /filter by status/i })).toBeInTheDocument();
  });

  it('renders store data from API', () => {
    render(<StoresPage />);
    expect(screen.getByText('Colombo Store')).toBeInTheDocument();
  });

  it('shows 0 total when data is undefined', () => {
    vi.mocked(useStores).mockReturnValue({
      data: undefined,
      isLoading: false,
    } as ReturnType<typeof useStores>);
    render(<StoresPage />);
    expect(screen.getByText(/0 total/i)).toBeInTheDocument();
  });

  it('passes loading state to table', () => {
    vi.mocked(useStores).mockReturnValue({
      data: undefined,
      isLoading: true,
    } as ReturnType<typeof useStores>);
    render(<StoresPage />);
    // Page heading still visible during loading
    expect(screen.getByText('Stores')).toBeInTheDocument();
  });

  it('filters by status when select changes', () => {
    render(<StoresPage />);
    const select = screen.getByRole('combobox', { name: /filter by status/i });
    fireEvent.change(select, { target: { value: 'HEALTHY' } });
    expect(select).toHaveValue('HEALTHY');
  });

  it('renders all status options', () => {
    render(<StoresPage />);
    const select = screen.getByRole('combobox', { name: /filter by status/i });
    expect(select).toContainHTML('HEALTHY');
    expect(select).toContainHTML('WARNING');
    expect(select).toContainHTML('CRITICAL');
    expect(select).toContainHTML('OFFLINE');
  });

  it('updates search value on input', () => {
    render(<StoresPage />);
    const input = screen.getByPlaceholderText(/search stores/i);
    fireEvent.change(input, { target: { value: 'Kandy' } });
    expect(input).toHaveValue('Kandy');
  });
});
