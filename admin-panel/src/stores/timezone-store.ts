import { create } from 'zustand';
import { persist } from 'zustand/middleware';

// Curated list of IANA timezones ordered by UTC offset.
// Format: [id, display label]
export const TIMEZONE_OPTIONS: [string, string][] = [
  ['Pacific/Midway',      'Midway Island (UTC-11:00)'],
  ['Pacific/Honolulu',    'Hawaii (UTC-10:00)'],
  ['America/Anchorage',   'Alaska (UTC-09:00)'],
  ['America/Los_Angeles', 'Pacific Time — US & Canada (UTC-08:00)'],
  ['America/Denver',      'Mountain Time — US & Canada (UTC-07:00)'],
  ['America/Chicago',     'Central Time — US & Canada (UTC-06:00)'],
  ['America/New_York',    'Eastern Time — US & Canada (UTC-05:00)'],
  ['America/Caracas',     'Caracas (UTC-04:30)'],
  ['America/Halifax',     'Atlantic Time — Canada (UTC-04:00)'],
  ['America/Argentina/Buenos_Aires', 'Buenos Aires (UTC-03:00)'],
  ['America/Sao_Paulo',   'Brasilia (UTC-03:00)'],
  ['Atlantic/Azores',     'Azores (UTC-01:00)'],
  ['Europe/London',       'London, Dublin, Lisbon (UTC+00:00)'],
  ['Europe/Paris',        'Paris, Berlin, Amsterdam (UTC+01:00)'],
  ['Europe/Helsinki',     'Helsinki, Kyiv, Riga (UTC+02:00)'],
  ['Europe/Moscow',       'Moscow, St. Petersburg (UTC+03:00)'],
  ['Asia/Tehran',         'Tehran (UTC+03:30)'],
  ['Asia/Dubai',          'Abu Dhabi, Muscat (UTC+04:00)'],
  ['Asia/Kabul',          'Kabul (UTC+04:30)'],
  ['Asia/Karachi',        'Karachi, Islamabad (UTC+05:00)'],
  ['Asia/Colombo',        'Sri Lanka — Colombo (UTC+05:30)'],
  ['Asia/Kolkata',        'India — New Delhi, Mumbai (UTC+05:30)'],
  ['Asia/Kathmandu',      'Kathmandu (UTC+05:45)'],
  ['Asia/Dhaka',          'Dhaka, Almaty (UTC+06:00)'],
  ['Asia/Rangoon',        'Yangon (UTC+06:30)'],
  ['Asia/Bangkok',        'Bangkok, Hanoi, Jakarta (UTC+07:00)'],
  ['Asia/Singapore',      'Singapore, Kuala Lumpur (UTC+08:00)'],
  ['Asia/Tokyo',          'Tokyo, Seoul, Osaka (UTC+09:00)'],
  ['Australia/Adelaide',  'Adelaide (UTC+09:30)'],
  ['Australia/Sydney',    'Sydney, Melbourne (UTC+10:00)'],
  ['Pacific/Auckland',    'Auckland, Wellington (UTC+12:00)'],
];

interface TimezoneStore {
  /** IANA timezone identifier, e.g. "Asia/Colombo" */
  timezone: string;
  setTimezone: (tz: string) => void;
}

export const useTimezoneStore = create<TimezoneStore>()(
  persist(
    (set) => ({
      timezone: 'Asia/Colombo',
      setTimezone: (timezone) => set({ timezone }),
    }),
    { name: 'zynta-admin-prefs' },
  ),
);
