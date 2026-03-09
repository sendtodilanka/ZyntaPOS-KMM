import { describe, it, expect } from 'vitest';
import { render } from '../utils';
import {
  Skeleton,
  CardSkeleton,
  TableSkeleton,
  LoadingSpinner,
  PageLoader,
} from '@/components/shared/LoadingState';

describe('Skeleton', () => {
  it('renders with animate-pulse class', () => {
    const { container } = render(<Skeleton />);
    const el = container.firstChild as HTMLElement;
    expect(el).toBeInTheDocument();
    expect(el.className).toContain('animate-pulse');
  });

  it('applies custom className', () => {
    const { container } = render(<Skeleton className="h-4 w-1/3" />);
    const el = container.firstChild as HTMLElement;
    expect(el.className).toContain('h-4');
    expect(el.className).toContain('w-1/3');
  });
});

describe('CardSkeleton', () => {
  it('renders multiple skeleton rows', () => {
    const { container } = render(<CardSkeleton />);
    const skeletons = container.querySelectorAll('.animate-pulse');
    expect(skeletons.length).toBeGreaterThanOrEqual(3);
  });
});

describe('TableSkeleton', () => {
  it('renders with default row count of 5', () => {
    const { container } = render(<TableSkeleton />);
    // Default is rows=5, plus the header row = 6 animate-pulse elements
    const skeletons = container.querySelectorAll('.animate-pulse');
    // At minimum the default rows should be present (5 data rows + 1 header)
    expect(skeletons.length).toBe(6);
  });

  it('renders with custom row count', () => {
    const { container } = render(<TableSkeleton rows={3} />);
    // 3 data rows + 1 header row
    const skeletons = container.querySelectorAll('.animate-pulse');
    expect(skeletons.length).toBe(4);
  });

  it('renders 1 row when rows=1', () => {
    const { container } = render(<TableSkeleton rows={1} />);
    const skeletons = container.querySelectorAll('.animate-pulse');
    expect(skeletons.length).toBe(2);
  });
});

describe('LoadingSpinner', () => {
  it('renders a spinner element', () => {
    const { container } = render(<LoadingSpinner />);
    const spinner = container.firstChild as HTMLElement;
    expect(spinner).toBeInTheDocument();
    expect(spinner.className).toContain('animate-spin');
  });

  it('applies the medium size class by default', () => {
    const { container } = render(<LoadingSpinner />);
    const spinner = container.firstChild as HTMLElement;
    expect(spinner.className).toContain('w-8');
    expect(spinner.className).toContain('h-8');
  });

  it('applies small size class when size="sm"', () => {
    const { container } = render(<LoadingSpinner size="sm" />);
    const spinner = container.firstChild as HTMLElement;
    expect(spinner.className).toContain('w-4');
    expect(spinner.className).toContain('h-4');
  });

  it('applies large size class when size="lg"', () => {
    const { container } = render(<LoadingSpinner size="lg" />);
    const spinner = container.firstChild as HTMLElement;
    expect(spinner.className).toContain('w-12');
    expect(spinner.className).toContain('h-12');
  });
});

describe('PageLoader', () => {
  it('renders spinner in a centering layout', () => {
    const { container } = render(<PageLoader />);
    // The outer wrapper should use flex + justify-center + items-center
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper).toBeInTheDocument();
    expect(wrapper.className).toContain('flex');
    expect(wrapper.className).toContain('items-center');
    expect(wrapper.className).toContain('justify-center');
  });

  it('contains an animate-spin spinner inside', () => {
    const { container } = render(<PageLoader />);
    const spinner = container.querySelector('.animate-spin');
    expect(spinner).toBeInTheDocument();
  });

  it('has a minimum height to fill space', () => {
    const { container } = render(<PageLoader />);
    const wrapper = container.firstChild as HTMLElement;
    // min-h-[400px] keeps the loader visually centred on the page
    expect(wrapper.className).toContain('min-h-');
  });
});
