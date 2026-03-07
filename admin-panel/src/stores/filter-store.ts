import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type FilterState = Record<string, Record<string, unknown>>;

interface FilterStore {
  filters: FilterState;
  setFilter: (page: string, key: string, value: unknown) => void;
  clearFilters: (page: string) => void;
  getFilters: (page: string) => Record<string, unknown>;
}

export const useFilterStore = create<FilterStore>()(
  persist(
    (set, get) => ({
      filters: {},
      setFilter: (page, key, value) =>
        set((s) => ({
          filters: {
            ...s.filters,
            [page]: { ...(s.filters[page] ?? {}), [key]: value },
          },
        })),
      clearFilters: (page) =>
        set((s) => {
          const { [page]: _, ...rest } = s.filters;
          return { filters: rest };
        }),
      getFilters: (page) => get().filters[page] ?? {},
    }),
    { name: 'zynta-admin-filters' },
  ),
);
