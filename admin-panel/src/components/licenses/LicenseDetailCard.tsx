import { Key, Monitor, Calendar, Clock } from 'lucide-react';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { DeviceList } from './DeviceList';
import { formatDateTime, formatRelativeTime, maskLicenseKey } from '@/lib/utils';
import { useLicenseDevices } from '@/api/licenses';
import type { License } from '@/types/license';

interface LicenseDetailCardProps {
  license: License;
  onExtend: () => void;
}

export function LicenseDetailCard({ license, onExtend }: LicenseDetailCardProps) {
  const { data: devices = [], isLoading: devicesLoading } = useLicenseDevices(license.key);

  return (
    <div className="space-y-6">
      {/* Overview */}
      <div className="panel-card">
        <div className="flex flex-wrap items-start justify-between gap-4 mb-6">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Key className="w-4 h-4 text-brand-400" />
              <span className="font-mono text-sm text-brand-400">{maskLicenseKey(license.key)}</span>
            </div>
            <h2 className="text-xl font-bold text-slate-100">{license.customerName}</h2>
            <div className="flex flex-wrap items-center gap-2 mt-2">
              <StatusBadge status={license.status} />
              <span className="text-xs text-slate-400 uppercase font-medium">{license.edition}</span>
            </div>
          </div>
          <button
            onClick={onExtend}
            className="px-4 py-2 bg-brand-700 hover:bg-brand-800 text-white text-sm font-medium rounded-lg transition-colors min-h-[44px]"
          >
            Extend / Edit
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { icon: Monitor, label: 'Devices', value: `${license.activeDevices} / ${license.maxDevices}` },
            { icon: Calendar, label: 'Activated', value: formatDateTime(license.activatedAt) },
            { icon: Calendar, label: 'Expires', value: license.expiresAt ? formatDateTime(license.expiresAt) : 'Never' },
            { icon: Clock, label: 'Last Heartbeat', value: license.lastHeartbeatAt ? formatRelativeTime(license.lastHeartbeatAt) : '—' },
          ].map(({ icon: Icon, label, value }) => (
            <div key={label} className="bg-surface-elevated rounded-lg p-3">
              <div className="flex items-center gap-2 mb-1">
                <Icon className="w-3.5 h-3.5 text-slate-400" />
                <span className="text-[11px] text-slate-400 uppercase font-medium">{label}</span>
              </div>
              <span className="text-sm font-semibold text-slate-100">{value}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Devices */}
      <div className="panel-card">
        <h3 className="panel-title text-base mb-4">Registered Devices</h3>
        <DeviceList licenseKey={license.key} devices={devices} isLoading={devicesLoading} />
      </div>
    </div>
  );
}
