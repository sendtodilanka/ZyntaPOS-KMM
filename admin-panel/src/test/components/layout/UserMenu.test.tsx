import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { UserMenu } from '@/components/layout/UserMenu';
import { useAuthStore } from '@/stores/auth-store';
import type { AdminUser } from '@/types/user';
import * as authApi from '@/api/auth';

// Mock the auth API so no real HTTP calls are made.
// useAdminLogout returns a stable mock object; individual tests can spy on mutate.
vi.mock('@/api/auth', () => ({
  useAdminLogout: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

// Mock TanStack Router — UserMenu calls useNavigate for the profile link
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}));

const testUser: AdminUser = {
  id: 'u1',
  email: 'alice@zyntasolutions.com',
  name: 'Alice Wonderland',
  role: 'ADMIN',
  mfaEnabled: false,
  isActive: true,
  lastLoginAt: null,
  createdAt: Date.now(),
};

beforeEach(() => {
  // Set a logged-in user before every test
  useAuthStore.setState({ user: testUser, isLoading: false });
});

describe('UserMenu', () => {
  it('renders user initials derived from the user name', () => {
    render(<UserMenu />);
    // "Alice Wonderland" → initials "AW"
    expect(screen.getByText('AW')).toBeInTheDocument();
  });

  it('renders the user name in the trigger button', () => {
    render(<UserMenu />);
    expect(screen.getByText('Alice Wonderland')).toBeInTheDocument();
  });

  it('renders nothing when there is no authenticated user', () => {
    useAuthStore.setState({ user: null, isLoading: false });
    const { container } = render(<UserMenu />);
    expect(container).toBeEmptyDOMElement();
  });

  it('dropdown is closed initially', () => {
    render(<UserMenu />);
    expect(screen.queryByText('Profile')).not.toBeInTheDocument();
    expect(screen.queryByText('Sign out')).not.toBeInTheDocument();
  });

  it('clicking the user button opens the dropdown menu', () => {
    render(<UserMenu />);
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));
    expect(screen.getByText('Profile')).toBeInTheDocument();
    expect(screen.getByText('Sign out')).toBeInTheDocument();
  });

  it('shows the user email in the open dropdown', () => {
    render(<UserMenu />);
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));
    expect(screen.getByText('alice@zyntasolutions.com')).toBeInTheDocument();
  });

  it('shows the user role badge in the open dropdown', () => {
    render(<UserMenu />);
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));
    expect(screen.getByText('ADMIN')).toBeInTheDocument();
  });

  it('"Profile" item is visible after opening the menu', () => {
    render(<UserMenu />);
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));
    expect(screen.getByText('Profile')).toBeInTheDocument();
  });

  it('"Sign out" calls the logout mutation when clicked', () => {
    const mutateFn = vi.fn();
    vi.mocked(authApi.useAdminLogout).mockReturnValue({
      mutate: mutateFn,
      isPending: false,
    } as unknown as ReturnType<typeof authApi.useAdminLogout>);

    render(<UserMenu />);
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));
    fireEvent.click(screen.getByText('Sign out'));

    expect(mutateFn).toHaveBeenCalledOnce();
  });

  it('closes the dropdown when clicking outside', () => {
    render(
      <div>
        <UserMenu />
        <p>Outside</p>
      </div>,
    );
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }));
    expect(screen.getByText('Profile')).toBeInTheDocument();

    fireEvent.mouseDown(screen.getByText('Outside'));
    expect(screen.queryByText('Profile')).not.toBeInTheDocument();
  });

  it('renders a single initial when name is a single word', () => {
    useAuthStore.setState({
      user: { ...testUser, name: 'Administrator' },
      isLoading: false,
    });
    render(<UserMenu />);
    // Single-word name: split gives one token → one initial letter "A"
    expect(screen.getByText('A')).toBeInTheDocument();
  });

  it('trigger button has aria-expanded=false when closed', () => {
    render(<UserMenu />);
    const btn = screen.getByRole('button', { name: /user menu/i });
    expect(btn).toHaveAttribute('aria-expanded', 'false');
  });

  it('trigger button has aria-expanded=true when open', () => {
    render(<UserMenu />);
    const btn = screen.getByRole('button', { name: /user menu/i });
    fireEvent.click(btn);
    expect(btn).toHaveAttribute('aria-expanded', 'true');
  });
});
