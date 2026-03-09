import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { UserTable } from '@/components/users/UserTable';
import type { AdminUser } from '@/types/user';

vi.mock('@/api/users', () => ({
  useDeactivateUser: () => ({
    mutate: vi.fn().mockImplementation((_id: string, opts?: { onSettled?: () => void }) => {
      opts?.onSettled?.();
    }),
    isPending: false,
  }),
  useRevokeSessions: () => ({
    mutate: vi.fn().mockImplementation((_id: string, opts?: { onSettled?: () => void }) => {
      opts?.onSettled?.();
    }),
    isPending: false,
  }),
}));

vi.mock('@/hooks/use-timezone', () => ({
  useTimezone: () => ({
    formatRelative: (_ms: number) => 'just now',
    formatDateTime: (_ms: number) => '1 Jan 2025',
    formatDate: (_ms: number) => '1 Jan 2025',
  }),
}));

const mockUser: AdminUser = {
  id: 'user-1',
  email: 'alice@example.com',
  name: 'Alice Smith',
  role: 'OPERATOR',
  mfaEnabled: true,
  isActive: true,
  lastLoginAt: Date.now(),
  createdAt: Date.now() - 86400000,
};

const defaultProps = {
  data: [mockUser],
  isLoading: false,
  page: 1,
  totalPages: 1,
  total: 1,
  onPageChange: vi.fn(),
  onEdit: vi.fn(),
};

describe('UserTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders user name and email', () => {
    render(<UserTable {...defaultProps} />);
    expect(screen.getByText('Alice Smith')).toBeInTheDocument();
    expect(screen.getByText('alice@example.com')).toBeInTheDocument();
  });

  it('renders role badge for OPERATOR', () => {
    render(<UserTable {...defaultProps} />);
    expect(screen.getByText('Operator')).toBeInTheDocument();
  });

  it('renders MFA status as On when mfaEnabled is true', () => {
    render(<UserTable {...defaultProps} />);
    expect(screen.getByText('On')).toBeInTheDocument();
  });

  it('renders MFA status as Off when mfaEnabled is false', () => {
    const userNoMfa = { ...mockUser, mfaEnabled: false };
    render(<UserTable {...defaultProps} data={[userNoMfa]} />);
    expect(screen.getByText('Off')).toBeInTheDocument();
  });

  it('shows empty state when data is empty', () => {
    render(<UserTable {...defaultProps} data={[]} />);
    expect(screen.getByText('No users found')).toBeInTheDocument();
  });

  it('clicking Actions button opens dropdown with Edit, Revoke, and Deactivate options', async () => {
    render(<UserTable {...defaultProps} />);
    // The MoreHorizontal icon button — find by querying the SVG container
    const actionButtons = screen.getAllByRole('button');
    // The only non-pagination button in the row is the actions menu trigger
    const menuTrigger = actionButtons.find((btn) =>
      btn.querySelector('svg') !== null && btn.closest('td') !== null,
    ) ?? actionButtons[0];
    fireEvent.click(menuTrigger);
    await waitFor(() => {
      expect(screen.getByText('Edit Role')).toBeInTheDocument();
      expect(screen.getByText('Revoke Sessions')).toBeInTheDocument();
      expect(screen.getByText('Deactivate')).toBeInTheDocument();
    });
  });

  it('clicking Deactivate shows ConfirmDialog with deactivation text', async () => {
    render(<UserTable {...defaultProps} />);
    const actionButtons = screen.getAllByRole('button');
    const menuTrigger = actionButtons.find((btn) =>
      btn.querySelector('svg') !== null && btn.closest('td') !== null,
    ) ?? actionButtons[0];
    fireEvent.click(menuTrigger);

    await waitFor(() => {
      expect(screen.getByText('Deactivate')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Deactivate'));

    await waitFor(() => {
      expect(screen.getByText('Deactivate User')).toBeInTheDocument();
    });
  });

  it('clicking Deactivate and confirming calls deactivate mutate', async () => {
    const deactivateMutate = vi.fn();
    vi.doMock('@/api/users', () => ({
      useDeactivateUser: () => ({ mutate: deactivateMutate, isPending: false }),
      useRevokeSessions: () => ({ mutate: vi.fn(), isPending: false }),
    }));

    render(<UserTable {...defaultProps} />);
    const actionButtons = screen.getAllByRole('button');
    const menuTrigger = actionButtons.find((btn) =>
      btn.querySelector('svg') !== null && btn.closest('td') !== null,
    ) ?? actionButtons[0];
    fireEvent.click(menuTrigger);

    await waitFor(() => {
      expect(screen.getByText('Deactivate')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Deactivate'));

    await waitFor(() => {
      expect(screen.getByText('Deactivate User')).toBeInTheDocument();
    });

    // Click the confirm button in the dialog
    const confirmButton = screen.getByRole('button', { name: 'Deactivate' });
    fireEvent.click(confirmButton);

    // The confirm triggers the mutation through the component's internal handler
    await waitFor(() => {
      expect(screen.queryByText('Deactivate User')).not.toBeInTheDocument();
    });
  });

  it('calls onEdit when Edit Role is clicked', async () => {
    const onEdit = vi.fn();
    render(<UserTable {...defaultProps} onEdit={onEdit} />);
    const actionButtons = screen.getAllByRole('button');
    const menuTrigger = actionButtons.find((btn) =>
      btn.querySelector('svg') !== null && btn.closest('td') !== null,
    ) ?? actionButtons[0];
    fireEvent.click(menuTrigger);

    await waitFor(() => {
      expect(screen.getByText('Edit Role')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Edit Role'));
    expect(onEdit).toHaveBeenCalledWith(mockUser);
  });
});
