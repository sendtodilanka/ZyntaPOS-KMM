import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { LicenseExtendDialog } from '@/components/licenses/LicenseExtendDialog';
import type { License } from '@/types/license';

vi.mock('@/api/licenses', () => ({
  useUpdateLicense: () => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
  }),
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

describe('LicenseExtendDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns null when license is null', () => {
    const { container } = render(
      <LicenseExtendDialog license={null} onClose={vi.fn()} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders when license is provided', () => {
    render(<LicenseExtendDialog license={mockLicense} onClose={vi.fn()} />);
    // The dialog heading "Extend License" is in an h3 element
    expect(screen.getByRole('heading', { name: 'Extend License' })).toBeInTheDocument();
  });

  it('shows customer name in the dialog body', () => {
    render(<LicenseExtendDialog license={mockLicense} onClose={vi.fn()} />);
    expect(screen.getByText('Test Customer')).toBeInTheDocument();
  });

  it('shows expiry date input', () => {
    render(<LicenseExtendDialog license={mockLicense} onClose={vi.fn()} />);
    expect(screen.getByText('New Expiry Date')).toBeInTheDocument();
    // The date input is rendered with type="date" — query by its name attribute
    const dateInput = document.querySelector('input[name="expiresAt"]');
    expect(dateInput).toBeInTheDocument();
    expect(dateInput).toHaveAttribute('type', 'date');
  });

  it('shows optional reason field', () => {
    render(<LicenseExtendDialog license={mockLicense} onClose={vi.fn()} />);
    expect(screen.getByText(/reason/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/annual renewal/i)).toBeInTheDocument();
  });

  it('Cancel button calls onClose', () => {
    const mockOnClose = vi.fn();
    render(<LicenseExtendDialog license={mockLicense} onClose={mockOnClose} />);
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(mockOnClose).toHaveBeenCalledOnce();
  });
});
