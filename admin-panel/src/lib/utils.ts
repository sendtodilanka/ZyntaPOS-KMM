import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { format, formatDistanceToNow, parseISO } from 'date-fns';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatCurrency(amount: number, currency = 'LKR'): string {
  return new Intl.NumberFormat('en-LK', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US').format(value);
}

export function formatPercent(value: number, decimals = 1): string {
  return `${value >= 0 ? '+' : ''}${value.toFixed(decimals)}%`;
}

// ── ISO-string formatters (legacy — used by backend string timestamps) ───────

export function formatDateTime(isoString: string): string {
  return format(parseISO(isoString), 'MMM d, yyyy HH:mm');
}

export function formatDate(isoString: string): string {
  return format(parseISO(isoString), 'MMM d, yyyy');
}

export function formatRelativeTime(isoString: string): string {
  const diffSeconds = Math.floor((Date.now() - parseISO(isoString).getTime()) / 1000);
  if (diffSeconds < 60) return 'just now';
  if (diffSeconds < 3600) return `${Math.floor(diffSeconds / 60)} min ago`;
  if (diffSeconds < 86400) return `${Math.floor(diffSeconds / 3600)} hr ago`;
  return formatDistanceToNow(parseISO(isoString), { addSuffix: true });
}

// ── Epoch-ms formatters (timezone-aware) ─────────────────────────────────────
// All ZyntaPOS timestamps are stored as epoch-ms (UTC). These helpers convert
// them for display in the user's configured timezone using the native Intl API.

/**
 * Formats an epoch-ms timestamp as "14 Mar 2025, 14:35" in the given timezone.
 */
export function formatEpochDateTime(epochMs: number, tz: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: tz,
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(epochMs));
}

/**
 * Formats an epoch-ms timestamp as a short date "14 Mar 2025" in the given timezone.
 */
export function formatEpochDate(epochMs: number, tz: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: tz,
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(epochMs));
}

/**
 * Returns a relative label ("just now", "5 min ago", "3 hr ago") or a short date
 * for timestamps older than 24 hours, all in the given timezone.
 */
export function formatEpochRelative(epochMs: number, tz: string): string {
  const diff = Date.now() - epochMs;
  if (diff < 60_000)    return 'just now';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} min ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} hr ago`;
  return formatEpochDate(epochMs, tz);
}

/**
 * Returns the UTC offset label for a timezone at the current moment,
 * e.g. "Asia/Colombo" → "UTC+05:30".
 */
export function tzOffsetLabel(tz: string): string {
  try {
    const part = new Intl.DateTimeFormat('en', {
      timeZone: tz,
      timeZoneName: 'shortOffset',
    }).formatToParts(new Date()).find((p) => p.type === 'timeZoneName');
    return part?.value ?? tz;
  } catch {
    return tz;
  }
}

// ── Misc utilities ────────────────────────────────────────────────────────────

export function maskLicenseKey(key: string): string {
  const parts = key.split('-');
  if (parts.length <= 2) return key;
  const masked = parts.slice(1, -1).map(() => '****');
  return [parts[0], ...masked, parts[parts.length - 1]].join('-');
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const value = bytes / Math.pow(k, i);
  return i === 0 ? `${value} B` : `${value.toFixed(1)} ${sizes[i]}`;
}

export function buildQueryString(params: object): string {
  const filtered = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return filtered.join('&');
}

export function truncate(str: string, maxLength: number): string {
  if (str.length <= maxLength) return str;
  return `${str.slice(0, maxLength)}…`;
}
