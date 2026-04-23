import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import React from 'react';
import type { Ticket, TicketsPage, TicketMetrics } from '@/types/ticket';

// H-002: TicketsPage now reads filters from the URL via Route.useSearch()
// and routes page+filter updates through useNavigate. The mock keeps a
// module-level search object that useNavigate mutates when tests change
// filters, so the select `value=` prop reflects the update and assertions
// about "the dropdown shows HIGH" continue to pass.
let mockSearch: Record<string, unknown> = {};
const resetMockSearch = () => { mockSearch = {}; };
const mockNavigate = vi.fn((opts: { search?: ((prev: Record<string, unknown>) => Record<string, unknown>) | Record<string, unknown> }) => {
  if (typeof opts.search === 'function') {
    mockSearch = opts.search(mockSearch);
  } else if (opts.search) {
    mockSearch = { ...mockSearch, ...opts.search };
  }
});

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (opts: Record<string, unknown>) => ({
    ...opts,
    useSearch: () => mockSearch,
    fullPath: '/tickets/',
  }),
  useNavigate: () => mockNavigate,
}));

vi.mock('@/hooks/use-debounce', () => ({
  useDebounce: (val: unknown) => val,
}));

// Mock complex components
vi.mock('@/components/tickets/TicketTable', () => ({
  TicketTable: ({ data }: { data: Ticket[] }) => (
    <div data-testid="ticket-table">
      {data.map((ticket) => (
        <div key={ticket.id}>
          <span>{ticket.title}</span>
          <span>{ticket.storeId}</span>
          <span>{ticket.status}</span>
        </div>
      ))}
    </div>
  ),
}));

vi.mock('@/components/tickets/TicketCreateModal', () => ({
  TicketCreateModal: ({ open, onClose }: { open: boolean; onClose: () => void }) =>
    open ? (
      <div role="dialog" aria-label="Create Ticket">
        <button onClick={onClose}>Cancel</button>
      </div>
    ) : null,
}));

vi.mock('@/components/shared/SearchInput', () => ({
  SearchInput: ({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) => (
    <input
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
    />
  ),
}));

vi.mock('@/api/tickets');
vi.mock('@/hooks/use-auth', () => ({
  useAuth: () => ({ hasPermission: (_p: string) => true }),
}));

import { useTickets, useTicketMetrics } from '@/api/tickets';
import { Route } from '@/routes/tickets/index';

const TicketsPage = (Route as unknown as { component: React.FC }).component;

const mockTicket: Ticket = {
  id: 'ticket-1',
  ticketNumber: 'TKT-0001',
  storeId: 'store-1',
  licenseId: null,
  createdBy: 'user-1',
  createdByName: 'Manager',
  customerName: 'Store Manager',
  customerEmail: 'manager@store.com',
  customerPhone: null,
  assignedTo: null,
  assignedToName: null,
  assignedAt: null,
  title: 'POS not syncing',
  description: 'The POS is not syncing with the server.',
  category: 'SYNC',
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

const mockTicketPage: TicketsPage = {
  items: [mockTicket],
  total: 1,
  page: 0,
  size: 20,
};

const mockMetrics: TicketMetrics = {
  totalOpen: 5,
  totalAssigned: 2,
  totalResolved: 18,
  totalClosed: 4,
  slaBreached: 1,
  avgResolutionTimeMin: 270,
  openByPriority: {},
  openByCategory: {},
};

describe('TicketsPage', () => {
  beforeEach(() => {
    resetMockSearch();
    mockNavigate.mockClear();
    vi.mocked(useTickets).mockReturnValue({
      data: mockTicketPage,
      isLoading: false,
    } as ReturnType<typeof useTickets>);

    vi.mocked(useTicketMetrics).mockReturnValue({
      data: mockMetrics,
      isLoading: false,
    } as ReturnType<typeof useTicketMetrics>);
  });

  it('renders page heading', () => {
    render(<TicketsPage />);
    expect(screen.getByText(/support tickets/i)).toBeInTheDocument();
  });

  it('renders ticket count in subtitle', () => {
    render(<TicketsPage />);
    expect(screen.getByText(/1 tickets total/i)).toBeInTheDocument();
  });

  it('renders search input', () => {
    render(<TicketsPage />);
    expect(screen.getByPlaceholderText(/search tickets/i)).toBeInTheDocument();
  });

  it('renders status filter', () => {
    render(<TicketsPage />);
    expect(screen.getByRole('combobox', { name: /status/i })).toBeInTheDocument();
  });

  it('renders priority filter', () => {
    render(<TicketsPage />);
    expect(screen.getByRole('combobox', { name: /priority/i })).toBeInTheDocument();
  });

  it('renders category filter', () => {
    render(<TicketsPage />);
    expect(screen.getByRole('combobox', { name: /category/i })).toBeInTheDocument();
  });

  it('renders create ticket button for users with permission', () => {
    render(<TicketsPage />);
    expect(screen.getByRole('button', { name: /new ticket/i })).toBeInTheDocument();
  });

  it('renders ticket title in table', () => {
    render(<TicketsPage />);
    expect(screen.getByText('POS not syncing')).toBeInTheDocument();
  });

  it('renders metrics summary', () => {
    render(<TicketsPage />);
    // Open count from metrics (totalOpen: 5)
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('filters by status on select change', () => {
    render(<TicketsPage />);
    const select = screen.getByRole('combobox', { name: /status/i });
    fireEvent.change(select, { target: { value: 'OPEN' } });
    // H-002: filter now lives in the URL. Assert navigate was called with
    // the correct status patch instead of checking the (controlled) select
    // value — a real router drives re-renders via useSearch which the test
    // mock cannot simulate.
    expect(mockNavigate).toHaveBeenCalledWith(expect.objectContaining({
      search: expect.any(Function),
      replace: true,
    }));
    // Execute the search updater to verify the patch it produces.
    const lastCall = mockNavigate.mock.calls.at(-1)![0] as {
      search: (prev: Record<string, unknown>) => Record<string, unknown>;
    };
    expect(lastCall.search({})).toEqual(expect.objectContaining({ status: 'OPEN' }));
  });

  it('filters by priority on select change', () => {
    render(<TicketsPage />);
    const select = screen.getByRole('combobox', { name: /priority/i });
    fireEvent.change(select, { target: { value: 'HIGH' } });
    expect(mockNavigate).toHaveBeenCalled();
    const lastCall = mockNavigate.mock.calls.at(-1)![0] as {
      search: (prev: Record<string, unknown>) => Record<string, unknown>;
    };
    expect(lastCall.search({})).toEqual(expect.objectContaining({ priority: 'HIGH' }));
  });

  it('shows 0 total when data undefined', () => {
    vi.mocked(useTickets).mockReturnValue({
      data: undefined,
      isLoading: false,
    } as ReturnType<typeof useTickets>);
    render(<TicketsPage />);
    expect(screen.getByText(/0 tickets total/i)).toBeInTheDocument();
  });

  it('opens create modal when New Ticket clicked', () => {
    render(<TicketsPage />);
    fireEvent.click(screen.getByRole('button', { name: /new ticket/i }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
