import { useTimezoneStore } from '@/stores/timezone-store';
import {
  formatEpochDateTime,
  formatEpochDate,
  formatEpochRelative,
} from '@/lib/utils';

/**
 * Returns the user's current timezone preference and pre-bound formatting helpers.
 *
 * All helpers accept an epoch-millisecond timestamp (as stored in the DB)
 * and convert it for display in the configured timezone without any extra arguments.
 *
 * Usage:
 * ```tsx
 * const { formatDateTime, formatRelative, timezone } = useTimezone();
 * <span>{formatDateTime(user.createdAt)}</span>
 * ```
 */
export function useTimezone() {
  const timezone = useTimezoneStore((s) => s.timezone);

  return {
    /** Current IANA timezone identifier, e.g. "Asia/Colombo" */
    timezone,

    /** "14 Mar 2025, 14:35" */
    formatDateTime: (epochMs: number) => formatEpochDateTime(epochMs, timezone),

    /** "14 Mar 2025" */
    formatDate: (epochMs: number) => formatEpochDate(epochMs, timezone),

    /** "5 min ago" / "3 hr ago" / "14 Mar 2025" */
    formatRelative: (epochMs: number) => formatEpochRelative(epochMs, timezone),
  };
}
