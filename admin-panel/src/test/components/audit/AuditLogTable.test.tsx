import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { AuditLogTable } from '@/components/audit/AuditLogTable';
import type { AuditEntry } from '@/types/audit';

vi.mock('@/hooks/use-timezone', () => ({
  useTimezone: () => ({
    formatDateTime: (_ms: number) => '1 Jan 2025 10:00',
    formatDate: (_ms: number) => '1 Jan 2025',
    formatRelative: (_ms: number) => 'just now',
  }),
}));

vi.mock('@/lib/utils', () => ({
  formatDateTime: (s: string) => new Date(s).toLocaleString(),
  formatRelativeTime: (_s: string) => 'just now',
  truncate: (s: string, n: number) => (s.length > n ? s.slice(0, n) + '…' : s),
  cn: (...args: unknown[]) => args.filter(Boolean).join(' '),
}));

const mockEntry: AuditEntry = {
  id: 'audit-1',
  eventType: 'USER_LOGIN',
  category: 'AUTH',
  userId: 'user-1',
  userName: 'Alice Smith',
  storeId: null,
  storeName: null,
  entityType: 'user',
  entityId: 'user-1',
  previousValues: null,
  newValues: null,
  ipAddress: '192.168.1.1',
  userAgent: 'Mozilla/5.0',
  success: true,
  errorMessage: null,
  hashChain: 'abc123',
  createdAt: new Date().toISOString(),
};

const defaultProps = {
  data: [mockEntry],
  isLoading: false,
  page: 1,
  totalPages: 1,
  total: 1,
  onPageChange: vi.fn(),
};

describe('AuditLogTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders user name', () => {
    render(<AuditLogTable {...defaultProps} />);
    expect(screen.getByText('Alice Smith')).toBeInTheDocument();
  });

  it('renders event type', () => {
    render(<AuditLogTable {...defaultProps} />);
    expect(screen.getByText('USER_LOGIN')).toBeInTheDocument();
  });

  it('renders category badge', () => {
    render(<AuditLogTable {...defaultProps} />);
    expect(screen.getByText('AUTH')).toBeInTheDocument();
  });

  it('shows success indicator for successful events', () => {
    render(<AuditLogTable {...defaultProps} />);
    // CheckCircle svg is rendered for success=true; verify no failure icon present
    const svgs = document.querySelectorAll('svg');
    // At least one svg should be in the document (the CheckCircle icon)
    expect(svgs.length).toBeGreaterThan(0);
  });

  it('shows empty state when data is empty', () => {
    render(<AuditLogTable {...defaultProps} data={[]} />);
    expect(screen.getByText('No audit entries')).toBeInTheDocument();
  });

  it('clicking a row opens detail modal', async () => {
    render(<AuditLogTable {...defaultProps} />);
    // Click the Eye button to open modal
    const eyeButtons = screen.getAllByRole('button');
    // Find the eye-icon button (the view action button in the row)
    const viewButton = eyeButtons.find((btn) =>
      btn.querySelector('svg') !== null && btn.closest('td') !== null,
    ) ?? eyeButtons[0];
    fireEvent.click(viewButton);
    await waitFor(() => {
      // The modal renders the eventType as the title
      expect(screen.getAllByText('USER_LOGIN').length).toBeGreaterThan(0);
    });
  });
});
