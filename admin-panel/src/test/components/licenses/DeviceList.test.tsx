import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '../../utils';
import { DeviceList } from '@/components/licenses/DeviceList';
import type { LicenseDevice } from '@/types/license';

const mockDeregisterMutate = vi.fn();

vi.mock('@/api/licenses', () => ({
  useDeregisterDevice: () => ({ mutate: mockDeregisterMutate, isPending: false }),
}));

const mockDevice: LicenseDevice = {
  id: 'dev-1',
  licenseKey: 'ZYNTA-ABCD-1234-EFGH',
  deviceId: 'device-abc',
  deviceName: 'Android Tablet',
  appVersion: '1.0.0',
  os: 'Android',
  osVersion: '14',
  firstSeenAt: new Date().toISOString(),
  lastSeenAt: new Date().toISOString(),
};

describe('DeviceList', () => {
  beforeEach(() => {
    mockDeregisterMutate.mockClear();
  });

  it('shows device name', () => {
    render(
      <DeviceList
        licenseKey="ZYNTA-ABCD-1234-EFGH"
        devices={[mockDevice]}
        isLoading={false}
      />,
    );
    expect(screen.getByText('Android Tablet')).toBeInTheDocument();
  });

  it('shows OS info', () => {
    render(
      <DeviceList
        licenseKey="ZYNTA-ABCD-1234-EFGH"
        devices={[mockDevice]}
        isLoading={false}
      />,
    );
    expect(screen.getByText('Android 14')).toBeInTheDocument();
  });

  it('shows app version', () => {
    render(
      <DeviceList
        licenseKey="ZYNTA-ABCD-1234-EFGH"
        devices={[mockDevice]}
        isLoading={false}
      />,
    );
    expect(screen.getByText('1.0.0')).toBeInTheDocument();
  });

  it('shows last seen date', () => {
    render(
      <DeviceList
        licenseKey="ZYNTA-ABCD-1234-EFGH"
        devices={[mockDevice]}
        isLoading={false}
      />,
    );
    // formatRelativeTime would render something like "just now" or similar
    expect(screen.getByText('Last Seen')).toBeInTheDocument();
  });

  it('has a delete button per device', () => {
    render(
      <DeviceList
        licenseKey="ZYNTA-ABCD-1234-EFGH"
        devices={[mockDevice]}
        isLoading={false}
      />,
    );
    expect(screen.getByLabelText('Deregister device')).toBeInTheDocument();
  });

  it('clicking deregister button shows confirmation dialog', () => {
    render(
      <DeviceList
        licenseKey="ZYNTA-ABCD-1234-EFGH"
        devices={[mockDevice]}
        isLoading={false}
      />,
    );
    fireEvent.click(screen.getByLabelText('Deregister device'));
    expect(screen.getByText('Deregister Device')).toBeInTheDocument();
  });

  it('shows empty state when devices array is empty', () => {
    render(
      <DeviceList
        licenseKey="ZYNTA-ABCD-1234-EFGH"
        devices={[]}
        isLoading={false}
      />,
    );
    expect(screen.getByText('No registered devices.')).toBeInTheDocument();
  });
});
