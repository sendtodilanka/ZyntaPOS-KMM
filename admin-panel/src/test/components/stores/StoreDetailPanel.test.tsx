import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { StoreDetailPanel } from '@/components/stores/StoreDetailPanel';
import type { Store, StoreConfig } from '@/types/store';

vi.mock('@/api/stores', () => ({
  useStoreHealth: () => ({
    data: {
      storeId: 'store-1',
      status: 'HEALTHY',
      healthScore: 98,
      dbSizeBytes: 1024 * 1024,
      syncQueueDepth: 0,
      errorCount24h: 0,
      uptimeHours: 720,
      lastHeartbeatAt: new Date().toISOString(),
      responseTimeMs: 45,
      appVersion: '1.0.0',
      osInfo: 'Android 14',
    },
    isLoading: false,
  }),
  useUpdateStoreConfig: () => ({ mutate: vi.fn(), isPending: false }),
}));

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

const mockConfig: StoreConfig = {
  storeId: 'store-1',
  taxRates: [],
  featureFlags: {},
  timezone: 'Asia/Colombo',
  currency: 'LKR',
  receiptFooter: 'Thank you!',
  syncIntervalSeconds: 300,
  updatedAt: new Date().toISOString(),
};

describe('StoreDetailPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders Overview tab by default', () => {
    render(<StoreDetailPanel store={mockStore} config={mockConfig} />);
    const overviewTab = screen.getByRole('button', { name: /overview/i });
    expect(overviewTab).toBeInTheDocument();
  });

  it('Overview tab shows health score or status data', () => {
    render(<StoreDetailPanel store={mockStore} config={mockConfig} />);
    // Health score is displayed as "98%" from the health card
    expect(screen.getByText('98%')).toBeInTheDocument();
  });

  it('clicking Configuration tab shows config form', () => {
    render(<StoreDetailPanel store={mockStore} config={mockConfig} />);
    const configTab = screen.getByRole('button', { name: /configuration/i });
    fireEvent.click(configTab);
    expect(screen.getByText(/store configuration/i)).toBeInTheDocument();
    expect(screen.getByDisplayValue('Asia/Colombo')).toBeInTheDocument();
  });

  it('clicking Sync tab shows sync information', () => {
    render(<StoreDetailPanel store={mockStore} config={mockConfig} />);
    const syncTab = screen.getByRole('button', { name: /sync/i });
    fireEvent.click(syncTab);
    expect(screen.getByText(/sync status/i)).toBeInTheDocument();
    expect(screen.getByText('Queue Depth')).toBeInTheDocument();
  });
});
