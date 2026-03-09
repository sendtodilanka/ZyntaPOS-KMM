import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { RoleAssignment } from '@/components/users/RoleAssignment';
import type { AdminRole } from '@/types/user';

const defaultProps = {
  value: 'OPERATOR' as AdminRole,
  onChange: vi.fn(),
};

describe('RoleAssignment', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders all 5 role buttons', () => {
    render(<RoleAssignment {...defaultProps} />);
    expect(screen.getByText('Admin')).toBeInTheDocument();
    expect(screen.getByText('Operator')).toBeInTheDocument();
    expect(screen.getByText('Finance')).toBeInTheDocument();
    expect(screen.getByText('Auditor')).toBeInTheDocument();
    expect(screen.getByText('Helpdesk')).toBeInTheDocument();
  });

  it('selected role button has active styling (contains role-specific color class)', () => {
    render(<RoleAssignment value="OPERATOR" onChange={vi.fn()} />);
    const operatorButton = screen.getByText('Operator').closest('button');
    expect(operatorButton).toHaveClass('text-yellow-400');
  });

  it('non-selected roles do not have active styling', () => {
    render(<RoleAssignment value="OPERATOR" onChange={vi.fn()} />);
    const adminButton = screen.getByText('Admin').closest('button');
    expect(adminButton).not.toHaveClass('text-red-400');
  });

  it('clicking a different role calls onChange with that role', () => {
    const onChange = vi.fn();
    render(<RoleAssignment value="OPERATOR" onChange={onChange} />);
    fireEvent.click(screen.getByText('Admin'));
    expect(onChange).toHaveBeenCalledWith('ADMIN');
  });

  it('clicking Finance role calls onChange with FINANCE', () => {
    const onChange = vi.fn();
    render(<RoleAssignment value="ADMIN" onChange={onChange} />);
    fireEvent.click(screen.getByText('Finance'));
    expect(onChange).toHaveBeenCalledWith('FINANCE');
  });

  it('clicking Auditor role calls onChange with AUDITOR', () => {
    const onChange = vi.fn();
    render(<RoleAssignment value="ADMIN" onChange={onChange} />);
    fireEvent.click(screen.getByText('Auditor'));
    expect(onChange).toHaveBeenCalledWith('AUDITOR');
  });

  it('clicking Helpdesk role calls onChange with HELPDESK', () => {
    const onChange = vi.fn();
    render(<RoleAssignment value="ADMIN" onChange={onChange} />);
    fireEvent.click(screen.getByText('Helpdesk'));
    expect(onChange).toHaveBeenCalledWith('HELPDESK');
  });

  it('each role button shows its name and description text', () => {
    render(<RoleAssignment {...defaultProps} />);
    expect(screen.getByText('Full access to all panel features')).toBeInTheDocument();
    expect(screen.getByText('Store ops, sync, tickets, diagnostics')).toBeInTheDocument();
    expect(screen.getByText('Financial reports and license exports')).toBeInTheDocument();
    expect(screen.getByText('Read-only audit log and reports')).toBeInTheDocument();
    expect(screen.getByText('Support tickets and store diagnostics')).toBeInTheDocument();
  });

  it('ADMIN role selected has active red styling', () => {
    render(<RoleAssignment value="ADMIN" onChange={vi.fn()} />);
    const adminButton = screen.getByText('Admin').closest('button');
    expect(adminButton).toHaveClass('text-red-400');
  });

  it('FINANCE role selected has active green styling', () => {
    render(<RoleAssignment value="FINANCE" onChange={vi.fn()} />);
    const financeButton = screen.getByText('Finance').closest('button');
    expect(financeButton).toHaveClass('text-green-400');
  });
});
