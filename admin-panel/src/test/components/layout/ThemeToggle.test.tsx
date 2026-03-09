import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act } from '../../utils';
import { ThemeToggle } from '@/components/layout/ThemeToggle';
import { useUiStore } from '@/stores/ui-store';

beforeEach(() => {
  // Reset to dark theme before each test so tests are deterministic
  useUiStore.setState({ theme: 'dark' });
});

describe('ThemeToggle', () => {
  it('renders a button element', () => {
    render(<ThemeToggle />);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('has an accessible aria-label when theme is dark', () => {
    useUiStore.setState({ theme: 'dark' });
    render(<ThemeToggle />);
    expect(
      screen.getByRole('button', { name: 'Switch to light mode' }),
    ).toBeInTheDocument();
  });

  it('has an accessible aria-label when theme is light', () => {
    useUiStore.setState({ theme: 'light' });
    render(<ThemeToggle />);
    expect(
      screen.getByRole('button', { name: 'Switch to dark mode' }),
    ).toBeInTheDocument();
  });

  it('clicking the button calls toggleTheme in the store', () => {
    const toggleTheme = vi.fn();
    useUiStore.setState({ theme: 'dark', toggleTheme });

    render(<ThemeToggle />);
    fireEvent.click(screen.getByRole('button'));

    expect(toggleTheme).toHaveBeenCalledOnce();
  });

  it('switches aria-label from dark to light after toggle', () => {
    // Start dark
    useUiStore.setState({ theme: 'dark' });
    const { rerender } = render(<ThemeToggle />);
    expect(
      screen.getByRole('button', { name: 'Switch to light mode' }),
    ).toBeInTheDocument();

    // Simulate the store updating to light — wrap in act so React flushes state
    act(() => {
      useUiStore.setState({ theme: 'light' });
    });
    rerender(<ThemeToggle />);
    expect(
      screen.getByRole('button', { name: 'Switch to dark mode' }),
    ).toBeInTheDocument();
  });
});
