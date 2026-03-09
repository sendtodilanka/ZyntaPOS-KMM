import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import { DateRangePicker } from '@/components/shared/DateRangePicker';

describe('DateRangePicker', () => {
  it('renders without a value and shows placeholder text', () => {
    render(<DateRangePicker onChange={vi.fn()} />);
    // When no value is provided the label defaults to "All time"
    expect(screen.getByText('All time')).toBeInTheDocument();
  });

  it('renders with existing from/to values formatted as a range label', () => {
    render(
      <DateRangePicker
        value={{ from: '2024-01-01', to: '2024-01-31' }}
        onChange={vi.fn()}
      />,
    );
    // The label should show "from → to"
    expect(screen.getByText('2024-01-01 → 2024-01-31')).toBeInTheDocument();
  });

  it('clicking the button opens the dropdown with preset options', () => {
    render(<DateRangePicker onChange={vi.fn()} />);
    // Presets not visible yet
    expect(screen.queryByText('Today')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Today')).toBeInTheDocument();
    expect(screen.getByText('Last 7 days')).toBeInTheDocument();
    expect(screen.getByText('Last 30 days')).toBeInTheDocument();
    expect(screen.getByText('This week')).toBeInTheDocument();
    expect(screen.getByText('This month')).toBeInTheDocument();
  });

  it('clicking "Today" preset calls onChange with a same-day range', () => {
    const onChange = vi.fn();
    render(<DateRangePicker onChange={onChange} />);

    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Today'));

    expect(onChange).toHaveBeenCalledOnce();
    const [range] = onChange.mock.calls[0] as [{ from: string; to: string }];
    // Both from and to should be the same date (today)
    expect(range.from).toBe(range.to);
    // Matches yyyy-MM-dd format
    expect(range.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('clicking "Last 7 days" calls onChange with a 7-day range', () => {
    const onChange = vi.fn();
    render(<DateRangePicker onChange={onChange} />);

    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Last 7 days'));

    expect(onChange).toHaveBeenCalledOnce();
    const [range] = onChange.mock.calls[0] as [{ from: string; to: string }];
    expect(range.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(range.to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    // from must be before to
    expect(new Date(range.from) < new Date(range.to)).toBe(true);
  });

  it('clicking "Last 30 days" calls onChange with a 30-day range', () => {
    const onChange = vi.fn();
    render(<DateRangePicker onChange={onChange} />);

    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Last 30 days'));

    expect(onChange).toHaveBeenCalledOnce();
    const [range] = onChange.mock.calls[0] as [{ from: string; to: string }];
    expect(new Date(range.from) < new Date(range.to)).toBe(true);
  });

  it('clicking a preset closes the dropdown', () => {
    render(<DateRangePicker onChange={vi.fn()} />);

    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Today')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Today'));
    expect(screen.queryByText('Today')).not.toBeInTheDocument();
  });

  it('clicking "All time" clear option calls onChange with undefined', () => {
    const onChange = vi.fn();
    render(
      <DateRangePicker
        value={{ from: '2024-01-01', to: '2024-01-31' }}
        onChange={onChange}
      />,
    );

    fireEvent.click(screen.getByRole('button'));
    // "All time" acts as the reset/clear option inside the dropdown
    const allTimeButtons = screen.getAllByText('All time');
    // The one inside the dropdown (not the trigger label) resets the value
    const dropdownAllTime = allTimeButtons[allTimeButtons.length - 1];
    fireEvent.click(dropdownAllTime);

    expect(onChange).toHaveBeenCalledOnce();
    expect(onChange).toHaveBeenCalledWith(undefined);
  });

  it('Apply button is disabled when custom inputs are empty', () => {
    render(<DateRangePicker onChange={vi.fn()} />);

    fireEvent.click(screen.getByRole('button'));
    const applyButton = screen.getByRole('button', { name: /apply/i });
    expect(applyButton).toBeDisabled();
  });

  it('Apply button calls onChange with custom range when both inputs are filled', () => {
    const onChange = vi.fn();
    render(<DateRangePicker onChange={onChange} />);

    fireEvent.click(screen.getByRole('button'));

    const [fromInput, toInput] = screen.getAllByDisplayValue('');
    fireEvent.change(fromInput, { target: { value: '2024-03-01' } });
    fireEvent.change(toInput, { target: { value: '2024-03-31' } });

    fireEvent.click(screen.getByRole('button', { name: /apply/i }));

    expect(onChange).toHaveBeenCalledOnce();
    expect(onChange).toHaveBeenCalledWith({ from: '2024-03-01', to: '2024-03-31' });
  });
});
