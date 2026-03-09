import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { ForceSyncButton } from '@/components/sync/ForceSyncButton';

// Mutable refs so individual tests can override isPending / mutate behaviour
const mockMutate = vi.fn();
let mockIsPending = false;

vi.mock('@/api/sync', () => ({
  useForceSync: () => ({ mutate: mockMutate, isPending: mockIsPending }),
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
    isLoading,
  }: {
    open: boolean;
    onClose: () => void;
    onConfirm: () => void;
    title: string;
    isLoading?: boolean;
  }) =>
    open ? (
      <div data-testid="confirm-dialog">
        <span>{title}</span>
        <button data-testid="confirm-btn" onClick={onConfirm} disabled={isLoading}>
          {isLoading ? 'Processing…' : 'Confirm'}
        </button>
        <button data-testid="cancel-btn" onClick={onClose}>
          Cancel
        </button>
      </div>
    ) : null,
}));

const defaultProps = {
  storeId: 'store-1',
  storeName: 'Colombo Store',
};

describe('ForceSyncButton', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsPending = false;
  });

  it('renders Force Sync button', () => {
    render(<ForceSyncButton {...defaultProps} />);
    // The button text "Force Sync" is in a <span className="hidden sm:inline">
    // The button itself is still in the DOM
    const button = screen.getByRole('button');
    expect(button).toBeInTheDocument();
  });

  it('does not show confirmation dialog initially', () => {
    render(<ForceSyncButton {...defaultProps} />);
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
  });

  it('clicking button opens confirmation dialog', () => {
    render(<ForceSyncButton {...defaultProps} />);
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
  });

  it('confirmation dialog shows correct title', () => {
    render(<ForceSyncButton {...defaultProps} />);
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Force Re-sync')).toBeInTheDocument();
  });

  it('confirming calls the sync mutate with storeId', () => {
    render(<ForceSyncButton {...defaultProps} />);
    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByTestId('confirm-btn'));
    expect(mockMutate).toHaveBeenCalledWith(
      'store-1',
      expect.objectContaining({ onSettled: expect.any(Function) }),
    );
  });

  it('cancelling closes dialog without calling mutate', () => {
    render(<ForceSyncButton {...defaultProps} />);
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('cancel-btn'));
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
    expect(mockMutate).not.toHaveBeenCalled();
  });

  it('passes isPending to dialog during loading state', async () => {
    // Re-render with isPending = true by configuring the mock before render
    mockIsPending = true;
    render(<ForceSyncButton {...defaultProps} />);
    fireEvent.click(screen.getByRole('button'));
    await waitFor(() => {
      const confirmBtn = screen.getByTestId('confirm-btn');
      expect(confirmBtn).toBeDisabled();
    });
  });

  it('applies optional className to the trigger button', () => {
    render(<ForceSyncButton {...defaultProps} className="custom-class" />);
    const button = screen.getByRole('button');
    expect(button.className).toContain('custom-class');
  });
});
