import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { LicenseDetailCard } from '@/components/licenses/LicenseDetailCard';
import type { License } from '@/types/license';

vi.mock('@/api/licenses', () => ({
  useLicenseDevices: () => ({ data: [], isLoading: false }),
  useDeregisterDevice: () => ({ mutate: vi.fn(), isPending: false }),
}));

const mockLicense: License = {
  id: 'lic-1',
  key: 'ZYNTA-ABCD-1234-EFGH',
  customerId: 'cust_abc123',
  customerName: 'Test Customer',
  edition: 'PROFESSIONAL',
  status: 'ACTIVE',
  maxDevices: 5,
  activeDevices: 3,
  activatedAt: new Date().toISOString(),
  expiresAt: '2026-12-31T00:00:00Z',
  lastHeartbeatAt: new Date().toISOString(),
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('LicenseDetailCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows masked license key', () => {
    render(<LicenseDetailCard license={mockLicense} onExtend={vi.fn()} />);
    // maskLicenseKey formats the key with asterisks in middle segments
    expect(screen.getByText(/ZYNTA-/)).toBeInTheDocument();
    expect(screen.getByText(/EFGH/)).toBeInTheDocument();
  });

  it('shows customer name', () => {
    render(<LicenseDetailCard license={mockLicense} onExtend={vi.fn()} />);
    expect(screen.getByText('Test Customer')).toBeInTheDocument();
  });

  it('shows edition badge', () => {
    render(<LicenseDetailCard license={mockLicense} onExtend={vi.fn()} />);
    expect(screen.getByText('PROFESSIONAL')).toBeInTheDocument();
  });

  it('shows device count vs max', () => {
    render(<LicenseDetailCard license={mockLicense} onExtend={vi.fn()} />);
    // Rendered as "3 / 5" in the devices metric tile
    expect(screen.getByText(/3\s*\/\s*5/)).toBeInTheDocument();
  });

  it('shows Extend button', () => {
    render(<LicenseDetailCard license={mockLicense} onExtend={vi.fn()} />);
    expect(screen.getByRole('button', { name: /extend/i })).toBeInTheDocument();
  });

  it('clicking Extend button calls onExtend', () => {
    const mockOnExtend = vi.fn();
    render(<LicenseDetailCard license={mockLicense} onExtend={mockOnExtend} />);
    fireEvent.click(screen.getByRole('button', { name: /extend/i }));
    expect(mockOnExtend).toHaveBeenCalledOnce();
  });
});
