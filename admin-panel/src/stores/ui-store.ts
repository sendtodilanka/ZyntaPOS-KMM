import { create } from 'zustand';
import { persist } from 'zustand/middleware';

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

export const useUiStore = create<UiStore>()(
  persist(
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
      // Flatten the persisted value so localStorage.getItem('zynta-admin-ui') returns
      // { theme: '...' } directly (not wrapped in { state: {...} })
      serialize: (value) => JSON.stringify(value.state),
      deserialize: (str) => ({ state: JSON.parse(str) as { sidebarCollapsed: boolean; theme: 'dark' | 'light' }, version: 0 }),
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
