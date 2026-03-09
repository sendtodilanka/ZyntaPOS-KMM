import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import { DataTable } from '@/components/shared/DataTable';
import type { Column } from '@/components/shared/DataTable';

interface TestRow {
  id: string;
  name: string;
  value: number;
}

const columns: Column<TestRow>[] = [
  { key: 'name', header: 'Name', cell: (row) => row.name },
  { key: 'value', header: 'Value', cell: (row) => String(row.value) },
];

const sampleData: TestRow[] = [
  { id: '1', name: 'Alice', value: 100 },
  { id: '2', name: 'Bob', value: 200 },
];

describe('DataTable', () => {
  it('renders column headers from columns prop', () => {
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
      />,
    );
    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Value')).toBeInTheDocument();
  });

  it('renders data in cells using the cell function', () => {
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
      />,
    );
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('100')).toBeInTheDocument();
    expect(screen.getByText('200')).toBeInTheDocument();
  });

  it('shows loading skeleton when isLoading=true', () => {
    const { container } = render(
      <DataTable
        columns={columns}
        data={[]}
        isLoading={true}
        rowKey={(row) => row.id}
      />,
    );
    // TableSkeleton renders elements with animate-pulse
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    // Data rows should not be present
    expect(screen.queryByText('Alice')).not.toBeInTheDocument();
  });

  it('shows empty state when data is empty and not loading', () => {
    render(
      <DataTable
        columns={columns}
        data={[]}
        isLoading={false}
        emptyTitle="No records found"
        rowKey={(row) => row.id}
      />,
    );
    expect(screen.getByText('No records found')).toBeInTheDocument();
  });

  it('calls onRowClick with the row when a row is clicked', () => {
    const onRowClick = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
        onRowClick={onRowClick}
      />,
    );
    fireEvent.click(screen.getByText('Alice'));
    expect(onRowClick).toHaveBeenCalledOnce();
    expect(onRowClick).toHaveBeenCalledWith(sampleData[0]);
  });

  it('shows pagination controls when totalPages > 1', () => {
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
        page={0}
        totalPages={3}
        total={30}
        onPageChange={vi.fn()}
      />,
    );
    expect(screen.getByLabelText('Next page')).toBeInTheDocument();
    expect(screen.getByLabelText('Previous page')).toBeInTheDocument();
  });

  it('shows total records count in pagination', () => {
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
        page={0}
        totalPages={3}
        total={30}
        onPageChange={vi.fn()}
      />,
    );
    expect(screen.getByText(/30.*total records/i)).toBeInTheDocument();
  });

  it('does not show pagination when totalPages is 1', () => {
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
        page={0}
        totalPages={1}
        total={2}
        onPageChange={vi.fn()}
      />,
    );
    expect(screen.queryByLabelText('Next page')).not.toBeInTheDocument();
  });

  it('calls onPageChange when next page button is clicked', () => {
    const onPageChange = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
        page={0}
        totalPages={3}
        total={30}
        onPageChange={onPageChange}
      />,
    );
    fireEvent.click(screen.getByLabelText('Next page'));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('calls onPageChange when previous page button is clicked', () => {
    const onPageChange = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
        page={1}
        totalPages={3}
        total={30}
        onPageChange={onPageChange}
      />,
    );
    fireEvent.click(screen.getByLabelText('Previous page'));
    expect(onPageChange).toHaveBeenCalledWith(0);
  });

  it('shows "0 total records" when total is 0 but totalPages > 1', () => {
    render(
      <DataTable
        columns={columns}
        data={sampleData}
        rowKey={(row) => row.id}
        page={0}
        totalPages={2}
        total={0}
        onPageChange={vi.fn()}
      />,
    );
    expect(screen.getByText(/0.*total records/i)).toBeInTheDocument();
  });
});
