import { createFileRoute, Link } from '@tanstack/react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2, MailCheck } from 'lucide-react';
import { useState } from 'react';
import { useForgotPassword } from '@/api/auth';

export const Route = createFileRoute('/forgot-password')({
  component: ForgotPasswordPage,
});

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
});

type ForgotForm = z.infer<typeof schema>;

function ForgotPasswordPage() {
  const forgot = useForgotPassword();
  const [submitted, setSubmitted] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotForm>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: ForgotForm) => {
    try {
      await forgot.mutateAsync(data);
      // Backend always responds 202 regardless of whether the email exists, to
      // prevent enumeration. Mirror that UX on the client — always show success.
      setSubmitted(true);
    } catch {
      // Hook-level onError shows a toast on network/500 errors; still flip to
      // the "we sent an email" state to match the backend's non-enumeration
      // behaviour on 5xx (the user cannot tell whether it worked anyway).
      setSubmitted(true);
    }
  };

  return (
    <main className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-600 mb-4">
            <span className="text-white text-2xl font-bold">Z</span>
          </div>
          <h1 className="text-2xl font-bold text-gray-900">
            {submitted ? 'Check your email' : 'Reset your password'}
          </h1>
          <p className="mt-0.5 text-sm font-medium text-gray-500">ZyntaPOS Admin</p>
          <p className="mt-1 text-sm text-gray-500">
            {submitted
              ? 'We sent a reset link to that address if an account exists.'
              : 'Enter your email and we will send a reset link.'}
          </p>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8">
          {submitted ? (
            <div className="space-y-5 text-center">
              <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-blue-50">
                <MailCheck size={24} className="text-blue-600" />
              </div>
              <p className="text-sm text-gray-700">
                If an administrator account exists for the email you provided, a
                reset link will arrive in your inbox within a few minutes. The
                link expires after 1 hour.
              </p>
              <Link
                to="/login"
                className="inline-block text-sm font-medium text-blue-600 hover:text-blue-700"
              >
                Back to sign in
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
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

              <button
                type="submit"
                disabled={isSubmitting || forgot.isPending}
                className={[
                  'w-full flex items-center justify-center gap-2 rounded-lg px-4 py-2.5',
                  'text-sm font-semibold text-white bg-blue-600',
                  'hover:bg-blue-700 active:bg-blue-800',
                  'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
                  'disabled:opacity-60 disabled:cursor-not-allowed',
                  'transition-colors',
                ].join(' ')}
              >
                {(isSubmitting || forgot.isPending) && <Loader2 size={16} className="animate-spin" />}
                Send reset link
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
