import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  cn,
  formatCurrency,
  formatNumber,
  formatPercent,
  formatDateTime,
  formatDate,
  formatRelativeTime,
  formatEpochDateTime,
  formatEpochDate,
  formatEpochRelative,
  tzOffsetLabel,
  maskLicenseKey,
  formatBytes,
  buildQueryString,
  truncate,
} from '@/lib/utils';

// ── cn ────────────────────────────────────────────────────────────────────────

describe('cn', () => {
  it('merges class strings', () => {
    expect(cn('foo', 'bar')).toBe('foo bar');
  });

  it('handles conditional classes with falsy values', () => {
    expect(cn('foo', false && 'bar', undefined, null, 'baz')).toBe('foo baz');
  });

  it('deduplicates conflicting tailwind classes (last wins)', () => {
    // twMerge resolves conflicts: later class wins
    const result = cn('text-red-500', 'text-blue-500');
    expect(result).toBe('text-blue-500');
    expect(result).not.toContain('text-red-500');
  });

  it('returns empty string when all arguments are falsy', () => {
    expect(cn(false, undefined, null)).toBe('');
  });

  it('handles object syntax for conditional classes', () => {
    const result = cn({ 'bg-red-500': true, 'bg-blue-500': false });
    expect(result).toContain('bg-red-500');
    expect(result).not.toContain('bg-blue-500');
  });

  it('merges array of classes', () => {
    const result = cn(['px-4', 'py-2'], 'font-bold');
    expect(result).toContain('px-4');
    expect(result).toContain('py-2');
    expect(result).toContain('font-bold');
  });
});

// ── formatCurrency ────────────────────────────────────────────────────────────

describe('formatCurrency', () => {
  it('formats LKR amounts with thousands separator', () => {
    const result = formatCurrency(1500);
    expect(result).toContain('1,500');
  });

  it('handles zero', () => {
    const result = formatCurrency(0);
    expect(result).toContain('0');
  });

  it('handles large amounts', () => {
    const result = formatCurrency(1000000);
    expect(result).toContain('1,000,000');
  });

  it('formats with two decimal places', () => {
    const result = formatCurrency(1234.5);
    expect(result).toContain('1,234.50');
  });

  it('uses LKR currency symbol by default', () => {
    const result = formatCurrency(100);
    // LKR symbol or currency code should appear
    expect(result).toMatch(/LKR|Rs|₨/);
  });

  it('formats negative values', () => {
    const result = formatCurrency(-500);
    expect(result).toContain('500');
    // Negative values include a minus sign or parentheses
    expect(result).toMatch(/-|\(.*\)/);
  });

  it('accepts a custom currency code', () => {
    const result = formatCurrency(100, 'USD');
    expect(result).toMatch(/\$|USD/);
  });
});

// ── formatNumber ──────────────────────────────────────────────────────────────

describe('formatNumber', () => {
  it('formats thousands with separator', () => {
    expect(formatNumber(1000)).toBe('1,000');
  });

  it('formats millions', () => {
    expect(formatNumber(1000000)).toBe('1,000,000');
  });

  it('formats small numbers without separator', () => {
    expect(formatNumber(999)).toBe('999');
  });

  it('formats zero', () => {
    expect(formatNumber(0)).toBe('0');
  });

  it('formats negative numbers', () => {
    expect(formatNumber(-1500)).toBe('-1,500');
  });
});

// ── formatPercent ─────────────────────────────────────────────────────────────

describe('formatPercent', () => {
  it('prefixes positive values with +', () => {
    expect(formatPercent(12.5)).toBe('+12.5%');
  });

  it('does not double-prefix negative values', () => {
    expect(formatPercent(-3.2)).toBe('-3.2%');
  });

  it('formats zero with + prefix', () => {
    expect(formatPercent(0)).toBe('+0.0%');
  });

  it('respects the decimals argument', () => {
    expect(formatPercent(5.678, 2)).toBe('+5.68%');
  });

  it('uses 1 decimal by default', () => {
    expect(formatPercent(1.0)).toBe('+1.0%');
  });
});

// ── formatDateTime ────────────────────────────────────────────────────────────

describe('formatDateTime', () => {
  it('formats an ISO string as "MMM d, yyyy HH:mm"', () => {
    const result = formatDateTime('2025-03-14T14:35:00Z');
    // The format is "Mar 14, 2025 14:35" — allow for timezone-relative output
    expect(result).toMatch(/Mar \d+, 2025 \d{2}:\d{2}/);
  });

  it('handles a midnight timestamp', () => {
    const result = formatDateTime('2024-01-01T00:00:00Z');
    expect(result).toMatch(/Jan \d+, 2024 \d{2}:\d{2}/);
  });

  it('returns a string', () => {
    expect(typeof formatDateTime('2024-06-15T10:30:00Z')).toBe('string');
  });
});

// ── formatDate ────────────────────────────────────────────────────────────────

describe('formatDate', () => {
  it('formats an ISO string as "MMM d, yyyy" (date only)', () => {
    const result = formatDate('2025-03-14T00:00:00Z');
    expect(result).toMatch(/Mar \d+, 2025/);
  });

  it('does not include a time component', () => {
    const result = formatDate('2025-12-31T23:59:59Z');
    expect(result).not.toMatch(/\d{2}:\d{2}/);
  });

  it('returns a string', () => {
    expect(typeof formatDate('2024-06-15T10:30:00Z')).toBe('string');
  });
});

// ── formatRelativeTime ────────────────────────────────────────────────────────

describe('formatRelativeTime', () => {
  it('returns "just now" for very recent timestamps (within 60 s)', () => {
    const now = new Date().toISOString();
    expect(formatRelativeTime(now)).toBe('just now');
  });

  it('returns "X min ago" for timestamps within an hour', () => {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    expect(formatRelativeTime(fiveMinutesAgo)).toBe('5 min ago');
  });

  it('returns "X hr ago" for timestamps between 1 and 24 hours ago', () => {
    const threeHoursAgo = new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString();
    expect(formatRelativeTime(threeHoursAgo)).toBe('3 hr ago');
  });

  it('returns a relative string for timestamps older than 24 hours', () => {
    const twoDaysAgo = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString();
    const result = formatRelativeTime(twoDaysAgo);
    // date-fns formatDistanceToNow produces e.g. "2 days ago"
    expect(result).toContain('ago');
  });

  it('returns "just now" for a timestamp 59 seconds ago', () => {
    const fiftyNineSecondsAgo = new Date(Date.now() - 59 * 1000).toISOString();
    expect(formatRelativeTime(fiftyNineSecondsAgo)).toBe('just now');
  });

  it('returns "1 min ago" for exactly 60 seconds ago', () => {
    const sixtySecondsAgo = new Date(Date.now() - 60 * 1000).toISOString();
    expect(formatRelativeTime(sixtySecondsAgo)).toBe('1 min ago');
  });
});

// ── formatEpochDateTime ───────────────────────────────────────────────────────

describe('formatEpochDateTime', () => {
  it('formats epoch ms in the given timezone', () => {
    // 2025-03-14 09:00:00 UTC = 14:30 in Asia/Colombo (UTC+05:30)
    const epochMs = new Date('2025-03-14T09:00:00Z').getTime();
    const result = formatEpochDateTime(epochMs, 'Asia/Colombo');
    expect(result).toContain('14');   // day
    expect(result).toContain('Mar'); // month abbreviation
    expect(result).toContain('2025'); // year
    expect(result).toContain('14:30'); // 09:00 UTC + 05:30 = 14:30
  });

  it('returns a non-empty string', () => {
    const result = formatEpochDateTime(Date.now(), 'UTC');
    expect(result.length).toBeGreaterThan(0);
  });

  it('produces different output for different timezones', () => {
    const epochMs = new Date('2025-06-15T12:00:00Z').getTime();
    const utcResult = formatEpochDateTime(epochMs, 'UTC');
    const colomboResult = formatEpochDateTime(epochMs, 'Asia/Colombo');
    expect(utcResult).not.toBe(colomboResult);
  });
});

// ── formatEpochDate ───────────────────────────────────────────────────────────

describe('formatEpochDate', () => {
  it('formats epoch ms as a short date in the given timezone', () => {
    const epochMs = new Date('2025-03-14T00:00:00Z').getTime();
    const result = formatEpochDate(epochMs, 'UTC');
    expect(result).toContain('14');
    expect(result).toContain('Mar');
    expect(result).toContain('2025');
  });

  it('does not include a time component', () => {
    const result = formatEpochDate(Date.now(), 'UTC');
    // Should not contain HH:MM pattern
    expect(result).not.toMatch(/\d{2}:\d{2}/);
  });

  it('respects timezone boundaries (date can differ between UTC and +05:30)', () => {
    // 2025-03-14 23:00:00 UTC = 2025-03-15 04:30 in Asia/Colombo
    const epochMs = new Date('2025-03-14T23:00:00Z').getTime();
    const utcResult = formatEpochDate(epochMs, 'UTC');
    const colomboResult = formatEpochDate(epochMs, 'Asia/Colombo');
    expect(utcResult).toContain('14');
    expect(colomboResult).toContain('15');
  });
});

// ── formatEpochRelative ───────────────────────────────────────────────────────

describe('formatEpochRelative', () => {
  it('returns "just now" for timestamps within 60 seconds', () => {
    expect(formatEpochRelative(Date.now() - 30_000, 'UTC')).toBe('just now');
  });

  it('returns "X min ago" for timestamps within an hour', () => {
    const tenMinAgo = Date.now() - 10 * 60_000;
    expect(formatEpochRelative(tenMinAgo, 'UTC')).toBe('10 min ago');
  });

  it('returns "X hr ago" for timestamps between 1 and 24 hours ago', () => {
    const twoHoursAgo = Date.now() - 2 * 3_600_000;
    expect(formatEpochRelative(twoHoursAgo, 'UTC')).toBe('2 hr ago');
  });

  it('falls back to formatEpochDate for timestamps older than 24 hours', () => {
    const threeDaysAgo = Date.now() - 3 * 86_400_000;
    const result = formatEpochRelative(threeDaysAgo, 'UTC');
    // Should return a date string, not a "X ago" label
    expect(result).not.toContain('ago');
    expect(result).toMatch(/\w{3} \d{4}/); // e.g. "Mar 2025"
  });
});

// ── tzOffsetLabel ─────────────────────────────────────────────────────────────

describe('tzOffsetLabel', () => {
  it('returns a GMT+5:30 label for Asia/Colombo', () => {
    const label = tzOffsetLabel('Asia/Colombo');
    // Intl output varies by Node version: "GMT+5:30" or "GMT+05:30"
    expect(label).toMatch(/GMT\+0?5:30|UTC\+0?5:30/);
  });

  it('returns UTC+00:00 or GMT for UTC', () => {
    const label = tzOffsetLabel('UTC');
    expect(label).toMatch(/GMT|UTC/);
  });

  it('returns the raw timezone string on invalid input', () => {
    const label = tzOffsetLabel('Invalid/Timezone');
    expect(label).toBe('Invalid/Timezone');
  });

  it('returns a non-empty string for any valid IANA timezone', () => {
    expect(tzOffsetLabel('America/New_York').length).toBeGreaterThan(0);
    expect(tzOffsetLabel('Asia/Tokyo').length).toBeGreaterThan(0);
  });
});

// ── maskLicenseKey ────────────────────────────────────────────────────────────

describe('maskLicenseKey', () => {
  it('masks middle segments of a 4-part key', () => {
    expect(maskLicenseKey('ZYNTA-ABCD-1234-EFGH')).toBe('ZYNTA-****-****-EFGH');
  });

  it('keeps the first and last segments visible', () => {
    const result = maskLicenseKey('PREFIX-HIDE-ME-SUFFIX');
    expect(result.startsWith('PREFIX')).toBe(true);
    expect(result.endsWith('SUFFIX')).toBe(true);
  });

  it('handles short keys with one segment gracefully (no masking)', () => {
    expect(maskLicenseKey('SHORT')).toBe('SHORT');
  });

  it('handles two-segment keys without masking', () => {
    // parts.length <= 2 → return key as-is
    expect(maskLicenseKey('PART1-PART2')).toBe('PART1-PART2');
  });

  it('masks all middle segments of a 5-part key', () => {
    const result = maskLicenseKey('A-B-C-D-E');
    expect(result).toBe('A-****-****-****-E');
  });
});

// ── formatBytes ──────────────────────────────────────────────────────────────

describe('formatBytes', () => {
  it('returns "0 B" for zero', () => {
    expect(formatBytes(0)).toBe('0 B');
  });

  it('formats bytes (under 1 KB)', () => {
    expect(formatBytes(500)).toBe('500 B');
  });

  it('formats 1 KB', () => {
    expect(formatBytes(1024)).toBe('1.0 KB');
  });

  it('formats 1 MB', () => {
    expect(formatBytes(1024 * 1024)).toBe('1.0 MB');
  });

  it('formats 1 GB', () => {
    expect(formatBytes(1024 ** 3)).toBe('1.0 GB');
  });

  it('formats 1 TB', () => {
    expect(formatBytes(1024 ** 4)).toBe('1.0 TB');
  });

  it('formats fractional KB', () => {
    expect(formatBytes(1536)).toBe('1.5 KB');
  });
});

// ── buildQueryString ──────────────────────────────────────────────────────────

describe('buildQueryString', () => {
  it('builds a query string from a plain object', () => {
    const qs = buildQueryString({ page: 1, search: 'hello' });
    expect(qs).toContain('page=1');
    expect(qs).toContain('search=hello');
  });

  it('omits undefined values', () => {
    const qs = buildQueryString({ page: 1, search: undefined });
    expect(qs).toBe('page=1');
  });

  it('omits null values', () => {
    const qs = buildQueryString({ page: 1, filter: null as unknown as string });
    expect(qs).toBe('page=1');
  });

  it('omits empty string values', () => {
    const qs = buildQueryString({ page: 1, search: '' });
    expect(qs).toBe('page=1');
  });

  it('returns empty string for an empty object', () => {
    expect(buildQueryString({})).toBe('');
  });

  it('returns empty string when all values are filtered out', () => {
    expect(buildQueryString({ a: undefined, b: null as unknown as string, c: '' })).toBe('');
  });

  it('URL-encodes special characters in values', () => {
    const qs = buildQueryString({ q: 'hello world' });
    expect(qs).toBe('q=hello%20world');
  });

  it('handles boolean and number values', () => {
    const qs = buildQueryString({ active: true, size: 50 });
    expect(qs).toContain('active=true');
    expect(qs).toContain('size=50');
  });
});

// ── truncate ──────────────────────────────────────────────────────────────────

describe('truncate', () => {
  it('returns the original string if it is shorter than maxLength', () => {
    expect(truncate('hello', 10)).toBe('hello');
  });

  it('returns the original string if it is exactly maxLength', () => {
    expect(truncate('hello', 5)).toBe('hello');
  });

  it('truncates and appends ellipsis when string exceeds maxLength', () => {
    expect(truncate('hello world', 5)).toBe('hello…');
  });

  it('uses the Unicode ellipsis character (…), not three dots', () => {
    const result = truncate('abcdefghij', 3);
    expect(result).toBe('abc…');
    expect(result).not.toContain('...');
  });

  it('handles maxLength of 0', () => {
    expect(truncate('hello', 0)).toBe('…');
  });

  it('handles an empty string', () => {
    expect(truncate('', 5)).toBe('');
  });
});
