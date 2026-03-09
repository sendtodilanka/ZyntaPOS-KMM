import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { AuditFilterPanel } from '@/components/audit/AuditFilterPanel';
import type { AuditFilter } from '@/types/audit';

vi.mock('@/lib/utils', () => ({
  cn: (...args: unknown[]) => args.filter(Boolean).join(' '),
}));

vi.mock('@/components/shared/DateRangePicker', () => ({
  DateRangePicker: ({ onChange }: { onChange: (r: unknown) => void }) => (
    <button onClick={() => onChange(undefined)}>DateRangePicker</button>
  ),
}));

const emptyFilters: AuditFilter = { page: 0, size: 50 };

describe('AuditFilterPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders category dropdown', () => {
    render(<AuditFilterPanel filters={emptyFilters} onChange={vi.fn()} />);
    // The category select has "All Categories" as first option
    expect(screen.getByDisplayValue('All Categories')).toBeInTheDocument();
  });

  it('renders success/failure filter dropdown', () => {
    render(<AuditFilterPanel filters={emptyFilters} onChange={vi.fn()} />);
    expect(screen.getByDisplayValue('All Results')).toBeInTheDocument();
  });

  it('changing category filter calls onChange with updated filter', () => {
    const onChange = vi.fn();
    render(<AuditFilterPanel filters={emptyFilters} onChange={onChange} />);
    const categorySelect = screen.getByDisplayValue('All Categories');
    fireEvent.change(categorySelect, { target: { value: 'AUTH' } });
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'AUTH', page: 0 }),
    );
  });

  it('changing success filter calls onChange with updated filter', () => {
    const onChange = vi.fn();
    render(<AuditFilterPanel filters={emptyFilters} onChange={onChange} />);
    const successSelect = screen.getByDisplayValue('All Results');
    fireEvent.change(successSelect, { target: { value: 'true' } });
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ success: true, page: 0 }),
    );
  });

  it('changing success filter to false calls onChange with success: false', () => {
    const onChange = vi.fn();
    render(<AuditFilterPanel filters={emptyFilters} onChange={onChange} />);
    const successSelect = screen.getByDisplayValue('All Results');
    fireEvent.change(successSelect, { target: { value: 'false' } });
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ success: false, page: 0 }),
    );
  });

  it('clear button appears when filters are active', () => {
    const activeFilters: AuditFilter = { category: 'AUTH', page: 0, size: 50 };
    render(<AuditFilterPanel filters={activeFilters} onChange={vi.fn()} />);
    // Clear button and "Filtered" badge appear when hasFilters is true
    expect(screen.getByText('Filtered')).toBeInTheDocument();
  });

  it('clicking clear resets filters', () => {
    const onChange = vi.fn();
    const activeFilters: AuditFilter = { category: 'AUTH', page: 0, size: 50 };
    render(<AuditFilterPanel filters={activeFilters} onChange={onChange} />);
    // Find the clear button (contains an X icon and optionally "Clear" text)
    const buttons = screen.getAllByRole('button');
    // The clear button is the first button with an SVG (X icon)
    const clearBtn = buttons.find((btn) => btn.querySelector('svg') !== null);
    expect(clearBtn).toBeDefined();
    fireEvent.click(clearBtn!);
    expect(onChange).toHaveBeenCalledWith({ page: 0, size: 50 });
  });
});
