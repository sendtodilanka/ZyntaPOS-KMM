import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Eye, EyeOff, Loader2, ShieldCheck } from 'lucide-react';
import { useState, useEffect } from 'react';
import { useAdminLogin, useAdminMfaVerify } from '@/api/auth';
import { useAuth } from '@/hooks/use-auth';
import { HTTPError } from '@/lib/api-client';

export const Route = createFileRoute('/login')({
  component: LoginPage,
});

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginForm = z.infer<typeof schema>;

const mfaSchema = z.object({
  code: z.string().min(6, 'Enter a 6-digit code').max(8),
});
type MfaForm = z.infer<typeof mfaSchema>;

function LoginPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const login = useAdminLogin();
  const mfaVerify = useAdminMfaVerify();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [pendingToken, setPendingToken] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(schema) });

  const {
    register: registerMfa,
    handleSubmit: handleMfaSubmit,
    formState: { errors: mfaErrors, isSubmitting: mfaSubmitting },
  } = useForm<MfaForm>({ resolver: zodResolver(mfaSchema) });

  // Already authenticated — go to dashboard (must be in useEffect, not render body)
  useEffect(() => {
    if (isAuthenticated) navigate({ to: '/' });
  }, [isAuthenticated, navigate]);

  // S1-3: No manual navigate() on success — __root.tsx guard handles redirect
  // when isAuthenticated becomes true after setUser() in the login mutation.
  const onSubmit = async (data: LoginForm) => {
    setServerError(null);
    try {
      const response = await login.mutateAsync(data);
      if ('pendingToken' in response) {
        setPendingToken(response.pendingToken);
      }
      // Navigation handled by __root.tsx useEffect when isAuthenticated changes
    } catch (err) {
      if (err instanceof HTTPError) {
        if (err.response.status === 401) {
          setServerError('Invalid email or password.');
        } else if (err.response.status === 429) {
          setServerError('Account temporarily locked. Try again later.');
        } else if (err.response.status === 403) {
          setServerError('This account has been deactivated.');
        } else {
          setServerError('Something went wrong. Please try again.');
        }
      } else {
        setServerError('Network error. Check your connection.');
      }
    }
  };

  const onMfaSubmit = async (data: MfaForm) => {
    setServerError(null);
    try {
      await mfaVerify.mutateAsync({ code: data.code, pendingToken: pendingToken! });
      // S1-3: Navigation handled by __root.tsx guard
    } catch (err) {
      if (err instanceof HTTPError && err.response.status === 422) {
        setServerError('Invalid code. Check your authenticator app and try again.');
      } else if (err instanceof HTTPError && err.response.status === 401) {
        setServerError('Session expired. Please sign in again.');
        setPendingToken(null);
      } else {
        setServerError('Something went wrong. Please try again.');
      }
    }
  };

  return (
    <main className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md space-y-8">
        {/* Logo + Title */}
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-600 mb-4">
            <span className="text-white text-2xl font-bold">Z</span>
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Sign in</h1>
          <p className="mt-0.5 text-sm font-medium text-gray-500">ZyntaPOS Admin</p>
          <p className="mt-1 text-sm text-gray-500">
            {pendingToken ? 'Two-factor authentication' : 'Sign in to the management console'}
          </p>
        </div>

        {/* Form Card */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8">
          {pendingToken ? (
            /* ── MFA Step ── */
            <form onSubmit={handleMfaSubmit(onMfaSubmit)} noValidate className="space-y-5">
              <div className="flex items-center gap-3 rounded-lg bg-blue-50 border border-blue-200 px-4 py-3">
                <ShieldCheck size={20} className="text-blue-600 shrink-0" />
                <p className="text-sm text-blue-800">
                  Enter the 6-digit code from your authenticator app, or a backup code.
                </p>
              </div>

              {serverError && (
                <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                  {serverError}
                </div>
              )}

              <div>
                <label htmlFor="code" className="block text-sm font-medium text-gray-700 mb-1.5">
                  Authentication code
                </label>
                <input
                  {...registerMfa('code')}
                  id="code"
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  placeholder="000000"
                  maxLength={8}
                  className={[
                    'w-full rounded-lg border px-3.5 py-2.5 text-sm text-gray-900 placeholder-gray-400 text-center tracking-widest',
                    'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent',
                    'transition-colors',
                    mfaErrors.code ? 'border-red-400 bg-red-50' : 'border-gray-300 bg-white',
                  ].join(' ')}
                />
                {mfaErrors.code && (
                  <p className="mt-1 text-xs text-red-600">{mfaErrors.code.message}</p>
                )}
              </div>

              <button
                type="submit"
                disabled={mfaSubmitting || mfaVerify.isPending}
                className={[
                  'w-full flex items-center justify-center gap-2 rounded-lg px-4 py-2.5',
                  'text-sm font-semibold text-white bg-blue-600',
                  'hover:bg-blue-700 active:bg-blue-800',
                  'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
                  'disabled:opacity-60 disabled:cursor-not-allowed',
                  'transition-colors',
                ].join(' ')}
              >
                {(mfaSubmitting || mfaVerify.isPending) && <Loader2 size={16} className="animate-spin" />}
                Verify
              </button>

              <button
                type="button"
                onClick={() => { setPendingToken(null); setServerError(null); }}
                className="w-full text-sm text-gray-500 hover:text-gray-700 text-center"
              >
                Back to sign in
              </button>
            </form>
          ) : (
            /* ── Password Step ── */
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
              {serverError && (
                <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                  {serverError}
                </div>
              )}

              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1.5">
                  Email address
                </label>
                <input
                  {...register('email')}
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@zyntapos.com"
                  className={[
                    'w-full rounded-lg border px-3.5 py-2.5 text-sm text-gray-900 placeholder-gray-400',
                    'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent',
                    'transition-colors',
                    errors.email ? 'border-red-400 bg-red-50' : 'border-gray-300 bg-white',
                  ].join(' ')}
                />
                {errors.email && (
                  <p className="mt-1 text-xs text-red-600">{errors.email.message}</p>
                )}
              </div>

              <div>
                <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1.5">
                  Password
                </label>
                <div className="relative">
                  <input
                    {...register('password')}
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    placeholder="Enter your password"
                    className={[
                      'w-full rounded-lg border px-3.5 py-2.5 pr-10 text-sm text-gray-900 placeholder-gray-400',
                      'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent',
                      'transition-colors',
                      errors.password ? 'border-red-400 bg-red-50' : 'border-gray-300 bg-white',
                    ].join(' ')}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute inset-y-0 right-0 flex items-center pr-3 text-gray-400 hover:text-gray-600"
                    tabIndex={-1}
                  >
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
                {errors.password && (
                  <p className="mt-1 text-xs text-red-600">{errors.password.message}</p>
                )}
              </div>

              <button
                type="submit"
                disabled={isSubmitting || login.isPending}
                className={[
                  'w-full flex items-center justify-center gap-2 rounded-lg px-4 py-2.5',
                  'text-sm font-semibold text-white bg-blue-600',
                  'hover:bg-blue-700 active:bg-blue-800',
                  'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
                  'disabled:opacity-60 disabled:cursor-not-allowed',
                  'transition-colors',
                ].join(' ')}
              >
                {(isSubmitting || login.isPending) && <Loader2 size={16} className="animate-spin" />}
                Sign in
              </button>

            </form>
          )}
        </div>

        <p className="text-center text-xs text-gray-500">
          Zynta Solutions Pvt Ltd &mdash; Internal use only
        </p>
      </div>
    </main>
  );
}
