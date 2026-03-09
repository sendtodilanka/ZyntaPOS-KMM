import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '../../utils';
import { SyncDashboard } from '@/components/sync/SyncDashboard';
import type { StoreSyncStatus } from '@/types/sync';

// Mock ForceSyncButton so it renders predictably without needing @/api/sync
vi.mock('@/components/sync/ForceSyncButton', () => ({
  ForceSyncButton: ({ storeId }: { storeId: string }) => (
    <button data-testid={`force-sync-${storeId}`}>Force Sync</button>
  ),
}));

// Mock SyncQueueView to avoid needing useSyncQueue in expand flow
vi.mock('@/components/sync/SyncQueueView', () => ({
  SyncQueueView: () => <div data-testid="sync-queue-view">Queue</div>,
}));

// useSyncQueue is called by StoreCard when expanded; stub it
vi.mock('@/api/sync', () => ({
  useSyncQueue: () => ({ data: [], isLoading: false }),
}));

vi.mock('@/lib/utils', () => ({
  formatRelativeTime: (_s: string) => 'just now',
  cn: (...args: unknown[]) => args.filter(Boolean).join(' '),
}));

vi.mock('@/components/shared/StatusBadge', () => ({
  StatusBadge: ({ status }: { status: string }) => (
    <span data-testid="status-badge">{status}</span>
  ),
}));

const mockStoreSync: StoreSyncStatus = {
  storeId: 'store-1',
  storeName: 'Colombo Store',
  status: 'SYNCED',
  queueDepth: 0,
  lastSyncAt: new Date().toISOString(),
  lastSyncDurationMs: 1200,
  errorCount: 0,
  pendingOperations: 0,
};

describe('SyncDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders store name', () => {
    render(<SyncDashboard stores={[mockStoreSync]} isLoading={false} />);
    expect(screen.getByText('Colombo Store')).toBeInTheDocument();
  });

  it('renders sync status badge', () => {
    render(<SyncDashboard stores={[mockStoreSync]} isLoading={false} />);
    expect(screen.getByTestId('status-badge')).toHaveTextContent('SYNCED');
  });

  it('shows queue depth', () => {
    const storeWithQueue: StoreSyncStatus = { ...mockStoreSync, queueDepth: 7 };
    render(<SyncDashboard stores={[storeWithQueue]} isLoading={false} />);
    expect(screen.getByText('7')).toBeInTheDocument();
  });

  it('shows last sync time via relative formatter', () => {
    render(<SyncDashboard stores={[mockStoreSync]} isLoading={false} />);
    // formatRelativeTime is mocked to return "just now"
    expect(screen.getByText(/just now/)).toBeInTheDocument();
  });

  it('shows pending operations count', () => {
    const storeWithPending: StoreSyncStatus = { ...mockStoreSync, pendingOperations: 3 };
    render(<SyncDashboard stores={[storeWithPending]} isLoading={false} />);
    expect(screen.getByText('3 pending')).toBeInTheDocument();
  });

  it('shows error count when store has errors', () => {
    const storeWithErrors: StoreSyncStatus = { ...mockStoreSync, errorCount: 5 };
    render(<SyncDashboard stores={[storeWithErrors]} isLoading={false} />);
    expect(screen.getByText('5 errors')).toBeInTheDocument();
  });

  it('renders force sync button for each store', () => {
    render(<SyncDashboard stores={[mockStoreSync]} isLoading={false} />);
    expect(screen.getByTestId('force-sync-store-1')).toBeInTheDocument();
  });

  it('shows empty state when stores array is empty', () => {
    render(<SyncDashboard stores={[]} isLoading={false} />);
    expect(screen.getByText('No stores to monitor.')).toBeInTheDocument();
  });

  it('shows loading skeleton when isLoading is true', () => {
    const { container } = render(<SyncDashboard stores={[]} isLoading={true} />);
    // Loading state renders animate-pulse skeleton divs
    const skeletons = container.querySelectorAll('.animate-pulse');
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it('renders multiple stores', () => {
    const store2: StoreSyncStatus = {
      ...mockStoreSync,
      storeId: 'store-2',
      storeName: 'Kandy Store',
    };
    render(<SyncDashboard stores={[mockStoreSync, store2]} isLoading={false} />);
    expect(screen.getByText('Colombo Store')).toBeInTheDocument();
    expect(screen.getByText('Kandy Store')).toBeInTheDocument();
  });

  it('shows "Never synced" when lastSyncAt is null', () => {
    const neverSynced: StoreSyncStatus = {
      ...mockStoreSync,
      lastSyncAt: null,
      lastSyncDurationMs: null,
    };
    render(<SyncDashboard stores={[neverSynced]} isLoading={false} />);
    // The text "Never synced" lives in a <p> that may contain sibling text nodes
    expect(screen.getByText(/Never synced/)).toBeInTheDocument();
  });
});
