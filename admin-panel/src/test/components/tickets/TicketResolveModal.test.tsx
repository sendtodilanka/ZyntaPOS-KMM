import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { TicketResolveModal } from '@/components/tickets/TicketResolveModal';
import type { Ticket } from '@/types/ticket';

const mockResolveMutate = vi.fn();

vi.mock('@/api/tickets', () => ({
  useResolveTicket: () => ({ mutate: mockResolveMutate, mutateAsync: vi.fn().mockResolvedValue({}), isPending: false }),
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
  assignedTo: 'user-2',
  assignedToName: 'Bob',
  assignedAt: Date.now() - 3600000,
  title: 'Printer not working',
  description: 'Cannot print receipts',
  category: 'HARDWARE',
  priority: 'HIGH',
  status: 'IN_PROGRESS',
  resolvedBy: null,
  resolvedAt: null,
  resolutionNote: null,
  timeSpentMin: null,
  slaDueAt: null,
  slaBreached: false,
  createdAt: Date.now() - 86400000,
  updatedAt: Date.now(),
};

const defaultProps = {
  ticket: mockTicket,
  open: true,
  onClose: vi.fn(),
};

describe('TicketResolveModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render when open is false', () => {
    render(<TicketResolveModal ticket={mockTicket} open={false} onClose={vi.fn()} />);
    expect(screen.queryByText('Resolve Ticket')).not.toBeInTheDocument();
  });

  it('renders resolution note textarea and time spent field when open', () => {
    render(<TicketResolveModal {...defaultProps} />);
    expect(screen.getByText('Resolve Ticket')).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/describe how the issue was resolved/i)).toBeInTheDocument();
    expect(screen.getByRole('spinbutton')).toBeInTheDocument();
  });

  it('shows Resolution Note label', () => {
    render(<TicketResolveModal {...defaultProps} />);
    expect(screen.getByText(/resolution note/i)).toBeInTheDocument();
  });

  it('shows Time Spent label', () => {
    render(<TicketResolveModal {...defaultProps} />);
    expect(screen.getByText(/time spent/i)).toBeInTheDocument();
  });

  it('submitting without resolution note shows validation error', async () => {
    render(<TicketResolveModal {...defaultProps} />);

    // Clear the time spent input so we test resolution note
    const timeSpentInput = screen.getByRole('spinbutton');
    fireEvent.change(timeSpentInput, { target: { value: '30' } });

    const submitButton = screen.getByRole('button', { name: /mark resolved/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/resolution note is required/i)).toBeInTheDocument();
    });
  });

  it('submitting without time spent shows validation error', async () => {
    render(<TicketResolveModal {...defaultProps} />);

    const resolutionNoteTextarea = screen.getByPlaceholderText(/describe how the issue was resolved/i);
    fireEvent.change(resolutionNoteTextarea, { target: { value: 'Fixed the printer driver' } });

    const timeSpentInput = screen.getByRole('spinbutton');
    fireEvent.change(timeSpentInput, { target: { value: '' } });

    const submitButton = screen.getByRole('button', { name: /mark resolved/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/must be a number|must be at least 1 minute/i)).toBeInTheDocument();
    });
  });

  it('valid submission calls useResolveTicket mutate', async () => {
    render(<TicketResolveModal {...defaultProps} />);

    const resolutionNoteTextarea = screen.getByPlaceholderText(/describe how the issue was resolved/i);
    fireEvent.change(resolutionNoteTextarea, { target: { value: 'Fixed the printer driver' } });

    const timeSpentInput = screen.getByRole('spinbutton');
    fireEvent.change(timeSpentInput, { target: { value: '45' } });

    const submitButton = screen.getByRole('button', { name: /mark resolved/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mockResolveMutate).toHaveBeenCalledWith(
        {
          id: 'ticket-1',
          body: {
            resolutionNote: 'Fixed the printer driver',
            timeSpentMin: 45,
          },
        },
        expect.any(Object),
      );
    });
  });

  it('shows ticket number in the header', () => {
    render(<TicketResolveModal {...defaultProps} />);
    expect(screen.getByText('TK-001')).toBeInTheDocument();
  });

  it('renders Cancel button', () => {
    render(<TicketResolveModal {...defaultProps} />);
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  it('calls onClose when Cancel is clicked', () => {
    const onClose = vi.fn();
    render(<TicketResolveModal ticket={mockTicket} open={true} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });

  it('time spent input has default value of 30', () => {
    render(<TicketResolveModal {...defaultProps} />);
    const timeSpentInput = screen.getByRole('spinbutton') as HTMLInputElement;
    expect(timeSpentInput.value).toBe('30');
  });
});
