import { useEffect } from 'react';

function isInputElement(el: EventTarget | null): boolean {
  if (!el || !(el instanceof HTMLElement)) return false;
  const tag = el.tagName.toLowerCase();
  return tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable;
}

/**
 * Registers global keyboard shortcuts for the admin panel:
 * - '/' → focus the main search input (if visible)
 * - 'Escape' → close open dialogs / clear search focus
 * - 'Ctrl+K' (or Cmd+K on Mac) → focus the main search input
 *
 * Shortcuts are suppressed when focus is inside an input, textarea, or select.
 * Register this hook once in AppShell.
 */
export function useKeyboard() {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Cmd+K or Ctrl+K — open global search
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        focusSearch();
        return;
      }

      // Ignore if focus is inside an editable field
      if (isInputElement(document.activeElement)) return;

      if (e.key === '/') {
        e.preventDefault();
        focusSearch();
        return;
      }

      if (e.key === 'Escape') {
        blurActive();
        return;
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, []);
}

function focusSearch() {
  const search = document.querySelector<HTMLInputElement>(
    'input[type="search"], input[placeholder*="Search"], input[placeholder*="search"]',
  );
  search?.focus();
  search?.select();
}

function blurActive() {
  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur();
  }
}
