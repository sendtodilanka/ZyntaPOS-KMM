import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';
import { LicenseDetailCard } from '@/components/licenses/LicenseDetailCard';
import { LicenseExtendDialog } from '@/components/licenses/LicenseExtendDialog';
import { PageLoader } from '@/components/shared/LoadingState';
import { ErrorBanner } from '@/components/shared/ErrorBanner';
import { useLicense } from '@/api/licenses';

export const Route = createFileRoute('/licenses/$licenseKey')({
  component: LicenseDetailPage,
});

function LicenseDetailPage() {
  const { licenseKey } = Route.useParams();
  const navigate = useNavigate();
  // useLicense now returns LicenseWithDevices (license + devices)
  const { data, isLoading, isError, refetch } = useLicense(licenseKey);
  const [extendOpen, setExtendOpen] = useState(false);

  if (isLoading) return <PageLoader />;
  if (isError) return <ErrorBanner message="Failed to load license." onRetry={() => refetch()} />;
  if (!data) return (
    <div className="text-center py-16 text-slate-400">License not found.</div>
  );

  const { license } = data;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate({ to: '/licenses' })}
          className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated transition-colors min-w-[44px] min-h-[44px] flex items-center justify-center"
          aria-label="Back to licenses"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <h1 className="panel-title">License Detail</h1>
      </div>
      <LicenseDetailCard license={license} onExtend={() => setExtendOpen(true)} />
      <LicenseExtendDialog license={extendOpen ? license : null} onClose={() => setExtendOpen(false)} />
    </div>
  );
}
