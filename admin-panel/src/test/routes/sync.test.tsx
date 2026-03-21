import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import React from 'react';
import type { StoreSyncStatus, SyncConflict, DeadLetterOperation } from '@/types/sync';

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => opts,
  useNavigate: () => vi.fn(),
}));

vi.mock('@/hooks/use-timezone', () => ({
  useTimezone: () => ({ formatDateTime: (v: unknown) => String(v) }),
}));

// Mock heavy chart component
vi.mock('@/components/charts/SyncHealthChart', () => ({
  SyncHealthChart: () => <div data-testid="sync-health-chart" />,
}));

vi.mock('@/components/sync/SyncDashboard', () => ({
  SyncDashboard: ({ stores }: { stores: StoreSyncStatus[] }) => (
    <div data-testid="sync-dashboard">{stores.length} stores</div>
  ),
}));

vi.mock('@/api/sync');
import {
  useSyncStatus, useConflictLog, useDeadLetters,
  useRetryDeadLetter, useDiscardDeadLetter,
} from '@/api/sync';
import { Route } from '@/routes/sync/index';

const SyncPage = (Route as { component: React.FC }).component;

const mockStoreStatus: StoreSyncStatus = {
  storeId: 'store-1',
  storeName: 'Colombo Store',
  status: 'SYNCED',
  queueDepth: 0,
  lastSyncAt: new Date().toISOString(),
  lastSyncDurationMs: 320,
  errorCount: 0,
  pendingOperations: 0,
};

const mockConflict: SyncConflict = {
  id: 'conflict-1',
  entityType: 'product',
  entityId: 'prod-1',
  storeId: 'store-1',
  clientData: {},
  serverData: {},
  resolvedBy: 'lww',
  createdAt: Date.now(),
  resolvedAt: Date.now(),
};

const mockDeadLetter: DeadLetterOperation = {
  id: 'dl-1',
  operationType: 'CREATE',
  entityType: 'order',
  entityId: 'order-1',
  storeId: 'store-1',
  payload: {},
  failureReason: 'Foreign key constraint',
  retryCount: 3,
  createdAt: Date.now(),
  lastAttemptAt: Date.now(),
};

describe('SyncPage', () => {
  beforeEach(() => {
    vi.mocked(useSyncStatus).mockReturnValue({
      data: [mockStoreStatus],
      isLoading: false,
    } as ReturnType<typeof useSyncStatus>);

    vi.mocked(useConflictLog).mockReturnValue({
      data: [mockConflict],
      isLoading: false,
    } as ReturnType<typeof useConflictLog>);

    vi.mocked(useDeadLetters).mockReturnValue({
      data: [mockDeadLetter],
      isLoading: false,
    } as ReturnType<typeof useDeadLetters>);

    vi.mocked(useRetryDeadLetter).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useRetryDeadLetter>);

    vi.mocked(useDiscardDeadLetter).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useDiscardDeadLetter>);
  });

  it('renders page heading', () => {
    render(<SyncPage />);
    expect(screen.getByText('Sync Monitoring')).toBeInTheDocument();
  });

  it('renders overview tab by default', () => {
    render(<SyncPage />);
    expect(screen.getByTestId('sync-dashboard')).toBeInTheDocument();
  });

  it('renders sync health chart', () => {
    render(<SyncPage />);
    expect(screen.getByTestId('sync-health-chart')).toBeInTheDocument();
  });

  it('renders conflicts tab button', () => {
    render(<SyncPage />);
    expect(screen.getByRole('button', { name: /conflicts/i })).toBeInTheDocument();
  });

  it('renders dead letters tab button', () => {
    render(<SyncPage />);
    expect(screen.getByRole('button', { name: /dead letters/i })).toBeInTheDocument();
  });

  it('switches to conflicts tab on click', () => {
    render(<SyncPage />);
    fireEvent.click(screen.getByRole('button', { name: /conflicts/i }));
    expect(screen.getByText('product')).toBeInTheDocument();
    expect(screen.getByText('lww')).toBeInTheDocument();
  });

  it('switches to dead letters tab on click', () => {
    render(<SyncPage />);
    fireEvent.click(screen.getByRole('button', { name: /dead letters/i }));
    expect(screen.getByText('order')).toBeInTheDocument();
    expect(screen.getByText(/foreign key constraint/i)).toBeInTheDocument();
  });

  it('renders retry button for dead letter', () => {
    render(<SyncPage />);
    fireEvent.click(screen.getByRole('button', { name: /dead letters/i }));
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('calls retryDeadLetter on retry click', () => {
    const mockMutate = vi.fn();
    vi.mocked(useRetryDeadLetter).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useRetryDeadLetter>);
    render(<SyncPage />);
    fireEvent.click(screen.getByRole('button', { name: /dead letters/i }));
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(mockMutate).toHaveBeenCalledWith('dl-1');
  });

  it('renders discard button for dead letter', () => {
    render(<SyncPage />);
    fireEvent.click(screen.getByRole('button', { name: /dead letters/i }));
    expect(screen.getByRole('button', { name: /discard/i })).toBeInTheDocument();
  });

  it('shows store count in sync dashboard', () => {
    render(<SyncPage />);
    expect(screen.getByTestId('sync-dashboard')).toHaveTextContent('1 stores');
  });

  it('renders empty state when no conflicts', () => {
    vi.mocked(useConflictLog).mockReturnValue({
      data: [],
      isLoading: false,
    } as unknown as ReturnType<typeof useConflictLog>);
    render(<SyncPage />);
    fireEvent.click(screen.getByRole('button', { name: /conflicts/i }));
    expect(screen.getByText(/no conflicts/i)).toBeInTheDocument();
  });

  it('renders empty state when no dead letters', () => {
    vi.mocked(useDeadLetters).mockReturnValue({
      data: [],
      isLoading: false,
    } as unknown as ReturnType<typeof useDeadLetters>);
    render(<SyncPage />);
    fireEvent.click(screen.getByRole('button', { name: /dead letters/i }));
    expect(screen.getByText(/no dead letters/i)).toBeInTheDocument();
  });
});
