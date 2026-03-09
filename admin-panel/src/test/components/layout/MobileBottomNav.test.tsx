import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { MobileBottomNav } from '@/components/layout/MobileBottomNav';
import { useUiStore } from '@/stores/ui-store';
import * as tanstackRouter from '@tanstack/react-router';

// Mock TanStack Router — MobileBottomNav uses useRouterState and Link
vi.mock('@tanstack/react-router', () => ({
  useRouterState: vi.fn(() => ({
    location: { pathname: '/' },
  })),
  Link: ({
    children,
    to,
    className,
    ...props
  }: {
    children: React.ReactNode;
    to: string;
    className?: string;
    [key: string]: unknown;
  }) => (
    <a href={to} className={className} {...props}>
      {children}
    </a>
  ),
}));

function setPathname(pathname: string) {
  vi.mocked(tanstackRouter.useRouterState).mockReturnValue({
    location: { pathname },
  } as ReturnType<typeof tanstackRouter.useRouterState>);
}

beforeEach(() => {
  setPathname('/');
  useUiStore.setState({ mobileSidebarOpen: false });
});

describe('MobileBottomNav', () => {
  it('renders all navigation tab labels', () => {
    render(<MobileBottomNav />);
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Licenses')).toBeInTheDocument();
    expect(screen.getByText('Stores')).toBeInTheDocument();
    expect(screen.getByText('Health')).toBeInTheDocument();
  });

  it('renders the "More" overflow button', () => {
    render(<MobileBottomNav />);
    expect(screen.getByText('More')).toBeInTheDocument();
  });

  it('renders a nav element', () => {
    render(<MobileBottomNav />);
    expect(screen.getByRole('navigation')).toBeInTheDocument();
  });

  it('Dashboard tab is a link pointing to "/"', () => {
    render(<MobileBottomNav />);
    const dashboardLink = screen.getByText('Dashboard').closest('a');
    expect(dashboardLink).toHaveAttribute('href', '/');
  });

  it('Licenses tab is a link pointing to "/licenses"', () => {
    render(<MobileBottomNav />);
    const licensesLink = screen.getByText('Licenses').closest('a');
    expect(licensesLink).toHaveAttribute('href', '/licenses');
  });

  it('Stores tab is a link pointing to "/stores"', () => {
    render(<MobileBottomNav />);
    const storesLink = screen.getByText('Stores').closest('a');
    expect(storesLink).toHaveAttribute('href', '/stores');
  });

  it('Health tab is a link pointing to "/health"', () => {
    render(<MobileBottomNav />);
    const healthLink = screen.getByText('Health').closest('a');
    expect(healthLink).toHaveAttribute('href', '/health');
  });

  it('Dashboard tab has brand colour class when path is "/"', () => {
    setPathname('/');
    render(<MobileBottomNav />);
    const dashboardLink = screen.getByText('Dashboard').closest('a');
    expect(dashboardLink?.className).toContain('brand-400');
  });

  it('Licenses tab has brand colour class when path starts with "/licenses"', () => {
    setPathname('/licenses');
    render(<MobileBottomNav />);
    const licensesLink = screen.getByText('Licenses').closest('a');
    expect(licensesLink?.className).toContain('brand-400');
  });

  it('Dashboard tab does not have active class when on "/licenses" path', () => {
    setPathname('/licenses');
    render(<MobileBottomNav />);
    const dashboardLink = screen.getByText('Dashboard').closest('a');
    // Active state uses "brand-400"; inactive uses "slate-400"
    expect(dashboardLink?.className).toContain('slate-400');
    expect(dashboardLink?.className).not.toContain('brand-400');
  });

  it('clicking "More" opens the mobile sidebar via setMobileSidebarOpen', () => {
    const setMobileSidebarOpen = vi.fn();
    useUiStore.setState({ mobileSidebarOpen: false, setMobileSidebarOpen });

    render(<MobileBottomNav />);
    fireEvent.click(screen.getByText('More'));

    expect(setMobileSidebarOpen).toHaveBeenCalledWith(true);
  });

  it('Stores tab is highlighted when on "/stores/store-abc" (prefix match)', () => {
    setPathname('/stores/store-abc');
    render(<MobileBottomNav />);
    const storesLink = screen.getByText('Stores').closest('a');
    expect(storesLink?.className).toContain('brand-400');
  });

  it('renders the component in a fixed-bottom container', () => {
    const { container } = render(<MobileBottomNav />);
    const nav = container.firstChild as HTMLElement;
    expect(nav.className).toContain('fixed');
    expect(nav.className).toContain('bottom-0');
  });
});
