import { describe, it, expect } from 'vitest';
import { render, screen } from '../utils';
import { KpiCard } from '@/components/shared/KpiCard';
import { DollarSign } from 'lucide-react';

describe('KpiCard', () => {
  it('renders label and value', () => {
    render(<KpiCard label="Revenue" value="LKR 1,500" icon={DollarSign} />);
    expect(screen.getByText('Revenue')).toBeInTheDocument();
    expect(screen.getByText('LKR 1,500')).toBeInTheDocument();
  });

  it('renders positive trend', () => {
    render(<KpiCard label="Revenue" value="1,500" icon={DollarSign} trend={12.5} />);
    expect(screen.getByText(/12\.5/)).toBeInTheDocument();
  });

  it('renders negative trend', () => {
    render(<KpiCard label="Revenue" value="1,500" icon={DollarSign} trend={-5.3} />);
    expect(screen.getByText(/5\.3/)).toBeInTheDocument();
  });

  it('renders skeleton when loading', () => {
    const { container } = render(<KpiCard label="Revenue" value="1,500" icon={DollarSign} loading />);
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });
});
