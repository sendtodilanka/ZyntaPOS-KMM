import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { ArrowLeft } from 'lucide-react';
import { StoreDetailPanel } from '@/components/stores/StoreDetailPanel';
import { StatusBadge } from '@/components/shared/StatusBadge';
import { PageLoader } from '@/components/shared/LoadingState';
import { useStore } from '@/api/stores';

export const Route = createFileRoute('/stores/$storeId')({
  component: StoreDetailPage,
});

function StoreDetailPage() {
  const { storeId } = Route.useParams();
  const navigate = useNavigate();
  const { data: store, isLoading } = useStore(storeId);

  if (isLoading) return <PageLoader />;
  if (!store) return <div className="text-center py-16 text-slate-400">Store not found.</div>;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate({ to: '/stores' })}
          className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated transition-colors min-w-[44px] min-h-[44px] flex items-center justify-center"
          aria-label="Back to stores"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="panel-title truncate">{store.name}</h1>
            <StatusBadge status={store.status} />
          </div>
          <p className="text-sm text-slate-400">{store.location}</p>
        </div>
      </div>
      <StoreDetailPanel store={store} />
    </div>
  );
}
