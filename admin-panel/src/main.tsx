import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import * as Sentry from '@sentry/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { RouterProvider, createRouter } from '@tanstack/react-router';
import { routeTree } from './routeTree.gen';
import { queryClient } from './lib/query-client';
import './globals.css';

// Sentry error tracking — DSN injected via VITE_SENTRY_DSN env var.
// No-ops gracefully when DSN is empty (local dev).
// Analytics tracking is handled via @sentry/react (ADR-012: Firebase removed).
Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN || '',
  environment: import.meta.env.MODE,
  release: `zyntapos-admin@${import.meta.env.VITE_APP_VERSION || '1.0.0'}`,
  tracesSampleRate: import.meta.env.PROD ? 0.1 : 0,
  enabled: !!import.meta.env.VITE_SENTRY_DSN,
});

const router = createRouter({
  routeTree,
  context: {
    queryClient,
  },
  defaultPreload: 'intent',
  defaultPreloadStaleTime: 0,
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}

async function enableMocking() {
  if (import.meta.env.VITE_MOCK !== 'true') return;
  const { worker } = await import('./test/mocks/browser');
  return worker.start({ onUnhandledRequest: 'bypass' });
}

const rootElement = document.getElementById('root')!;

enableMocking().then(() => {
  createRoot(rootElement).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <ReactQueryDevtools initialIsOpen={false} />
      </QueryClientProvider>
    </StrictMode>,
  );
});
