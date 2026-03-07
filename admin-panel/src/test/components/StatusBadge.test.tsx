import { describe, it, expect } from 'vitest';
import { render, screen } from '../utils';
import { StatusBadge } from '@/components/shared/StatusBadge';

describe('StatusBadge', () => {
  it('renders ACTIVE status', () => {
    render(<StatusBadge status="ACTIVE" />);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('renders EXPIRED status', () => {
    render(<StatusBadge status="EXPIRED" />);
    expect(screen.getByText('Expired')).toBeInTheDocument();
  });

  it('renders SUSPENDED status', () => {
    render(<StatusBadge status="SUSPENDED" />);
    expect(screen.getByText('Suspended')).toBeInTheDocument();
  });

  it('renders PENDING status', () => {
    render(<StatusBadge status="PENDING" />);
    expect(screen.getByText('Pending')).toBeInTheDocument();
  });

  it('renders HEALTHY status', () => {
    render(<StatusBadge status="HEALTHY" />);
    expect(screen.getByText('Healthy')).toBeInTheDocument();
  });

  it('renders unknown status as-is', () => {
    render(<StatusBadge status="UNKNOWN_STATUS" />);
    expect(screen.getByText('Unknown Status')).toBeInTheDocument();
  });
});
