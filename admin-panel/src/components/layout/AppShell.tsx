import { useEffect } from 'react';
import { Outlet, useRouter } from '@tanstack/react-router';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { MobileBottomNav } from './MobileBottomNav';
import { ToastContainer } from '../shared/ToastContainer';
import { useUiStore } from '@/stores/ui-store';
import { useIsMobile } from '@/hooks/use-media-query';
import { cn } from '@/lib/utils';

export function AppShell() {
  const { mobileSidebarOpen, setMobileSidebarOpen } = useUiStore();
  const isMobile = useIsMobile();
  const router = useRouter();

  // Close mobile sidebar on route change
  useEffect(() => {
    return router.subscribe('onLoad', () => {
      setMobileSidebarOpen(false);
    });
  }, [router, setMobileSidebarOpen]);

  return (
    <div className="flex h-screen bg-surface overflow-hidden">
      {/* Desktop/Tablet Sidebar */}
      {!isMobile && (
        <Sidebar />
      )}

      {/* Mobile Sidebar Overlay */}
      {isMobile && mobileSidebarOpen && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
            onClick={() => setMobileSidebarOpen(false)}
          />
          {/* Slide-in sidebar */}
          <div className="fixed inset-y-0 left-0 z-50 w-72 animate-slide-in">
            <Sidebar mobile />
          </div>
        </>
      )}

      {/* Main content area */}
      <div
        className={cn(
          'flex flex-1 flex-col min-w-0 overflow-hidden transition-all duration-200',
        )}
      >
        <Header />

        <main className="flex-1 overflow-y-auto overflow-x-hidden">
          <div className="px-4 py-4 sm:px-6 lg:px-8 pb-24 md:pb-6">
            <Outlet />
          </div>
        </main>

        {/* Mobile bottom nav */}
        {isMobile && <MobileBottomNav />}
      </div>

      <ToastContainer />
    </div>
  );
}
