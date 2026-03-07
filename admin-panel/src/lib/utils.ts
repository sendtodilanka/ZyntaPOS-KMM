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

export function buildQueryString(params: Record<string, unknown>): string {
  const filtered = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return filtered.join('&');
}

export function truncate(str: string, maxLength: number): string {
  if (str.length <= maxLength) return str;
  return `${str.slice(0, maxLength)}…`;
}
