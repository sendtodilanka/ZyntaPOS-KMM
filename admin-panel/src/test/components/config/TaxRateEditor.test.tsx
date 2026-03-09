import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { TaxRateEditor } from '@/components/config/TaxRateEditor';
import type { TaxRate } from '@/types/config';

const mockCreateRate = vi.fn().mockResolvedValue({});
const mockUpdateRate = vi.fn().mockResolvedValue({});
const mockDeleteRate = vi.fn();

const mockTaxRate: TaxRate = {
  id: 'tax-1',
  name: 'VAT',
  rate: 15,
  description: 'Value Added Tax',
  applicableTo: ['all'],
  isDefault: true,
  country: 'LK',
  active: true,
};

vi.mock('@/api/config', () => ({
  useTaxRates: () => ({
    data: [mockTaxRate],
    isLoading: false,
  }),
  useCreateTaxRate: () => ({
    mutate: mockCreateRate,
    isPending: false,
  }),
  useUpdateTaxRate: () => ({
    mutate: mockUpdateRate,
    isPending: false,
  }),
  useDeleteTaxRate: () => ({
    mutate: mockDeleteRate,
    isPending: false,
  }),
}));

vi.mock('@/lib/utils', () => ({
  cn: (...args: unknown[]) => args.filter(Boolean).join(' '),
}));

vi.mock('@/components/shared/ConfirmDialog', () => ({
  ConfirmDialog: ({
    open,
    onClose,
    onConfirm,
    title,
  }: {
    open: boolean;
    onClose: () => void;
    onConfirm: () => void;
    title: string;
  }) =>
    open ? (
      <div data-testid="confirm-dialog">
        <span>{title}</span>
        <button data-testid="confirm-btn" onClick={onConfirm}>Confirm</button>
        <button data-testid="cancel-btn" onClick={onClose}>Cancel</button>
      </div>
    ) : null,
}));

// react-hook-form uses zod resolver; mock both to avoid environment issues
vi.mock('react-hook-form', async () => {
  const actual = await vi.importActual<typeof import('react-hook-form')>('react-hook-form');
  return actual;
});

vi.mock('@hookform/resolvers/zod', () => ({
  zodResolver: () => async (values: unknown) => ({ values, errors: {} }),
}));

describe('TaxRateEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders existing tax rate name (VAT)', () => {
    render(<TaxRateEditor />);
    expect(screen.getByText('VAT')).toBeInTheDocument();
  });

  it('shows rate percentage', () => {
    render(<TaxRateEditor />);
    expect(screen.getByText('15%')).toBeInTheDocument();
  });

  it('shows tax rate description', () => {
    render(<TaxRateEditor />);
    expect(screen.getByText(/Value Added Tax/)).toBeInTheDocument();
  });

  it('shows Default badge for default tax rate', () => {
    render(<TaxRateEditor />);
    expect(screen.getByText('Default')).toBeInTheDocument();
  });

  it('shows configured rate count', () => {
    render(<TaxRateEditor />);
    expect(screen.getByText('1 tax rates configured')).toBeInTheDocument();
  });

  it('renders "Add Rate" button', () => {
    render(<TaxRateEditor />);
    expect(screen.getByText('Add Rate')).toBeInTheDocument();
  });

  it('clicking "Add Rate" shows inline form', async () => {
    render(<TaxRateEditor />);
    fireEvent.click(screen.getByText('Add Rate'));
    await waitFor(() => {
      expect(screen.getByText('New Tax Rate')).toBeInTheDocument();
    });
  });

  it('form has Name and Rate fields when opened', async () => {
    render(<TaxRateEditor />);
    fireEvent.click(screen.getByText('Add Rate'));
    await waitFor(() => {
      expect(screen.getByPlaceholderText('VAT, GST...')).toBeInTheDocument();
    });
  });

  it('Cancel button in form hides the form', async () => {
    render(<TaxRateEditor />);
    fireEvent.click(screen.getByText('Add Rate'));
    await waitFor(() => screen.getByText('New Tax Rate'));
    fireEvent.click(screen.getByText('Cancel'));
    await waitFor(() => {
      expect(screen.queryByText('New Tax Rate')).not.toBeInTheDocument();
    });
  });

  it('Edit button (pencil icon) opens the form in edit mode', async () => {
    render(<TaxRateEditor />);
    const editButton = screen.getByLabelText('Edit');
    fireEvent.click(editButton);
    await waitFor(() => {
      expect(screen.getByText('Edit Tax Rate')).toBeInTheDocument();
    });
  });

  it('Delete button opens confirmation dialog', async () => {
    render(<TaxRateEditor />);
    const deleteButton = screen.getByLabelText('Delete');
    fireEvent.click(deleteButton);
    await waitFor(() => {
      expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
      expect(screen.getByText('Delete Tax Rate')).toBeInTheDocument();
    });
  });

  it('confirming delete calls deleteRate mutation', async () => {
    render(<TaxRateEditor />);
    fireEvent.click(screen.getByLabelText('Delete'));
    await waitFor(() => screen.getByTestId('confirm-btn'));
    fireEvent.click(screen.getByTestId('confirm-btn'));
    expect(mockDeleteRate).toHaveBeenCalledWith('tax-1');
  });

  it('cancelling delete closes dialog without calling deleteRate', async () => {
    render(<TaxRateEditor />);
    fireEvent.click(screen.getByLabelText('Delete'));
    await waitFor(() => screen.getByTestId('cancel-btn'));
    fireEvent.click(screen.getByTestId('cancel-btn'));
    expect(mockDeleteRate).not.toHaveBeenCalled();
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
  });
});
