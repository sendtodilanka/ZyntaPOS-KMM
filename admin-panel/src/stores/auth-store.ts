import { create } from 'zustand';
import type { AdminUser } from '@/types/user';

// S1-10: setUser accepts non-null only — use clearUser() for logout
interface AuthStore {
  user: AdminUser | null;
  setUser: (user: AdminUser) => void;
  clearUser: () => void;
}

export const useAuthStore = create<AuthStore>()((set) => ({
  user: null,

  setUser: (user) => set({ user }),
  clearUser: () => set({ user: null }),
}));
