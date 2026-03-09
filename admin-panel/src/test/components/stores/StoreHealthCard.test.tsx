import { describe, it, expect } from 'vitest';
import { render, screen } from '../../utils';
import { StoreHealthCard } from '@/components/stores/StoreHealthCard';
import type { StoreHealth } from '@/types/store';

const mockHealth: StoreHealth = {
  storeId: 'store-1',
  status: 'HEALTHY',
  healthScore: 97,
  dbSizeBytes: 1048576,
  syncQueueDepth: 2,
  errorCount24h: 0,
  uptimeHours: 720,
  lastHeartbeatAt: new Date().toISOString(),
  responseTimeMs: 45,
  appVersion: '1.0.0',
  osInfo: 'Android 14',
};

describe('StoreHealthCard', () => {
  it('shows health score value', () => {
    render(<StoreHealthCard health={mockHealth} />);
    expect(screen.getByText('97%')).toBeInTheDocument();
  });

  it('shows DB size metric', () => {
    render(<StoreHealthCard health={mockHealth} />);
    // 1048576 bytes = 1.0 MB
    expect(screen.getByText('1.0 MB')).toBeInTheDocument();
  });

  it('shows sync queue depth', () => {
    render(<StoreHealthCard health={mockHealth} />);
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('shows uptime hours', () => {
    render(<StoreHealthCard health={mockHealth} />);
    expect(screen.getByText('720h')).toBeInTheDocument();
  });

  it('shows response time', () => {
    render(<StoreHealthCard health={mockHealth} />);
    expect(screen.getByText('45ms')).toBeInTheDocument();
  });

  it('shows loading skeleton when isLoading is true', () => {
    const { container } = render(<StoreHealthCard isLoading={true} />);
    // When loading, skeleton divs are rendered instead of content
    const skeletonEls = container.querySelectorAll('[class*="animate-pulse"]');
    expect(skeletonEls.length).toBeGreaterThan(0);
  });

  it('shows app version', () => {
    render(<StoreHealthCard health={mockHealth} />);
    expect(screen.getByText(/1\.0\.0/)).toBeInTheDocument();
  });
});
