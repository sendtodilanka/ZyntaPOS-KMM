import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';

describe('ConfirmDialog', () => {
  it('does not render when closed', () => {
    render(
      <ConfirmDialog
        open={false}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        title="Delete item"
        description="Are you sure?"
      />,
    );
    expect(screen.queryByText('Delete item')).not.toBeInTheDocument();
  });

  it('renders title and description when open', () => {
    render(
      <ConfirmDialog
        open
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        title="Delete item"
        description="This cannot be undone."
      />,
    );
    expect(screen.getByText('Delete item')).toBeInTheDocument();
    expect(screen.getByText('This cannot be undone.')).toBeInTheDocument();
  });

  it('calls onConfirm when confirm button clicked', () => {
    const onConfirm = vi.fn();
    render(
      <ConfirmDialog
        open
        onClose={vi.fn()}
        onConfirm={onConfirm}
        title="Delete"
        description="Sure?"
        confirmLabel="Delete"
      />,
    );
    fireEvent.click(screen.getByText('Delete'));
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it('calls onClose when cancel button clicked', () => {
    const onClose = vi.fn();
    render(
      <ConfirmDialog
        open
        onClose={onClose}
        onConfirm={vi.fn()}
        title="Delete"
        description="Sure?"
      />,
    );
    fireEvent.click(screen.getByText('Cancel'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('shows loading state', () => {
    render(
      <ConfirmDialog
        open
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        title="Delete"
        description="Sure?"
        isLoading
      />,
    );
    expect(screen.getByText('Processing…')).toBeInTheDocument();
  });
});
