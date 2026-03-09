import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '../../utils';
import { Breadcrumbs } from '@/components/layout/Breadcrumbs';
import * as tanstackRouter from '@tanstack/react-router';

// Mock TanStack Router so the component can render without a real router context.
// Link is replaced with a plain <a> tag; useRouterState returns a controllable location.
vi.mock('@tanstack/react-router', () => ({
  useRouterState: vi.fn(() => ({
    location: { pathname: '/licenses/ZYNTA-1234' },
  })),
  Link: ({ children, to, ...props }: { children: React.ReactNode; to: string; [key: string]: unknown }) => (
    <a href={to} {...props}>{children}</a>
  ),
}));

// Helper that mutates the mock's return value between tests
function setPathname(pathname: string) {
  vi.mocked(tanstackRouter.useRouterState).mockReturnValue({
    location: { pathname },
  } as ReturnType<typeof tanstackRouter.useRouterState>);
}

describe('Breadcrumbs', () => {
  it('renders home icon link for the root segment', () => {
    setPathname('/licenses/ZYNTA-1234');
    render(<Breadcrumbs />);
    // Home link points to "/"
    // Home icon is rendered inside the first link — just verify the link exists and has the right href
    const homeAnchor = document.querySelector('a[href="/"]') as HTMLAnchorElement;
    expect(homeAnchor).toBeInTheDocument();
  });

  it('renders first path segment ("licenses") as a clickable link', () => {
    setPathname('/licenses/ZYNTA-1234');
    render(<Breadcrumbs />);
    // ROUTE_LABELS maps "licenses" → "Licenses"
    const link = screen.getByRole('link', { name: 'Licenses' });
    expect(link).toBeInTheDocument();
    expect((link as HTMLAnchorElement).href).toContain('/licenses');
  });

  it('renders the last segment as plain text (not a link)', () => {
    setPathname('/licenses/ZYNTA-1234');
    render(<Breadcrumbs />);
    // The last segment is truncated to 12 chars: "ZYNTA-1234" is 10 chars so shown as-is
    const currentPage = screen.getByText('ZYNTA-1234');
    expect(currentPage.tagName).not.toBe('A');
  });

  it('renders nav element with aria-label="Breadcrumb"', () => {
    setPathname('/licenses/ZYNTA-1234');
    render(<Breadcrumbs />);
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toBeInTheDocument();
  });

  it('renders Dashboard with home icon for root path', () => {
    setPathname('/');
    render(<Breadcrumbs />);
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });

  it('renders mapped label for known route "users"', () => {
    setPathname('/users');
    render(<Breadcrumbs />);
    // "users" is the only segment and it's the last → rendered as plain text using ROUTE_LABELS
    expect(screen.getByText('Users')).toBeInTheDocument();
  });

  it('renders mapped label for known route "audit"', () => {
    setPathname('/audit');
    render(<Breadcrumbs />);
    expect(screen.getByText('Audit Log')).toBeInTheDocument();
  });

  it('truncates long segment IDs to 12 characters with ellipsis', () => {
    // Segment longer than 12 chars should be truncated
    setPathname('/licenses/ABCDEFGHIJKLMNOP');
    render(<Breadcrumbs />);
    // "ABCDEFGHIJK…" (10 chars + ellipsis)
    expect(screen.getByText('ABCDEFGHIJ\u2026')).toBeInTheDocument();
  });

  it('renders intermediate segments as links and last segment as text', () => {
    setPathname('/stores/store-123');
    render(<Breadcrumbs />);

    // "stores" → link
    expect(screen.getByRole('link', { name: 'Stores' })).toBeInTheDocument();
    // "store-123" → plain text (last segment, 9 chars ≤ 12)
    const lastSegment = screen.getByText('store-123');
    expect(lastSegment.tagName).not.toBe('A');
  });

  it('does not render breadcrumb nav for root "/" path', () => {
    setPathname('/');
    render(<Breadcrumbs />);
    // Root path shows the home+Dashboard fallback, not the <nav> breadcrumb
    expect(screen.queryByRole('navigation', { name: 'Breadcrumb' })).not.toBeInTheDocument();
  });
});
