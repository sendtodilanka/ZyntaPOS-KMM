import { Trash2 } from 'lucide-react';
import { useState } from 'react';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { formatRelativeTime } from '@/lib/utils';
import { useDeregisterDevice } from '@/api/licenses';
import type { LicenseDevice } from '@/types/license';

interface DeviceListProps {
  licenseKey: string;
  devices: LicenseDevice[];
  isLoading: boolean;
}

export function DeviceList({ licenseKey, devices, isLoading }: DeviceListProps) {
  const deregister = useDeregisterDevice();
  const [target, setTarget] = useState<LicenseDevice | null>(null);

  if (isLoading) return <div className="text-sm text-slate-400 py-4">Loading devices…</div>;
  if (devices.length === 0) return <div className="text-sm text-slate-500 py-4">No registered devices.</div>;

  return (
    <>
      <div className="overflow-x-auto rounded-lg border border-surface-border" tabIndex={0}>
        <table className="w-full text-sm min-w-[500px]">
          <thead>
            <tr className="border-b border-surface-border bg-surface-elevated">
              {['Device', 'OS', 'App Version', 'Last Seen', ''].map((h) => (
                <th key={h} className="px-4 py-2.5 text-left text-[11px] font-semibold uppercase tracking-wide text-slate-400">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-surface-border">
            {devices.map((d) => (
              <tr key={d.id} className="hover:bg-surface-elevated/50">
                <td className="px-4 py-3">
                  <p className="text-slate-200 font-medium text-xs">{d.deviceName}</p>
                  <p className="text-slate-500 text-[10px] font-mono">{d.deviceId.slice(0, 12)}…</p>
                </td>
                <td className="px-4 py-3 text-slate-400 text-xs">{d.os} {d.osVersion}</td>
                <td className="px-4 py-3 text-slate-400 text-xs">{d.appVersion}</td>
                <td className="px-4 py-3 text-slate-400 text-xs">{formatRelativeTime(d.lastSeenAt)}</td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => setTarget(d)}
                    className="p-1.5 rounded-md text-slate-400 hover:text-red-400 hover:bg-red-400/10 transition-colors min-w-[36px] min-h-[36px] flex items-center justify-center"
                    aria-label="Deregister device"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ConfirmDialog
        open={!!target}
        onClose={() => setTarget(null)}
        onConfirm={() => {
          if (target) deregister.mutate({ key: licenseKey, deviceId: target.id }, { onSettled: () => setTarget(null) });
        }}
        title="Deregister Device"
        description={`Remove "${target?.deviceName}" from this license? The device will lose access immediately.`}
        confirmLabel="Deregister"
        variant="destructive"
        isLoading={deregister.isPending}
      />
    </>
  );
}
