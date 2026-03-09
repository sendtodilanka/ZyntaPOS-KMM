import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { LicenseCreateForm } from '@/components/licenses/LicenseCreateForm';

const mockMutate = vi.fn();

vi.mock('@/api/licenses', () => ({
  useCreateLicense: () => ({ mutate: mockMutate, isPending: false }),
}));

describe('LicenseCreateForm', () => {
  beforeEach(() => {
    mockMutate.mockClear();
  });

  it('does not render when open is false', () => {
    const { container } = render(
      <LicenseCreateForm open={false} onClose={vi.fn()} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders when open is true', () => {
    render(<LicenseCreateForm open={true} onClose={vi.fn()} />);
    // The dialog heading "Create License" appears in an h3 element
    expect(screen.getByRole('heading', { name: 'Create License' })).toBeInTheDocument();
  });

  it('shows customer ID field', () => {
    render(<LicenseCreateForm open={true} onClose={vi.fn()} />);
    expect(screen.getByText('Customer ID')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('your-id-here')).toBeInTheDocument();
  });

  it('shows edition dropdown', () => {
    render(<LicenseCreateForm open={true} onClose={vi.fn()} />);
    expect(screen.getByText('Edition')).toBeInTheDocument();
    // The select element should contain the edition options
    expect(screen.getByDisplayValue('PROFESSIONAL')).toBeInTheDocument();
  });

  it('shows max devices field', () => {
    render(<LicenseCreateForm open={true} onClose={vi.fn()} />);
    expect(screen.getByText('Max Devices')).toBeInTheDocument();
    expect(screen.getByDisplayValue('3')).toBeInTheDocument();
  });

  it('submitting with empty required fields shows validation errors', async () => {
    render(<LicenseCreateForm open={true} onClose={vi.fn()} />);

    // Submit without filling in the customer ID
    const submitButton = screen.getByRole('button', { name: /create license/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Customer ID is required')).toBeInTheDocument();
    });

    // Mutation should not have been called
    expect(mockMutate).not.toHaveBeenCalled();
  });
});
