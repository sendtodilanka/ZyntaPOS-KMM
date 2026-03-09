import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import { ToastContainer } from '@/components/shared/ToastContainer';
import { useUiStore } from '@/stores/ui-store';

// Reset toast list before each test so store state from previous tests does not leak
beforeEach(() => {
  useUiStore.setState({ toasts: [] });
});

describe('ToastContainer', () => {
  it('renders nothing when there are no toasts', () => {
    const { container } = render(<ToastContainer />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a toast message when a toast is added to the store', () => {
    useUiStore.setState({
      toasts: [
        { id: 't1', title: 'Hello world', variant: 'default' },
      ],
    });

    render(<ToastContainer />);
    expect(screen.getByText('Hello world')).toBeInTheDocument();
  });

  it('renders toast title and description', () => {
    useUiStore.setState({
      toasts: [
        {
          id: 't2',
          title: 'Operation complete',
          description: 'Your changes have been saved.',
          variant: 'success',
        },
      ],
    });

    render(<ToastContainer />);
    expect(screen.getByText('Operation complete')).toBeInTheDocument();
    expect(screen.getByText('Your changes have been saved.')).toBeInTheDocument();
  });

  it('renders multiple toasts simultaneously', () => {
    useUiStore.setState({
      toasts: [
        { id: 't3', title: 'First toast', variant: 'default' },
        { id: 't4', title: 'Second toast', variant: 'error' },
      ],
    });

    render(<ToastContainer />);
    expect(screen.getByText('First toast')).toBeInTheDocument();
    expect(screen.getByText('Second toast')).toBeInTheDocument();
  });

  it('success variant toast has role="alert"', () => {
    useUiStore.setState({
      toasts: [
        { id: 't5', title: 'Saved!', variant: 'success' },
      ],
    });

    render(<ToastContainer />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('error variant toast has role="alert"', () => {
    useUiStore.setState({
      toasts: [
        { id: 't6', title: 'Something failed', variant: 'error' },
      ],
    });

    render(<ToastContainer />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('warning variant toast has role="alert"', () => {
    useUiStore.setState({
      toasts: [
        { id: 't7', title: 'Watch out', variant: 'warning' },
      ],
    });

    render(<ToastContainer />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('dismisses a toast when the dismiss button is clicked', () => {
    useUiStore.setState({
      toasts: [
        { id: 't8', title: 'Dismissible toast', variant: 'default' },
      ],
    });

    render(<ToastContainer />);
    expect(screen.getByText('Dismissible toast')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('Dismiss'));

    // Toast should be removed from the store and the DOM
    expect(screen.queryByText('Dismissible toast')).not.toBeInTheDocument();
  });

  it('only dismisses the clicked toast when multiple are present', () => {
    useUiStore.setState({
      toasts: [
        { id: 't9', title: 'Keep this', variant: 'success' },
        { id: 't10', title: 'Remove this', variant: 'error' },
      ],
    });

    render(<ToastContainer />);
    const dismissButtons = screen.getAllByLabelText('Dismiss');
    // Click dismiss on the second toast
    fireEvent.click(dismissButtons[1]);

    expect(screen.getByText('Keep this')).toBeInTheDocument();
    expect(screen.queryByText('Remove this')).not.toBeInTheDocument();
  });

  it('success toast does not carry error styling class', () => {
    useUiStore.setState({
      toasts: [
        { id: 't11', title: 'Great job', variant: 'success' },
      ],
    });

    render(<ToastContainer />);
    const alert = screen.getByRole('alert');
    // Success toasts use emerald colour tokens, not red ones
    expect(alert.className).toContain('emerald');
    expect(alert.className).not.toContain('red-');
  });

  it('error toast carries error styling class', () => {
    useUiStore.setState({
      toasts: [
        { id: 't12', title: 'Error occurred', variant: 'error' },
      ],
    });

    render(<ToastContainer />);
    const alert = screen.getByRole('alert');
    expect(alert.className).toContain('red-');
  });

  it('warning toast carries warning styling class', () => {
    useUiStore.setState({
      toasts: [
        { id: 't13', title: 'Low storage', variant: 'warning' },
      ],
    });

    render(<ToastContainer />);
    const alert = screen.getByRole('alert');
    expect(alert.className).toContain('amber');
  });

  it('toast without description renders only title', () => {
    useUiStore.setState({
      toasts: [
        { id: 't14', title: 'Title only', variant: 'default' },
      ],
    });

    render(<ToastContainer />);
    expect(screen.getByText('Title only')).toBeInTheDocument();
    // No description paragraph should exist
    expect(screen.queryByText(/description/i)).not.toBeInTheDocument();
  });
});
