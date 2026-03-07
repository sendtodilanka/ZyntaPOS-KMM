import { describe, it, expect } from 'vitest';
import { formatCurrency, maskLicenseKey, formatBytes, formatRelativeTime, buildQueryString } from '@/lib/utils';

describe('formatCurrency', () => {
  it('formats LKR amounts', () => {
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
});

describe('maskLicenseKey', () => {
  it('masks middle segments', () => {
    const result = maskLicenseKey('ZYNTA-ABCD-1234-EFGH');
    expect(result).toBe('ZYNTA-****-****-EFGH');
  });

  it('handles short keys gracefully', () => {
    const result = maskLicenseKey('SHORT');
    expect(result).toBe('SHORT');
  });
});

describe('formatBytes', () => {
  it('formats bytes', () => expect(formatBytes(500)).toBe('500 B'));
  it('formats kilobytes', () => expect(formatBytes(1024)).toBe('1.0 KB'));
  it('formats megabytes', () => expect(formatBytes(1024 * 1024)).toBe('1.0 MB'));
  it('formats gigabytes', () => expect(formatBytes(1024 ** 3)).toBe('1.0 GB'));
});

describe('buildQueryString', () => {
  it('builds query string from object', () => {
    const qs = buildQueryString({ page: 1, search: 'hello' });
    expect(qs).toContain('page=1');
    expect(qs).toContain('search=hello');
  });

  it('omits undefined/null values', () => {
    const qs = buildQueryString({ page: 1, search: undefined, filter: null as any });
    expect(qs).toBe('page=1');
  });

  it('returns empty string for empty object', () => {
    expect(buildQueryString({})).toBe('');
  });
});

describe('formatRelativeTime', () => {
  it('returns "just now" for recent times', () => {
    const now = new Date().toISOString();
    expect(formatRelativeTime(now)).toBe('just now');
  });

  it('returns minutes for times within an hour', () => {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    expect(formatRelativeTime(fiveMinutesAgo)).toContain('min');
  });
});
