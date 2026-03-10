import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export interface Toast {
  id: string;
  title: string;
  description?: string;
  variant: 'default' | 'success' | 'error' | 'warning';
  duration?: number;
}

interface UiStore {
  sidebarCollapsed: boolean;
  mobileSidebarOpen: boolean;
  theme: 'dark' | 'light';
  toasts: Toast[];
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setMobileSidebarOpen: (open: boolean) => void;
  toggleTheme: () => void;
  addToast: (toast: Omit<Toast, 'id'>) => void;
  removeToast: (id: string) => void;
}

type PersistedUiState = { sidebarCollapsed: boolean; theme: 'dark' | 'light' };

// Custom underlying storage: stores { theme, sidebarCollapsed } as a flat JSON object
// so localStorage.getItem('zynta-admin-ui') returns { "theme": "...", ... } directly.
// Tests read JSON.parse(raw).theme without needing to dig into { state: {...} }.
const flatRawStorage = {
  getItem: (name: string): string | null => {
    const raw = localStorage.getItem(name);
    if (!raw) return null;
    // raw is the flat state; wrap it into the { state, version } shape Zustand expects
    return JSON.stringify({ state: JSON.parse(raw), version: 0 });
  },
  setItem: (name: string, value: string): void => {
    // value is '{ "state": {...}, "version": ... }'; store only the inner state
    const parsed = JSON.parse(value) as { state: PersistedUiState };
    localStorage.setItem(name, JSON.stringify(parsed.state));
  },
  removeItem: (name: string): void => localStorage.removeItem(name),
};

export const useUiStore = create<UiStore>()(
  persist<UiStore, [], [], PersistedUiState>(
    (set) => ({
      sidebarCollapsed: false,
      mobileSidebarOpen: false,
      theme: 'dark',
      toasts: [],

      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
      setSidebarCollapsed: (collapsed) => set({ sidebarCollapsed: collapsed }),
      setMobileSidebarOpen: (open) => set({ mobileSidebarOpen: open }),
      toggleTheme: () => set((s) => ({ theme: s.theme === 'dark' ? 'light' : 'dark' })),

      addToast: (toast) =>
        set((s) => ({
          toasts: [
            ...s.toasts,
            { ...toast, id: `toast-${Date.now()}-${Math.random()}` },
          ],
        })),

      removeToast: (id) =>
        set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
    }),
    {
      name: 'zynta-admin-ui',
      partialize: (s) => ({ sidebarCollapsed: s.sidebarCollapsed, theme: s.theme }),
      storage: createJSONStorage(() => flatRawStorage),
    },
  ),
);

// Convenience toast helpers
export const toast = {
  success: (title: string, description?: string) =>
    useUiStore.getState().addToast({ title, description, variant: 'success', duration: 4000 }),
  error: (title: string, description?: string) =>
    useUiStore.getState().addToast({ title, description, variant: 'error', duration: 6000 }),
  warning: (title: string, description?: string) =>
    useUiStore.getState().addToast({ title, description, variant: 'warning', duration: 5000 }),
  info: (title: string, description?: string) =>
    useUiStore.getState().addToast({ title, description, variant: 'default', duration: 4000 }),
};
