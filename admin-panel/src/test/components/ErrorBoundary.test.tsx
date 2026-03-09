import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import { ErrorBoundary } from '@/components/shared/ErrorBoundary';

// Component that unconditionally throws during render
const ThrowingComponent = () => {
  throw new Error('test error');
  return null;
};

describe('ErrorBoundary', () => {
  // Suppress React's console.error output for expected errors in these tests
  let consoleError: typeof console.error;

  beforeEach(() => {
    consoleError = console.error;
    console.error = vi.fn();
  });

  afterEach(() => {
    console.error = consoleError;
  });

  it('renders children normally when there is no error', () => {
    render(
      <ErrorBoundary>
        <p>Safe content</p>
      </ErrorBoundary>,
    );
    expect(screen.getByText('Safe content')).toBeInTheDocument();
  });

  it('shows default error UI when a child throws', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
  });

  it('displays the thrown error message in the default error UI', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>,
    );
    expect(screen.getByText('test error')).toBeInTheDocument();
  });

  it('shows a custom fallback when the fallback prop is provided', () => {
    render(
      <ErrorBoundary fallback={<p>Custom fallback</p>}>
        <ThrowingComponent />
      </ErrorBoundary>,
    );
    expect(screen.getByText('Custom fallback')).toBeInTheDocument();
    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument();
  });

  it('renders "Try again" recovery button in default error UI', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('clears the error state when "Try again" is clicked', () => {
    // Use a component that can be conditionally broken
    let shouldThrow = true;
    const ConditionalThrow = () => {
      if (shouldThrow) throw new Error('recoverable error');
      return <p>Recovered</p>;
    };

    render(
      <ErrorBoundary>
        <ConditionalThrow />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Something went wrong')).toBeInTheDocument();

    // Fix the underlying cause before clicking "Try again"
    shouldThrow = false;
    fireEvent.click(screen.getByRole('button', { name: /try again/i }));

    expect(screen.getByText('Recovered')).toBeInTheDocument();
  });
});
