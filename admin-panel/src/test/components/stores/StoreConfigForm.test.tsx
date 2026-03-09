import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { StoreConfigForm } from '@/components/stores/StoreConfigForm';
import type { StoreConfig } from '@/types/store';

const mockMutate = vi.fn();

vi.mock('@/api/stores', () => ({
  useUpdateStoreConfig: () => ({ mutate: mockMutate, isPending: false }),
}));

const mockConfig: StoreConfig = {
  storeId: 'store-1',
  taxRates: [],
  featureFlags: {},
  timezone: 'Asia/Colombo',
  currency: 'LKR',
  receiptFooter: 'Thank you!',
  syncIntervalSeconds: 60,
  updatedAt: new Date().toISOString(),
};

describe('StoreConfigForm', () => {
  beforeEach(() => {
    mockMutate.mockClear();
  });

  it('renders timezone field with current value', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    const timezoneInput = screen.getByDisplayValue('Asia/Colombo');
    expect(timezoneInput).toBeInTheDocument();
  });

  it('renders currency field with current value', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    const currencyInput = screen.getByDisplayValue('LKR');
    expect(currencyInput).toBeInTheDocument();
  });

  it('renders receipt footer textarea', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    const textarea = screen.getByDisplayValue('Thank you!');
    expect(textarea).toBeInTheDocument();
  });

  it('Save button is present', () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);
    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
  });

  it('submitting the form calls useUpdateStoreConfig mutation', async () => {
    render(<StoreConfigForm storeId="store-1" config={mockConfig} />);

    // Dirty the form so the save button is enabled
    const timezoneInput = screen.getByDisplayValue('Asia/Colombo');
    fireEvent.change(timezoneInput, { target: { value: 'Asia/Kolkata' } });

    const saveButton = screen.getByRole('button', { name: /save changes/i });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalledWith(
        expect.objectContaining({
          storeId: 'store-1',
          config: expect.objectContaining({ timezone: 'Asia/Kolkata' }),
        }),
      );
    });
  });
});
