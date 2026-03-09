import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTimezone } from '@/hooks/use-timezone';
import { useTimezoneStore } from '@/stores/timezone-store';

// ── Test setup ────────────────────────────────────────────────────────────────

beforeEach(() => {
  // Pin the timezone store to 'Asia/Colombo' (UTC+05:30) before each test
  act(() => {
    useTimezoneStore.setState({ timezone: 'Asia/Colombo' });
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

// ── useTimezone ───────────────────────────────────────────────────────────────

describe('useTimezone', () => {
  describe('timezone field', () => {
    it('returns the timezone from the store', () => {
      const { result } = renderHook(() => useTimezone());
      expect(result.current.timezone).toBe('Asia/Colombo');
    });

    it('reflects a store change when timezone is updated', () => {
      const { result } = renderHook(() => useTimezone());

      act(() => {
        useTimezoneStore.getState().setTimezone('UTC');
      });

      expect(result.current.timezone).toBe('UTC');
    });
  });

  // ── formatDateTime ──────────────────────────────────────────────────────────

  describe('formatDateTime', () => {
    it('is a function', () => {
      const { result } = renderHook(() => useTimezone());
      expect(typeof result.current.formatDateTime).toBe('function');
    });

    it('formats an epoch-ms timestamp in Asia/Colombo (UTC+05:30)', () => {
      const { result } = renderHook(() => useTimezone());

      // 2025-03-14 09:00:00 UTC  =  14:30 in Asia/Colombo
      const epochMs = new Date('2025-03-14T09:00:00Z').getTime();
      const formatted = result.current.formatDateTime(epochMs);

      expect(formatted).toContain('14');    // day
      expect(formatted).toContain('Mar');   // month
      expect(formatted).toContain('2025');  // year
      expect(formatted).toContain('14:30'); // time in Colombo
    });

    it('produces different output for different timezones', () => {
      const { result } = renderHook(() => useTimezone());

      const epochMs = new Date('2025-06-15T12:00:00Z').getTime();
      const colomboFormatted = result.current.formatDateTime(epochMs);

      act(() => {
        useTimezoneStore.getState().setTimezone('UTC');
      });

      const utcFormatted = result.current.formatDateTime(epochMs);
      expect(colomboFormatted).not.toBe(utcFormatted);
    });

    it('returns a non-empty string', () => {
      const { result } = renderHook(() => useTimezone());
      expect(result.current.formatDateTime(Date.now()).length).toBeGreaterThan(0);
    });
  });

  // ── formatDate ──────────────────────────────────────────────────────────────

  describe('formatDate', () => {
    it('is a function', () => {
      const { result } = renderHook(() => useTimezone());
      expect(typeof result.current.formatDate).toBe('function');
    });

    it('formats epoch-ms as a short date string in the store timezone', () => {
      const { result } = renderHook(() => useTimezone());

      const epochMs = new Date('2025-03-14T00:00:00Z').getTime();
      const formatted = result.current.formatDate(epochMs);

      expect(formatted).toContain('Mar');
      expect(formatted).toContain('2025');
    });

    it('does not include a time component', () => {
      const { result } = renderHook(() => useTimezone());
      const formatted = result.current.formatDate(Date.now());
      // Should not contain HH:MM pattern
      expect(formatted).not.toMatch(/\d{2}:\d{2}/);
    });

    it('respects timezone boundaries — date can differ between UTC and Asia/Colombo', () => {
      // 2025-03-14 23:00:00 UTC = 2025-03-15 04:30 in Asia/Colombo
      const epochMs = new Date('2025-03-14T23:00:00Z').getTime();

      // Colombo timezone (UTC+05:30) → should show 15 Mar
      const { result: colomboResult } = renderHook(() => useTimezone());
      const colomboDate = colomboResult.current.formatDate(epochMs);
      expect(colomboDate).toContain('15');

      // Switch to UTC → should show 14 Mar
      act(() => {
        useTimezoneStore.getState().setTimezone('UTC');
      });

      const { result: utcResult } = renderHook(() => useTimezone());
      const utcDate = utcResult.current.formatDate(epochMs);
      expect(utcDate).toContain('14');
    });
  });

  // ── formatRelative ──────────────────────────────────────────────────────────

  describe('formatRelative', () => {
    it('is a function', () => {
      const { result } = renderHook(() => useTimezone());
      expect(typeof result.current.formatRelative).toBe('function');
    });

    it('returns "just now" for timestamps within the last 60 seconds', () => {
      const { result } = renderHook(() => useTimezone());
      expect(result.current.formatRelative(Date.now() - 10_000)).toBe('just now');
    });

    it('returns "X min ago" for timestamps within the last hour', () => {
      const { result } = renderHook(() => useTimezone());
      const fiveMinAgo = Date.now() - 5 * 60_000;
      expect(result.current.formatRelative(fiveMinAgo)).toBe('5 min ago');
    });

    it('returns "X hr ago" for timestamps between 1 and 24 hours ago', () => {
      const { result } = renderHook(() => useTimezone());
      const twoHoursAgo = Date.now() - 2 * 3_600_000;
      expect(result.current.formatRelative(twoHoursAgo)).toBe('2 hr ago');
    });

    it('falls back to a date string for timestamps older than 24 hours', () => {
      const { result } = renderHook(() => useTimezone());
      const threeDaysAgo = Date.now() - 3 * 86_400_000;
      const formatted = result.current.formatRelative(threeDaysAgo);
      // Should not be a relative label — should be a date string
      expect(formatted).not.toContain('ago');
      expect(formatted.length).toBeGreaterThan(0);
    });
  });

  // ── Re-render behaviour ─────────────────────────────────────────────────────

  describe('re-render on timezone change', () => {
    it('all three helpers use the updated timezone after a store change', () => {
      const { result } = renderHook(() => useTimezone());

      // 2025-03-14 09:00:00 UTC = 14:30 Colombo, 09:00 UTC
      const epochMs = new Date('2025-03-14T09:00:00Z').getTime();

      const colomboFormatted = result.current.formatDateTime(epochMs);

      act(() => {
        useTimezoneStore.getState().setTimezone('UTC');
      });

      const utcFormatted = result.current.formatDateTime(epochMs);

      expect(colomboFormatted).toContain('14:30');
      expect(utcFormatted).toContain('09:00');
    });
  });
});
