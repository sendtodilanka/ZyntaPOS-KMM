import { describe, it, expect, beforeEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useUiStore, toast } from '@/stores/ui-store';

/**
 * Reset the UI store to its logical defaults before each test.
 * The store uses zustand/persist, so we bypass localStorage and
 * overwrite state directly to keep tests deterministic.
 */
function resetUiStore() {
  useUiStore.setState({
    sidebarCollapsed: false,
    mobileSidebarOpen: false,
    theme: 'dark',
    toasts: [],
  });
}

describe('useUiStore', () => {
  beforeEach(() => {
    act(() => {
      resetUiStore();
    });
  });

  // ── Initial state ──────────────────────────────────────────────────────────

  describe('initial state', () => {
    it('has sidebarCollapsed false', () => {
      const { result } = renderHook(() => useUiStore());
      expect(result.current.sidebarCollapsed).toBe(false);
    });

    it('has mobileSidebarOpen false', () => {
      const { result } = renderHook(() => useUiStore());
      expect(result.current.mobileSidebarOpen).toBe(false);
    });

    it('has theme dark', () => {
      const { result } = renderHook(() => useUiStore());
      expect(result.current.theme).toBe('dark');
    });

    it('has empty toasts array', () => {
      const { result } = renderHook(() => useUiStore());
      expect(result.current.toasts).toHaveLength(0);
    });
  });

  // ── toggleSidebar ──────────────────────────────────────────────────────────

  describe('toggleSidebar', () => {
    it('collapses the sidebar when it is expanded', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.toggleSidebar();
      });

      expect(result.current.sidebarCollapsed).toBe(true);
    });

    it('expands the sidebar when it is collapsed', () => {
      act(() => {
        useUiStore.setState({ sidebarCollapsed: true });
      });

      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.toggleSidebar();
      });

      expect(result.current.sidebarCollapsed).toBe(false);
    });

    it('toggles back and forth correctly', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => { result.current.toggleSidebar(); });
      expect(result.current.sidebarCollapsed).toBe(true);

      act(() => { result.current.toggleSidebar(); });
      expect(result.current.sidebarCollapsed).toBe(false);
    });
  });

  // ── setSidebarCollapsed ────────────────────────────────────────────────────

  describe('setSidebarCollapsed', () => {
    it('sets sidebarCollapsed to true', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.setSidebarCollapsed(true);
      });

      expect(result.current.sidebarCollapsed).toBe(true);
    });

    it('sets sidebarCollapsed to false', () => {
      act(() => {
        useUiStore.setState({ sidebarCollapsed: true });
      });

      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.setSidebarCollapsed(false);
      });

      expect(result.current.sidebarCollapsed).toBe(false);
    });
  });

  // ── setMobileSidebarOpen ───────────────────────────────────────────────────

  describe('setMobileSidebarOpen', () => {
    it('opens the mobile sidebar', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.setMobileSidebarOpen(true);
      });

      expect(result.current.mobileSidebarOpen).toBe(true);
    });

    it('closes the mobile sidebar', () => {
      act(() => {
        useUiStore.setState({ mobileSidebarOpen: true });
      });

      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.setMobileSidebarOpen(false);
      });

      expect(result.current.mobileSidebarOpen).toBe(false);
    });
  });

  // ── toggleTheme ────────────────────────────────────────────────────────────

  describe('toggleTheme', () => {
    it('switches from dark to light', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.toggleTheme();
      });

      expect(result.current.theme).toBe('light');
    });

    it('switches from light back to dark', () => {
      act(() => {
        useUiStore.setState({ theme: 'light' });
      });

      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.toggleTheme();
      });

      expect(result.current.theme).toBe('dark');
    });

    it('can toggle multiple times in sequence', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => { result.current.toggleTheme(); }); // → light
      expect(result.current.theme).toBe('light');

      act(() => { result.current.toggleTheme(); }); // → dark
      expect(result.current.theme).toBe('dark');

      act(() => { result.current.toggleTheme(); }); // → light
      expect(result.current.theme).toBe('light');
    });
  });

  // ── addToast ───────────────────────────────────────────────────────────────

  describe('addToast', () => {
    it('adds a toast to the array', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.addToast({ title: 'Hello', variant: 'success' });
      });

      expect(result.current.toasts).toHaveLength(1);
      expect(result.current.toasts[0].title).toBe('Hello');
      expect(result.current.toasts[0].variant).toBe('success');
    });

    it('auto-assigns a unique id to each toast', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.addToast({ title: 'First', variant: 'success' });
        result.current.addToast({ title: 'Second', variant: 'error' });
      });

      expect(result.current.toasts).toHaveLength(2);
      expect(result.current.toasts[0].id).toBeTruthy();
      expect(result.current.toasts[1].id).toBeTruthy();
      expect(result.current.toasts[0].id).not.toBe(result.current.toasts[1].id);
    });

    it('preserves optional description field', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.addToast({
          title: 'With description',
          description: 'Extra detail',
          variant: 'default',
        });
      });

      expect(result.current.toasts[0].description).toBe('Extra detail');
    });

    it('preserves optional duration field', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.addToast({ title: 'Timed', variant: 'warning', duration: 9000 });
      });

      expect(result.current.toasts[0].duration).toBe(9000);
    });

    it('accumulates multiple toasts', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.addToast({ title: 'A', variant: 'success' });
        result.current.addToast({ title: 'B', variant: 'error' });
        result.current.addToast({ title: 'C', variant: 'warning' });
      });

      expect(result.current.toasts).toHaveLength(3);
    });
  });

  // ── removeToast ────────────────────────────────────────────────────────────

  describe('removeToast', () => {
    it('removes a toast by id', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.addToast({ title: 'Removable', variant: 'success' });
      });

      const id = result.current.toasts[0].id;

      act(() => {
        result.current.removeToast(id);
      });

      expect(result.current.toasts).toHaveLength(0);
    });

    it('only removes the matching toast, leaving others intact', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.addToast({ title: 'First', variant: 'success' });
        result.current.addToast({ title: 'Second', variant: 'error' });
      });

      const firstId = result.current.toasts[0].id;

      act(() => {
        result.current.removeToast(firstId);
      });

      expect(result.current.toasts).toHaveLength(1);
      expect(result.current.toasts[0].title).toBe('Second');
    });

    it('is a no-op when the id does not exist', () => {
      const { result } = renderHook(() => useUiStore());

      act(() => {
        result.current.addToast({ title: 'Only one', variant: 'default' });
      });

      act(() => {
        result.current.removeToast('nonexistent-id');
      });

      expect(result.current.toasts).toHaveLength(1);
    });
  });

  // ── toast convenience helpers ──────────────────────────────────────────────

  describe('toast helpers', () => {
    it('toast.success adds a success variant toast with 4 s duration', () => {
      act(() => {
        toast.success('Saved!');
      });

      const toasts = useUiStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0].variant).toBe('success');
      expect(toasts[0].title).toBe('Saved!');
      expect(toasts[0].duration).toBe(4000);
    });

    it('toast.error adds an error variant toast with 6 s duration', () => {
      act(() => {
        toast.error('Something went wrong', 'Please try again');
      });

      const toasts = useUiStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0].variant).toBe('error');
      expect(toasts[0].description).toBe('Please try again');
      expect(toasts[0].duration).toBe(6000);
    });

    it('toast.warning adds a warning variant toast with 5 s duration', () => {
      act(() => {
        toast.warning('Expiring soon');
      });

      const toasts = useUiStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0].variant).toBe('warning');
      expect(toasts[0].duration).toBe(5000);
    });

    it('toast.info adds a default variant toast with 4 s duration', () => {
      act(() => {
        toast.info('Did you know?');
      });

      const toasts = useUiStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0].variant).toBe('default');
      expect(toasts[0].duration).toBe(4000);
    });

    it('toast helpers accept an optional description', () => {
      act(() => {
        toast.success('Upload complete', '3 files processed');
      });

      const toasts = useUiStore.getState().toasts;
      expect(toasts[0].description).toBe('3 files processed');
    });

    it('description is undefined when not provided', () => {
      act(() => {
        toast.success('No description');
      });

      const toasts = useUiStore.getState().toasts;
      expect(toasts[0].description).toBeUndefined();
    });

    it('each toast helper call appends a new toast', () => {
      act(() => {
        toast.success('One');
        toast.error('Two');
        toast.warning('Three');
        toast.info('Four');
      });

      expect(useUiStore.getState().toasts).toHaveLength(4);
    });
  });
});
