import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import { EmptyState } from '@/components/shared/EmptyState';

describe('EmptyState', () => {
  it('renders title text', () => {
    render(<EmptyState title="No items found" />);
    expect(screen.getByText('No items found')).toBeInTheDocument();
  });

  it('renders description when provided', () => {
    render(
      <EmptyState
        title="No items found"
        description="Try adjusting your filters."
      />,
    );
    expect(screen.getByText('Try adjusting your filters.')).toBeInTheDocument();
  });

  it('does not render description when omitted', () => {
    render(<EmptyState title="Nothing here" />);
    // Only title should be rendered; no secondary paragraph
    expect(screen.queryByText(/Try/)).not.toBeInTheDocument();
    expect(screen.getByText('Nothing here')).toBeInTheDocument();
  });

  it('renders action node when provided', () => {
    render(
      <EmptyState
        title="Empty"
        action={<button>Add item</button>}
      />,
    );
    expect(screen.getByRole('button', { name: 'Add item' })).toBeInTheDocument();
  });

  it('clicking action calls the provided handler', () => {
    const handleClick = vi.fn();
    render(
      <EmptyState
        title="Empty"
        action={<button onClick={handleClick}>Add item</button>}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Add item' }));
    expect(handleClick).toHaveBeenCalledOnce();
  });

  it('renders default icon container', () => {
    const { container } = render(<EmptyState title="No data" />);
    // The icon wrapper div is always rendered
    const iconWrapper = container.querySelector('.rounded-2xl');
    expect(iconWrapper).toBeInTheDocument();
  });
});
