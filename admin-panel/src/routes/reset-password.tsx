import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Eye, EyeOff, Loader2, CheckCircle2 } from 'lucide-react';
import { useState } from 'react';
import { useResetPassword } from '@/api/auth';
import { HTTPError } from '@/lib/api-client';

export const Route = createFileRoute('/reset-password')({
  component: ResetPasswordPage,
  validateSearch: (search: Record<string, unknown>): { token?: string } => ({
    token: typeof search.token === 'string' ? search.token : undefined,
  }),
});

const schema = z
  .object({
    newPassword: z.string().min(8, 'Password must be at least 8 characters').max(128, 'Password too long'),
    confirmPassword: z.string().min(1, 'Confirm your new password'),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type ResetForm = z.infer<typeof schema>;

function ResetPasswordPage() {
  const navigate = useNavigate();
  const { token } = Route.useSearch();
  const resetPassword = useResetPassword();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetForm>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: ResetForm) => {
    setServerError(null);
    if (!token) {
      setServerError('Reset link is missing the token.');
      return;
    }
    try {
      await resetPassword.mutateAsync({ token, newPassword: data.newPassword });
      setDone(true);
    } catch (err) {
      if (err instanceof HTTPError && err.response.status === 422) {
        setServerError('This reset link has expired or already been used. Request a new one.');
      } else {
        setServerError('Something went wrong. Please try again.');
      }
    }
  };

  // Missing token — treat as invalid link
  if (!token) {
    return (
      <main className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <div className="w-full max-w-md space-y-8 text-center">
          <h1 className="text-2xl font-bold text-gray-900">Invalid reset link</h1>
          <p className="text-sm text-gray-600">
            The link you opened is missing the reset token. Request a new reset email.
          </p>
          <Link
            to="/forgot-password"
            className="inline-block text-sm font-medium text-blue-600 hover:text-blue-700"
          >
            Request a new link
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-600 mb-4">
            <span className="text-white text-2xl font-bold">Z</span>
          </div>
          <h1 className="text-2xl font-bold text-gray-900">
            {done ? 'Password reset' : 'Choose a new password'}
          </h1>
          <p className="mt-0.5 text-sm font-medium text-gray-500">ZyntaPOS Admin</p>
          <p className="mt-1 text-sm text-gray-500">
            {done ? 'Your password has been updated.' : 'Pick something you have not used before.'}
          </p>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8">
          {done ? (
            <div className="space-y-5 text-center">
              <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-green-50">
                <CheckCircle2 size={24} className="text-green-600" />
              </div>
              <p className="text-sm text-gray-700">
                You can now sign in with your new password. All other active sessions have been signed out.
              </p>
              <button
                type="button"
                onClick={() => navigate({ to: '/login' })}
                className={[
                  'w-full rounded-lg px-4 py-2.5 text-sm font-semibold text-white bg-blue-600',
                  'hover:bg-blue-700 active:bg-blue-800',
                  'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
                  'transition-colors',
                ].join(' ')}
              >
                Continue to sign in
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
              {serverError && (
                <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                  {serverError}
                </div>
              )}

              <div>
                <label htmlFor="newPassword" className="block text-sm font-medium text-gray-700 mb-1.5">
                  New password
                </label>
                <div className="relative">
                  <input
                    {...register('newPassword')}
                    id="newPassword"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="At least 8 characters"
                    className={[
                      'w-full rounded-lg border px-3.5 py-2.5 pr-10 text-sm text-gray-900 placeholder-gray-400',
                      'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent',
                      'transition-colors',
                      errors.newPassword ? 'border-red-400 bg-red-50' : 'border-gray-300 bg-white',
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
                {errors.newPassword && (
                  <p className="mt-1 text-xs text-red-600">{errors.newPassword.message}</p>
                )}
              </div>

              <div>
                <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700 mb-1.5">
                  Confirm new password
                </label>
                <input
                  {...register('confirmPassword')}
                  id="confirmPassword"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  placeholder="Re-enter new password"
                  className={[
                    'w-full rounded-lg border px-3.5 py-2.5 text-sm text-gray-900 placeholder-gray-400',
                    'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent',
                    'transition-colors',
                    errors.confirmPassword ? 'border-red-400 bg-red-50' : 'border-gray-300 bg-white',
                  ].join(' ')}
                />
                {errors.confirmPassword && (
                  <p className="mt-1 text-xs text-red-600">{errors.confirmPassword.message}</p>
                )}
              </div>

              <button
                type="submit"
                disabled={isSubmitting || resetPassword.isPending}
                className={[
                  'w-full flex items-center justify-center gap-2 rounded-lg px-4 py-2.5',
                  'text-sm font-semibold text-white bg-blue-600',
                  'hover:bg-blue-700 active:bg-blue-800',
                  'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
                  'disabled:opacity-60 disabled:cursor-not-allowed',
                  'transition-colors',
                ].join(' ')}
              >
                {(isSubmitting || resetPassword.isPending) && <Loader2 size={16} className="animate-spin" />}
                Reset password
              </button>

              <Link
                to="/login"
                className="block text-center text-sm text-gray-500 hover:text-gray-700"
              >
                Back to sign in
              </Link>
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
