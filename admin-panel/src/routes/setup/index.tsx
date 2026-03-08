import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Eye, EyeOff, Loader2, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { useAdminBootstrap } from '@/api/auth';
import { HTTPError } from '@/lib/api-client';

export const Route = createFileRoute('/setup/')({
  component: SetupPage,
});

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  name: z.string().min(1, 'Name is required'),
  password: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .max(256, 'Password is too long'),
});

type SetupForm = z.infer<typeof schema>;

function SetupPage() {
  const navigate = useNavigate();
  const bootstrap = useAdminBootstrap();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SetupForm>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: SetupForm) => {
    setServerError(null);
    try {
      await bootstrap.mutateAsync(data);
      navigate({ to: '/login' });
    } catch (err) {
      if (err instanceof HTTPError) {
        if (err.response.status === 409) {
          setServerError('An admin account already exists. Please sign in.');
        } else if (err.response.status === 422) {
          setServerError('Please check your inputs and try again.');
        } else {
          setServerError('Something went wrong. Please try again.');
        }
      } else {
        setServerError('Network error. Check your connection.');
      }
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md space-y-8">
        {/* Logo + Title */}
        <div className="text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-blue-600 mb-4">
            <span className="text-white text-2xl font-bold">Z</span>
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Welcome to ZyntaPOS</h1>
          <p className="mt-1 text-sm text-gray-500">Create your administrator account to get started</p>
        </div>

        {/* Info banner */}
        <div className="flex items-start gap-3 rounded-lg bg-blue-50 border border-blue-200 px-4 py-3">
          <ShieldCheck size={18} className="text-blue-600 mt-0.5 shrink-0" />
          <p className="text-sm text-blue-700">
            This is a one-time setup. Only one admin account can be created this way.
            Additional accounts can be added from the Users section after signing in.
          </p>
        </div>

        {/* Form Card */}
        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8">
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">

            {/* Server error */}
            {serverError && (
              <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                {serverError}
              </div>
            )}

            {/* Name */}
            <div>
              <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-1.5">
                Full name
              </label>
              <input
                {...register('name')}
                id="name"
                type="text"
                autoComplete="name"
                placeholder="Your name"
                className={[
                  'w-full rounded-lg border px-3.5 py-2.5 text-sm text-gray-900 placeholder-gray-400',
                  'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent',
                  'transition-colors',
                  errors.name ? 'border-red-400 bg-red-50' : 'border-gray-300 bg-white',
                ].join(' ')}
              />
              {errors.name && (
                <p className="mt-1 text-xs text-red-600">{errors.name.message}</p>
              )}
            </div>

            {/* Email */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1.5">
                Email address
              </label>
              <input
                {...register('email')}
                id="email"
                type="email"
                autoComplete="email"
                placeholder="admin@yourcompany.com"
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

            {/* Password */}
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1.5">
                Password
              </label>
              <div className="relative">
                <input
                  {...register('password')}
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  placeholder="At least 8 characters"
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

            {/* Submit */}
            <button
              type="submit"
              disabled={isSubmitting || bootstrap.isPending}
              className={[
                'w-full flex items-center justify-center gap-2 rounded-lg px-4 py-2.5',
                'text-sm font-semibold text-white bg-blue-600',
                'hover:bg-blue-700 active:bg-blue-800',
                'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
                'disabled:opacity-60 disabled:cursor-not-allowed',
                'transition-colors',
              ].join(' ')}
            >
              {(isSubmitting || bootstrap.isPending) && <Loader2 size={16} className="animate-spin" />}
              Create admin account
            </button>
          </form>
        </div>

        <p className="text-center text-xs text-gray-400">
          Zynta Solutions Pvt Ltd &mdash; Internal use only
        </p>
      </div>
    </div>
  );
}
