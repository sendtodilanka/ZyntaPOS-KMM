import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { TicketCreateModal } from '@/components/tickets/TicketCreateModal';

const mockCreateMutate = vi.fn();

vi.mock('@/api/tickets', () => ({
  useCreateTicket: () => ({ mutate: mockCreateMutate, mutateAsync: vi.fn().mockResolvedValue({}), isPending: false }),
}));

vi.mock('@/stores/ui-store', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

const defaultProps = {
  open: true,
  onClose: vi.fn(),
};

describe('TicketCreateModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render when open is false', () => {
    render(<TicketCreateModal open={false} onClose={vi.fn()} />);
    expect(screen.queryByText('New Support Ticket')).not.toBeInTheDocument();
  });

  it('renders when open is true', () => {
    render(<TicketCreateModal {...defaultProps} />);
    expect(screen.getByText('New Support Ticket')).toBeInTheDocument();
  });

  it('shows category selection', () => {
    render(<TicketCreateModal {...defaultProps} />);
    expect(screen.getAllByText(/category/i).length).toBeGreaterThan(0);
    const categorySelect = screen.getAllByRole('combobox')[0];
    expect(categorySelect).toBeInTheDocument();
  });

  it('shows title field', () => {
    render(<TicketCreateModal {...defaultProps} />);
    const titleInput = screen.getByPlaceholderText(/brief summary of the issue/i);
    expect(titleInput).toBeInTheDocument();
  });

  it('shows priority selection', () => {
    render(<TicketCreateModal {...defaultProps} />);
    expect(screen.getByText(/priority/i)).toBeInTheDocument();
    const selects = screen.getAllByRole('combobox');
    // There are category, subcategory (issue type), and priority selects
    expect(selects.length).toBeGreaterThanOrEqual(2);
  });

  it('shows customer name field', () => {
    render(<TicketCreateModal {...defaultProps} />);
    const customerNameInput = screen.getByPlaceholderText(/john doe/i);
    expect(customerNameInput).toBeInTheDocument();
  });

  it('submitting with empty required fields shows validation errors', async () => {
    render(<TicketCreateModal {...defaultProps} />);
    const submitButton = screen.getByRole('button', { name: /create ticket/i });
    fireEvent.click(submitButton);
    await waitFor(() => {
      // customerName and description are required; they should trigger validation
      expect(screen.getByText(/customer name is required/i)).toBeInTheDocument();
    });
  });

  it('submitting with empty description shows validation error', async () => {
    render(<TicketCreateModal {...defaultProps} />);

    const customerNameInput = screen.getByPlaceholderText(/john doe/i);
    fireEvent.change(customerNameInput, { target: { value: 'Test Customer' } });

    const titleInput = screen.getByPlaceholderText(/brief summary of the issue/i);
    fireEvent.change(titleInput, { target: { value: 'Test issue title' } });

    const submitButton = screen.getByRole('button', { name: /create ticket/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/description is required/i)).toBeInTheDocument();
    });
  });

  it('shows description textarea', () => {
    render(<TicketCreateModal {...defaultProps} />);
    const descriptionTextarea = screen.getByPlaceholderText(/describe the issue in detail/i);
    expect(descriptionTextarea).toBeInTheDocument();
  });

  it('renders Cancel button', () => {
    render(<TicketCreateModal {...defaultProps} />);
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  it('calls onClose when Cancel is clicked', () => {
    const onClose = vi.fn();
    render(<TicketCreateModal open={true} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });

  it('shows priority options including High and Critical', () => {
    render(<TicketCreateModal {...defaultProps} />);
    expect(screen.getByText(/high — core function broken/i)).toBeInTheDocument();
    expect(screen.getByText(/critical — complete outage/i)).toBeInTheDocument();
  });
});
