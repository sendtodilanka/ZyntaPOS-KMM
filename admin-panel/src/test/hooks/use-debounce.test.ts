import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useDebounce } from '@/hooks/use-debounce';

describe('useDebounce', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns the initial value immediately without waiting', () => {
    const { result } = renderHook(() => useDebounce('initial', 300));
    expect(result.current).toBe('initial');
  });

  it('does not update before the delay has elapsed', () => {
    const { result, rerender } = renderHook(
      ({ value, delay }: { value: string; delay: number }) =>
        useDebounce(value, delay),
      { initialProps: { value: 'first', delay: 300 } },
    );

    rerender({ value: 'second', delay: 300 });

    // Advance time by less than the delay — value should still be 'first'
    act(() => {
      vi.advanceTimersByTime(200);
    });

    expect(result.current).toBe('first');
  });

  it('updates the value after the delay has elapsed', () => {
    const { result, rerender } = renderHook(
      ({ value, delay }: { value: string; delay: number }) =>
        useDebounce(value, delay),
      { initialProps: { value: 'first', delay: 300 } },
    );

    rerender({ value: 'second', delay: 300 });

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(result.current).toBe('second');
  });

  it('resets the timer when value changes rapidly (only last value wins)', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 300),
      { initialProps: { value: 'a' } },
    );

    // Rapid successive updates
    rerender({ value: 'b' });
    act(() => { vi.advanceTimersByTime(100); });

    rerender({ value: 'c' });
    act(() => { vi.advanceTimersByTime(100); });

    rerender({ value: 'd' });
    act(() => { vi.advanceTimersByTime(100); });

    // Still in debounce window — should still show 'a'
    expect(result.current).toBe('a');

    // Let the full 300 ms pass from the last update
    act(() => { vi.advanceTimersByTime(300); });

    expect(result.current).toBe('d');
  });

  it('uses a default delay of 300 ms when no delay is provided', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value),
      { initialProps: { value: 'start' } },
    );

    rerender({ value: 'end' });

    // 299 ms — still debouncing
    act(() => { vi.advanceTimersByTime(299); });
    expect(result.current).toBe('start');

    // 1 more ms — timer fires
    act(() => { vi.advanceTimersByTime(1); });
    expect(result.current).toBe('end');
  });

  it('respects a custom delay value', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 1000),
      { initialProps: { value: 'start' } },
    );

    rerender({ value: 'end' });

    // 999 ms — still debouncing
    act(() => { vi.advanceTimersByTime(999); });
    expect(result.current).toBe('start');

    // 1 more ms — fires
    act(() => { vi.advanceTimersByTime(1); });
    expect(result.current).toBe('end');
  });

  it('works with number values', () => {
    const { result, rerender } = renderHook(
      ({ value }: { value: number }) => useDebounce(value, 300),
      { initialProps: { value: 0 } },
    );

    rerender({ value: 42 });

    act(() => { vi.advanceTimersByTime(300); });

    expect(result.current).toBe(42);
  });

  it('works with object values', () => {
    const initial = { query: '' };
    const updated = { query: 'hello' };

    const { result, rerender } = renderHook(
      ({ value }: { value: { query: string } }) => useDebounce(value, 300),
      { initialProps: { value: initial } },
    );

    rerender({ value: updated });

    act(() => { vi.advanceTimersByTime(300); });

    expect(result.current).toEqual({ query: 'hello' });
  });

  it('cleans up the timeout on unmount without error', () => {
    const { result, rerender, unmount } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 300),
      { initialProps: { value: 'a' } },
    );

    rerender({ value: 'b' });

    // Unmount before timer fires — should not throw
    expect(() => unmount()).not.toThrow();
  });
});
