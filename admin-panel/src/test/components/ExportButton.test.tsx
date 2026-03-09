import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '../utils';
import { ExportButton } from '@/components/shared/ExportButton';

describe('ExportButton', () => {
  it('renders an export button', () => {
    render(<ExportButton onExportCsv={vi.fn()} />);
    // The trigger button contains a Download icon and optional "Export" text
    const button = screen.getByRole('button');
    expect(button).toBeInTheDocument();
  });

  it('button is disabled when isLoading=true', () => {
    render(<ExportButton onExportCsv={vi.fn()} isLoading={true} />);
    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
  });

  it('button is enabled when isLoading=false', () => {
    render(<ExportButton onExportCsv={vi.fn()} isLoading={false} />);
    const button = screen.getByRole('button');
    expect(button).not.toBeDisabled();
  });

  it('clicking the button opens the dropdown menu', () => {
    render(<ExportButton onExportCsv={vi.fn()} />);
    // Dropdown not yet visible
    expect(screen.queryByText('Export CSV')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Export CSV')).toBeInTheDocument();
  });

  it('clicking CSV option calls onExportCsv', () => {
    const onExportCsv = vi.fn();
    render(<ExportButton onExportCsv={onExportCsv} />);

    // Open the dropdown
    fireEvent.click(screen.getByRole('button'));
    // Click CSV option
    fireEvent.click(screen.getByText('Export CSV'));

    expect(onExportCsv).toHaveBeenCalledOnce();
  });

  it('clicking CSV option closes the dropdown', () => {
    render(<ExportButton onExportCsv={vi.fn()} />);

    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Export CSV')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Export CSV'));
    expect(screen.queryByText('Export CSV')).not.toBeInTheDocument();
  });

  it('does not render PDF option when onExportPdf is not provided', () => {
    render(<ExportButton onExportCsv={vi.fn()} />);

    fireEvent.click(screen.getByRole('button'));
    expect(screen.queryByText('Export PDF')).not.toBeInTheDocument();
  });

  it('renders PDF option when onExportPdf is provided', () => {
    render(<ExportButton onExportCsv={vi.fn()} onExportPdf={vi.fn()} />);

    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Export PDF')).toBeInTheDocument();
  });

  it('clicking PDF option calls onExportPdf', () => {
    const onExportPdf = vi.fn();
    render(<ExportButton onExportCsv={vi.fn()} onExportPdf={onExportPdf} />);

    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Export PDF'));

    expect(onExportPdf).toHaveBeenCalledOnce();
  });

  it('clicking PDF option closes the dropdown', () => {
    render(<ExportButton onExportCsv={vi.fn()} onExportPdf={vi.fn()} />);

    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Export PDF')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Export PDF'));
    expect(screen.queryByText('Export PDF')).not.toBeInTheDocument();
  });

  it('closes the dropdown when clicking outside', () => {
    render(
      <div>
        <ExportButton onExportCsv={vi.fn()} />
        <p>Outside element</p>
      </div>,
    );

    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Export CSV')).toBeInTheDocument();

    fireEvent.mouseDown(screen.getByText('Outside element'));
    expect(screen.queryByText('Export CSV')).not.toBeInTheDocument();
  });
});
