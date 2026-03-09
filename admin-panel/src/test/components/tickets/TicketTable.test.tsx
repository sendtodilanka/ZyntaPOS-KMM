import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { TicketTable } from '@/components/tickets/TicketTable';
import type { Ticket } from '@/types/ticket';

const mockNavigate = vi.fn();

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

vi.mock('@/hooks/use-timezone', () => ({
  useTimezone: () => ({
    formatRelative: (_ms: number) => 'just now',
    formatDateTime: (_ms: number) => '1 Jan 2025 10:00',
    formatDate: (_ms: number) => '1 Jan 2025',
  }),
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
  data: [mockTicket],
  isLoading: false,
  page: 1,
  totalPages: 1,
  total: 1,
  onPageChange: vi.fn(),
};

describe('TicketTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders ticket number and title', () => {
    render(<TicketTable {...defaultProps} />);
    expect(screen.getByText('TK-001')).toBeInTheDocument();
    expect(screen.getByText('Printer not working')).toBeInTheDocument();
  });

  it('renders priority badge for HIGH', () => {
    render(<TicketTable {...defaultProps} />);
    expect(screen.getByText('HIGH')).toBeInTheDocument();
  });

  it('renders status badge for OPEN', () => {
    render(<TicketTable {...defaultProps} />);
    expect(screen.getByText('Open')).toBeInTheDocument();
  });

  it('shows customer name in the table', () => {
    render(<TicketTable {...defaultProps} />);
    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });

  it('shows empty state when data is empty', () => {
    render(<TicketTable {...defaultProps} data={[]} total={0} />);
    expect(screen.getByText('No tickets found')).toBeInTheDocument();
  });

  it('clicking a row triggers navigation to ticket detail', async () => {
    render(<TicketTable {...defaultProps} />);
    const ticketNumberCell = screen.getByText('TK-001');
    const row = ticketNumberCell.closest('tr');
    expect(row).not.toBeNull();
    fireEvent.click(row!);
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith({
        to: '/tickets/$ticketId',
        params: { ticketId: 'ticket-1' },
      });
    });
  });

  it('renders ticket title as truncated text', () => {
    render(<TicketTable {...defaultProps} />);
    const titleEl = screen.getByText('Printer not working');
    expect(titleEl).toBeInTheDocument();
  });

  it('renders CRITICAL priority badge', () => {
    const criticalTicket = { ...mockTicket, id: 'ticket-2', priority: 'CRITICAL' as const };
    render(<TicketTable {...defaultProps} data={[criticalTicket]} />);
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
  });

  it('renders MEDIUM priority badge', () => {
    const mediumTicket = { ...mockTicket, id: 'ticket-3', priority: 'MEDIUM' as const };
    render(<TicketTable {...defaultProps} data={[mediumTicket]} />);
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
  });

  it('renders RESOLVED status badge', () => {
    const resolvedTicket = { ...mockTicket, id: 'ticket-4', status: 'RESOLVED' as const };
    render(<TicketTable {...defaultProps} data={[resolvedTicket]} />);
    expect(screen.getByText('Resolved')).toBeInTheDocument();
  });

  it('shows Unassigned when no assignee', () => {
    render(<TicketTable {...defaultProps} />);
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('shows assignee name when ticket is assigned', () => {
    const assignedTicket = {
      ...mockTicket,
      id: 'ticket-5',
      assignedTo: 'user-2',
      assignedToName: 'Bob Jones',
    };
    render(<TicketTable {...defaultProps} data={[assignedTicket]} />);
    expect(screen.getByText('Bob Jones')).toBeInTheDocument();
  });
});
