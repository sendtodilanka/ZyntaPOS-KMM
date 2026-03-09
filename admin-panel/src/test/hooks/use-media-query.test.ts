import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import {
  useMediaQuery,
  useIsMobile,
  useIsTablet,
  useIsDesktop,
} from '@/hooks/use-media-query';

// ── matchMedia mock helpers ───────────────────────────────────────────────────

/**
 * Creates a minimal window.matchMedia mock where every query returns the
 * given `matches` value. The `addEventListener` / `removeEventListener`
 * stubs are required so useMediaQuery's useEffect can register its handler
 * without throwing.
 */
function mockMatchMedia(matches: boolean) {
  const listeners: Record<string, EventListener[]> = {};

  const mql = {
    matches,
    media: '',
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn((event: string, handler: EventListener) => {
      if (!listeners[event]) listeners[event] = [];
      listeners[event].push(handler);
    }),
    removeEventListener: vi.fn((event: string, handler: EventListener) => {
      if (listeners[event]) {
        listeners[event] = listeners[event].filter((h) => h !== handler);
      }
    }),
    dispatchEvent: vi.fn(),
    // Helper used in tests to simulate a media-query match change
    _fireChange: (newMatches: boolean) => {
      const changeListeners = listeners['change'] ?? [];
      changeListeners.forEach((fn) =>
        fn({ matches: newMatches } as unknown as Event),
      );
    },
  };

  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockReturnValue(mql),
  });

  return mql;
}

// ── useMediaQuery ─────────────────────────────────────────────────────────────

describe('useMediaQuery', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns true when matchMedia.matches is true', () => {
    mockMatchMedia(true);

    const { result } = renderHook(() => useMediaQuery('(min-width: 1024px)'));
    expect(result.current).toBe(true);
  });

  it('returns false when matchMedia.matches is false', () => {
    mockMatchMedia(false);

    const { result } = renderHook(() => useMediaQuery('(min-width: 1024px)'));
    expect(result.current).toBe(false);
  });

  it('updates state when the media query fires a change event to true', () => {
    const mql = mockMatchMedia(false);

    const { result } = renderHook(() => useMediaQuery('(min-width: 1024px)'));
    expect(result.current).toBe(false);

    act(() => {
      mql._fireChange(true);
    });

    expect(result.current).toBe(true);
  });

  it('updates state when the media query fires a change event to false', () => {
    const mql = mockMatchMedia(true);

    const { result } = renderHook(() => useMediaQuery('(min-width: 1024px)'));
    expect(result.current).toBe(true);

    act(() => {
      mql._fireChange(false);
    });

    expect(result.current).toBe(false);
  });

  it('registers an event listener on mount', () => {
    const mql = mockMatchMedia(false);
    renderHook(() => useMediaQuery('(max-width: 767px)'));
    expect(mql.addEventListener).toHaveBeenCalledWith('change', expect.any(Function));
  });

  it('removes the event listener on unmount', () => {
    const mql = mockMatchMedia(false);
    const { unmount } = renderHook(() => useMediaQuery('(max-width: 767px)'));
    unmount();
    expect(mql.removeEventListener).toHaveBeenCalledWith('change', expect.any(Function));
  });
});

// ── useIsMobile ───────────────────────────────────────────────────────────────

describe('useIsMobile', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns true when viewport matches (max-width: 767px)', () => {
    mockMatchMedia(true);
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(true);
  });

  it('returns false when viewport does not match (max-width: 767px)', () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(false);
  });

  it('passes the correct query to matchMedia', () => {
    mockMatchMedia(false);
    renderHook(() => useIsMobile());
    expect(window.matchMedia).toHaveBeenCalledWith('(max-width: 767px)');
  });
});

// ── useIsTablet ───────────────────────────────────────────────────────────────

describe('useIsTablet', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns true when viewport matches tablet breakpoint', () => {
    mockMatchMedia(true);
    const { result } = renderHook(() => useIsTablet());
    expect(result.current).toBe(true);
  });

  it('returns false when viewport does not match tablet breakpoint', () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useIsTablet());
    expect(result.current).toBe(false);
  });

  it('passes the correct query to matchMedia', () => {
    mockMatchMedia(false);
    renderHook(() => useIsTablet());
    expect(window.matchMedia).toHaveBeenCalledWith(
      '(min-width: 768px) and (max-width: 1023px)',
    );
  });
});

// ── useIsDesktop ──────────────────────────────────────────────────────────────

describe('useIsDesktop', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns true when viewport matches desktop breakpoint', () => {
    mockMatchMedia(true);
    const { result } = renderHook(() => useIsDesktop());
    expect(result.current).toBe(true);
  });

  it('returns false when viewport does not match desktop breakpoint', () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useIsDesktop());
    expect(result.current).toBe(false);
  });

  it('passes the correct query to matchMedia', () => {
    mockMatchMedia(false);
    renderHook(() => useIsDesktop());
    expect(window.matchMedia).toHaveBeenCalledWith('(min-width: 1024px)');
  });
});

// ── Breakpoint boundary checks ────────────────────────────────────────────────

describe('breakpoint boundaries', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('mobile and desktop are mutually exclusive for a given viewport', () => {
    // Simulate a desktop viewport: mobile = false, desktop = true
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn((query: string) => ({
        matches: query === '(min-width: 1024px)',
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    });

    const { result: mobileResult } = renderHook(() => useIsMobile());
    const { result: desktopResult } = renderHook(() => useIsDesktop());

    expect(mobileResult.current).toBe(false);
    expect(desktopResult.current).toBe(true);
  });
});
