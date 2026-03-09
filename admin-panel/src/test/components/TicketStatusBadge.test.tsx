import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TicketStatusBadge, TicketPriorityBadge } from '@/components/tickets/TicketStatusBadge';
import type { TicketStatus, TicketPriority } from '@/types/ticket';

describe('TicketStatusBadge', () => {
  const statuses: { status: TicketStatus; expectedLabel: string; colorClass: string }[] = [
    { status: 'OPEN',             expectedLabel: 'Open',             colorClass: 'text-blue-400' },
    { status: 'ASSIGNED',         expectedLabel: 'Assigned',         colorClass: 'text-amber-400' },
    { status: 'IN_PROGRESS',      expectedLabel: 'In Progress',      colorClass: 'text-orange-400' },
    { status: 'PENDING_CUSTOMER', expectedLabel: 'Pending Customer', colorClass: 'text-purple-400' },
    { status: 'RESOLVED',         expectedLabel: 'Resolved',         colorClass: 'text-emerald-400' },
    { status: 'CLOSED',           expectedLabel: 'Closed',           colorClass: 'text-slate-400' },
  ];

  statuses.forEach(({ status, expectedLabel, colorClass }) => {
    it(`renders label "${expectedLabel}" for status ${status}`, () => {
      render(<TicketStatusBadge status={status} />);
      expect(screen.getByText(expectedLabel)).toBeInTheDocument();
    });

    it(`applies color class "${colorClass}" for status ${status}`, () => {
      const { container } = render(<TicketStatusBadge status={status} />);
      expect((container.firstChild as HTMLElement).className).toContain(colorClass);
    });
  });
});

describe('TicketPriorityBadge', () => {
  const priorities: { priority: TicketPriority; colorClass: string }[] = [
    { priority: 'LOW',      colorClass: 'text-slate-400' },
    { priority: 'MEDIUM',   colorClass: 'text-blue-400' },
    { priority: 'HIGH',     colorClass: 'text-amber-400' },
    { priority: 'CRITICAL', colorClass: 'text-red-400' },
  ];

  priorities.forEach(({ priority, colorClass }) => {
    it(`renders label text for ${priority}`, () => {
      render(<TicketPriorityBadge priority={priority} />);
      expect(screen.getByText(priority)).toBeInTheDocument();
    });

    it(`applies color class "${colorClass}" for ${priority}`, () => {
      const { container } = render(<TicketPriorityBadge priority={priority} />);
      expect((container.firstChild as HTMLElement).className).toContain(colorClass);
    });
  });
});
