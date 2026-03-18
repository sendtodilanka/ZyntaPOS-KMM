import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { TicketCommentThread } from '@/components/tickets/TicketCommentThread';

const mockAddCommentMutate = vi.fn();

// Mutable permission control — toggled per test via hasPermissionImpl
let hasPermissionImpl: (permission: string) => boolean = () => true;

vi.mock('@/api/tickets', () => ({
  useTicketComments: () => ({
    data: [
      {
        id: 'c1',
        ticketId: 'ticket-1',
        authorId: 'user-1',
        authorName: 'Alice',
        body: 'Hello world',
        isInternal: false,
        createdAt: Date.now(),
      },
    ],
    isLoading: false,
  }),
  useAddComment: () => ({
    mutate: mockAddCommentMutate,
    mutateAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
  }),
}));

vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({
    hasPermission: (permission: string) => hasPermissionImpl(permission),
    user: { id: 'user-1', name: 'Alice', role: 'ADMIN' },
  }),
}));

vi.mock('@/hooks/use-timezone', () => ({
  useTimezone: () => ({
    formatRelative: (_ms: number) => 'just now',
    formatDateTime: (_ms: number) => '1 Jan 2025 10:00',
    formatDate: (_ms: number) => '1 Jan 2025',
  }),
}));

describe('TicketCommentThread', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: user has all permissions
    hasPermissionImpl = () => true;
  });

  it('renders existing comment body text', () => {
    render(<TicketCommentThread ticketId="ticket-1" />);
    expect(screen.getByText('Hello world')).toBeInTheDocument();
  });

  it('renders comment author name', () => {
    render(<TicketCommentThread ticketId="ticket-1" />);
    expect(screen.getByText('Alice')).toBeInTheDocument();
  });

  it('shows comment form when user has tickets:comment permission', () => {
    hasPermissionImpl = () => true;
    render(<TicketCommentThread ticketId="ticket-1" />);
    expect(screen.getByPlaceholderText(/add a comment/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add comment/i })).toBeInTheDocument();
  });

  it('submitting with comment text calls useAddComment mutate', async () => {
    hasPermissionImpl = () => true;
    render(<TicketCommentThread ticketId="ticket-1" />);
    const textarea = screen.getByPlaceholderText(/add a comment/i);
    fireEvent.change(textarea, { target: { value: 'This is a new comment' } });

    const submitButton = screen.getByRole('button', { name: /add comment/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mockAddCommentMutate).toHaveBeenCalledWith(
        {
          ticketId: 'ticket-1',
          body: { body: 'This is a new comment', isInternal: false, replyToCustomer: false },
        },
        expect.any(Object),
      );
    });
  });

  it('submit button is disabled when comment textarea is empty', () => {
    hasPermissionImpl = () => true;
    render(<TicketCommentThread ticketId="ticket-1" />);
    const submitButton = screen.getByRole('button', { name: /add comment/i });
    expect(submitButton).toBeDisabled();
  });

  it('when user does NOT have tickets:comment permission, comment form is hidden', () => {
    hasPermissionImpl = (permission: string) => permission !== 'tickets:comment';
    render(<TicketCommentThread ticketId="ticket-1" />);
    expect(screen.queryByPlaceholderText(/add a comment/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /add comment/i })).not.toBeInTheDocument();
  });

  it('comment body is still visible even without comment permission', () => {
    hasPermissionImpl = (permission: string) => permission !== 'tickets:comment';
    render(<TicketCommentThread ticketId="ticket-1" />);
    expect(screen.getByText('Hello world')).toBeInTheDocument();
  });

  it('shows comment count in section header', () => {
    render(<TicketCommentThread ticketId="ticket-1" />);
    expect(screen.getByText(/comments \(1\)/i)).toBeInTheDocument();
  });

  it('renders author initials avatar', () => {
    render(<TicketCommentThread ticketId="ticket-1" />);
    // "Alice" -> first letter of each word -> "A"
    expect(screen.getByText('A')).toBeInTheDocument();
  });
});
