import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '../../utils';
import { TicketPriorityBadge } from '@/components/tickets/TicketStatusBadge';

describe('TicketPriorityBadge', () => {
  it('renders LOW priority with uppercase text', () => {
    render(<TicketPriorityBadge priority="LOW" />);
    expect(screen.getByText('LOW')).toBeInTheDocument();
  });

  it('renders MEDIUM priority with uppercase text', () => {
    render(<TicketPriorityBadge priority="MEDIUM" />);
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
  });

  it('renders HIGH priority with uppercase text', () => {
    render(<TicketPriorityBadge priority="HIGH" />);
    expect(screen.getByText('HIGH')).toBeInTheDocument();
  });

  it('renders CRITICAL priority with uppercase text', () => {
    render(<TicketPriorityBadge priority="CRITICAL" />);
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
  });

  it('LOW badge has slate color styling', () => {
    render(<TicketPriorityBadge priority="LOW" />);
    const badge = screen.getByText('LOW');
    expect(badge).toHaveClass('text-slate-400');
  });

  it('MEDIUM badge has blue color styling', () => {
    render(<TicketPriorityBadge priority="MEDIUM" />);
    const badge = screen.getByText('MEDIUM');
    expect(badge).toHaveClass('text-blue-400');
  });

  it('HIGH badge has amber color styling', () => {
    render(<TicketPriorityBadge priority="HIGH" />);
    const badge = screen.getByText('HIGH');
    expect(badge).toHaveClass('text-amber-400');
  });

  it('CRITICAL badge has red color styling', () => {
    render(<TicketPriorityBadge priority="CRITICAL" />);
    const badge = screen.getByText('CRITICAL');
    expect(badge).toHaveClass('text-red-400');
  });

  it('renders as a span element', () => {
    render(<TicketPriorityBadge priority="HIGH" />);
    const badge = screen.getByText('HIGH');
    expect(badge.tagName.toLowerCase()).toBe('span');
  });

  it('accepts optional className prop', () => {
    render(<TicketPriorityBadge priority="HIGH" className="extra-class" />);
    const badge = screen.getByText('HIGH');
    expect(badge).toHaveClass('extra-class');
  });
});
