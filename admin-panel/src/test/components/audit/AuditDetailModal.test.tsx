import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { AuditDetailModal } from '@/components/audit/AuditDetailModal';
import type { AuditEntry } from '@/types/audit';

vi.mock('@/lib/utils', () => ({
  formatDateTime: (s: string) => new Date(s).toLocaleString(),
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

describe('AuditDetailModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when entry is null', () => {
    const { container } = render(<AuditDetailModal entry={null} onClose={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders event type when entry is provided', () => {
    render(<AuditDetailModal entry={mockEntry} onClose={vi.fn()} />);
    expect(screen.getByText('USER_LOGIN')).toBeInTheDocument();
  });

  it('renders user name', () => {
    render(<AuditDetailModal entry={mockEntry} onClose={vi.fn()} />);
    expect(screen.getByText('Alice Smith')).toBeInTheDocument();
  });

  it('renders IP address', () => {
    render(<AuditDetailModal entry={mockEntry} onClose={vi.fn()} />);
    expect(screen.getByText('192.168.1.1')).toBeInTheDocument();
  });

  it('renders success indicator for successful event', () => {
    render(<AuditDetailModal entry={mockEntry} onClose={vi.fn()} />);
    expect(screen.getByText('Success')).toBeInTheDocument();
  });

  it('renders failure indicator for failed event', () => {
    const failedEntry: AuditEntry = {
      ...mockEntry,
      success: false,
      errorMessage: 'Invalid credentials',
    };
    render(<AuditDetailModal entry={failedEntry} onClose={vi.fn()} />);
    expect(screen.getByText(/Failed/)).toBeInTheDocument();
  });

  it('close button calls onClose', () => {
    const onClose = vi.fn();
    render(<AuditDetailModal entry={mockEntry} onClose={onClose} />);
    // Find the X close button in the modal header
    const buttons = screen.getAllByRole('button');
    const closeButton = buttons[buttons.length - 1];
    fireEvent.click(closeButton);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('shows JSON diff when previousValues and newValues are provided', () => {
    const entryWithDiff: AuditEntry = {
      ...mockEntry,
      previousValues: { name: 'Old Name' },
      newValues: { name: 'New Name' },
    };
    render(<AuditDetailModal entry={entryWithDiff} onClose={vi.fn()} />);
    expect(screen.getByText('Previous Values')).toBeInTheDocument();
    expect(screen.getByText('New Values')).toBeInTheDocument();
    expect(screen.getByText(/"Old Name"/)).toBeInTheDocument();
    expect(screen.getByText(/"New Name"/)).toBeInTheDocument();
  });
});
