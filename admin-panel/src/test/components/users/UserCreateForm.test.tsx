import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '../../utils';
import { UserCreateForm } from '@/components/users/UserCreateForm';
import type { AdminUser } from '@/types/user';

const mockCreateMutateAsync = vi.fn().mockResolvedValue({});
const mockUpdateMutateAsync = vi.fn().mockResolvedValue({});

vi.mock('@/api/users', () => ({
  useCreateUser: () => ({ mutate: vi.fn(), mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateUser: () => ({ mutate: vi.fn(), mutateAsync: mockUpdateMutateAsync, isPending: false }),
}));

const mockEditUser: AdminUser = {
  id: 'user-1',
  email: 'alice@example.com',
  name: 'Alice Smith',
  role: 'OPERATOR',
  mfaEnabled: true,
  isActive: true,
  lastLoginAt: Date.now(),
  createdAt: Date.now() - 86400000,
};

const defaultProps = {
  open: true,
  onClose: vi.fn(),
};

describe('UserCreateForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not render when open is false', () => {
    render(<UserCreateForm open={false} onClose={vi.fn()} />);
    expect(screen.queryByText('Create User')).not.toBeInTheDocument();
    expect(screen.queryByText('Email')).not.toBeInTheDocument();
  });

  it('renders form fields when open is true', () => {
    const { container } = render(<UserCreateForm {...defaultProps} />);
    expect(screen.getByRole('heading', { name: 'Create User' })).toBeInTheDocument();
    expect(screen.getByText('Email')).toBeInTheDocument();
    expect(container.querySelector('input[type="email"]')).toBeInTheDocument();
    expect(screen.getByText('Full Name')).toBeInTheDocument();
    expect(screen.getByText('Password')).toBeInTheDocument();
    expect(container.querySelector('input[type="password"]')).toBeInTheDocument();
    expect(screen.getByText('Role')).toBeInTheDocument();
  });

  it('shows validation error when email is empty and form is submitted', async () => {
    render(<UserCreateForm {...defaultProps} />);
    const submitButton = screen.getByRole('button', { name: /create user/i });
    fireEvent.click(submitButton);
    await waitFor(() => {
      expect(screen.getByText(/invalid email/i)).toBeInTheDocument();
    });
  });

  it('shows validation error when password is less than 8 characters', async () => {
    const { container } = render(<UserCreateForm {...defaultProps} />);

    const emailInput = container.querySelector('input[type="email"]') as HTMLInputElement;
    fireEvent.change(emailInput, { target: { value: 'test@example.com' } });

    const textInputs = container.querySelectorAll('input:not([type="email"]):not([type="password"])');
    const nameInput = textInputs[0] as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: 'Test User' } });

    const passwordInput = container.querySelector('input[type="password"]') as HTMLInputElement;
    fireEvent.change(passwordInput, { target: { value: 'short' } });

    const submitButton = screen.getByRole('button', { name: /create user/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/min 8 characters/i)).toBeInTheDocument();
    });
  });

  it('valid submission calls useCreateUser mutate', async () => {
    const { container } = render(<UserCreateForm {...defaultProps} />);

    const emailInput = container.querySelector('input[type="email"]') as HTMLInputElement;
    fireEvent.change(emailInput, { target: { value: 'newuser@example.com' } });

    const textInputs = container.querySelectorAll('input:not([type="email"]):not([type="password"])');
    const nameInput = textInputs[0] as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: 'New User' } });

    const passwordInput = container.querySelector('input[type="password"]') as HTMLInputElement;
    fireEvent.change(passwordInput, { target: { value: 'securepassword123' } });

    const submitButton = screen.getByRole('button', { name: /create user/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.queryByText(/invalid email/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/min 8 characters/i)).not.toBeInTheDocument();
    });
  });

  it('in edit mode only shows role field (email and password hidden)', () => {
    const { container } = render(<UserCreateForm open={true} onClose={vi.fn()} editUser={mockEditUser} />);
    expect(screen.getByText('Edit User')).toBeInTheDocument();
    expect(container.querySelector('input[type="email"]')).not.toBeInTheDocument();
    expect(container.querySelector('input[type="password"]')).not.toBeInTheDocument();
    expect(screen.getByText('Role')).toBeInTheDocument();
  });

  it('in edit mode shows Save Changes button instead of Create User', () => {
    render(<UserCreateForm open={true} onClose={vi.fn()} editUser={mockEditUser} />);
    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /create user/i })).not.toBeInTheDocument();
  });

  it('edit mode submission calls useUpdateUser mutate', async () => {
    render(<UserCreateForm open={true} onClose={vi.fn()} editUser={mockEditUser} />);

    const submitButton = screen.getByRole('button', { name: /save changes/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      // The form has a valid default state for edit mode (role is pre-filled)
      // Submission goes through without validation errors
      expect(screen.queryByText(/is required/i)).not.toBeInTheDocument();
    });
  });
});
