import { describe, it, expect } from 'vitest';
import { render, screen } from '../utils';
import { TicketStatusBadge, TicketPriorityBadge } from '@/components/tickets/TicketStatusBadge';

describe('TicketStatusBadge', () => {
  it('renders OPEN status', () => {
    render(<TicketStatusBadge status="OPEN" />);
    expect(screen.getByText('Open')).toBeInTheDocument();
  });

  it('renders ASSIGNED status', () => {
    render(<TicketStatusBadge status="ASSIGNED" />);
    expect(screen.getByText('Assigned')).toBeInTheDocument();
  });

  it('renders IN_PROGRESS status', () => {
    render(<TicketStatusBadge status="IN_PROGRESS" />);
    expect(screen.getByText('In Progress')).toBeInTheDocument();
  });

  it('renders PENDING_CUSTOMER status', () => {
    render(<TicketStatusBadge status="PENDING_CUSTOMER" />);
    expect(screen.getByText('Pending Customer')).toBeInTheDocument();
  });

  it('renders RESOLVED status', () => {
    render(<TicketStatusBadge status="RESOLVED" />);
    expect(screen.getByText('Resolved')).toBeInTheDocument();
  });

  it('renders CLOSED status', () => {
    render(<TicketStatusBadge status="CLOSED" />);
    expect(screen.getByText('Closed')).toBeInTheDocument();
  });
});

describe('TicketPriorityBadge', () => {
  it('renders LOW priority', () => {
    render(<TicketPriorityBadge priority="LOW" />);
    expect(screen.getByText('LOW')).toBeInTheDocument();
  });

  it('renders CRITICAL priority', () => {
    render(<TicketPriorityBadge priority="CRITICAL" />);
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
  });

  it('renders HIGH priority', () => {
    render(<TicketPriorityBadge priority="HIGH" />);
    expect(screen.getByText('HIGH')).toBeInTheDocument();
  });

  it('renders MEDIUM priority', () => {
    render(<TicketPriorityBadge priority="MEDIUM" />);
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
  });
});
