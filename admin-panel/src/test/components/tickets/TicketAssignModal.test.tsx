import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { TicketAssignModal } from '@/components/tickets/TicketAssignModal';
import type { Ticket } from '@/types/ticket';

const mockAssignMutate = vi.fn();

vi.mock('@/api/users', () => ({
  useAdminUsers: () => ({
    data: {
      data: [
        { id: 'user-1', name: 'Alice', email: 'alice@example.com', role: 'OPERATOR', isActive: true },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock('@/api/tickets', () => ({
  useAssignTicket: () => ({ mutate: mockAssignMutate, mutateAsync: vi.fn().mockResolvedValue({}), isPending: false }),
}));

const mockTicket: Ticket = {
  id: 'ticket-1',
  ticketNumber: 'TK-001',
  storeId: 'store-1',
  licenseId: null,
  createdBy: 'user-1',
  createdByName: 'Alice',
  customerName: 'John Doe',
  customerEmail: 'john@example.com',
  customerPhone: null,
  assignedTo: null,
  assignedToName: null,
  assignedAt: null,
  title: 'Printer not working',
  description: 'Cannot print receipts',
  category: 'HARDWARE',
  priority: 'HIGH',
  status: 'OPEN',
  resolvedBy: null,
  resolvedAt: null,
  resolutionNote: null,
  timeSpentMin: null,
  slaDueAt: null,
  slaBreached: false,
  createdAt: Date.now(),
  updatedAt: Date.now(),
};

const defaultProps = {
  ticket: mockTicket,
  open: true,
  onClose: vi.fn(),
};

describe('TicketAssignModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render when open is false', () => {
    render(<TicketAssignModal ticket={mockTicket} open={false} onClose={vi.fn()} />);
    expect(screen.queryByText('Assign Ticket')).not.toBeInTheDocument();
  });

  it('renders staff selection when open', () => {
    render(<TicketAssignModal {...defaultProps} />);
    expect(screen.getByText('Assign Ticket')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });

  it('shows staff user names in dropdown', () => {
    render(<TicketAssignModal {...defaultProps} />);
    // Alice is OPERATOR which is eligible for assignment
    expect(screen.getByText(/Alice/)).toBeInTheDocument();
  });

  it('Assign button is present', () => {
    render(<TicketAssignModal {...defaultProps} />);
    expect(screen.getByRole('button', { name: /assign/i })).toBeInTheDocument();
  });

  it('clicking Assign with no selection shows validation error', async () => {
    render(<TicketAssignModal {...defaultProps} />);
    const assignButton = screen.getByRole('button', { name: /^assign$/i });
    fireEvent.click(assignButton);
    await waitFor(() => {
      expect(screen.getByText(/please select an assignee/i)).toBeInTheDocument();
    });
  });

  it('shows the Assignee label', () => {
    render(<TicketAssignModal {...defaultProps} />);
    expect(screen.getByText('Assignee')).toBeInTheDocument();
  });

  it('renders Cancel button', () => {
    render(<TicketAssignModal {...defaultProps} />);
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  it('calls onClose when Cancel is clicked', () => {
    const onClose = vi.fn();
    render(<TicketAssignModal ticket={mockTicket} open={true} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });

  it('selecting a user and clicking Assign calls mutate', async () => {
    render(<TicketAssignModal {...defaultProps} />);
    const select = screen.getByRole('combobox');
    fireEvent.change(select, { target: { value: 'user-1' } });

    const assignButton = screen.getByRole('button', { name: /^assign$/i });
    fireEvent.click(assignButton);

    await waitFor(() => {
      expect(mockAssignMutate).toHaveBeenCalledWith(
        { id: 'ticket-1', body: { assigneeId: 'user-1' } },
        expect.any(Object),
      );
    });
  });
});
