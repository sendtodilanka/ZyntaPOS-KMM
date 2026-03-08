import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X } from 'lucide-react';
import { RoleAssignment } from './RoleAssignment';
import { useCreateUser, useUpdateUser } from '@/api/users';
import type { AdminUser } from '@/types/user';

const schema = z.object({
  email: z.string().email('Invalid email'),
  name: z.string().min(1, 'Name is required'),
  password: z.string().min(8, 'Min 8 characters').optional().or(z.literal('')),
  role: z.enum(['ADMIN', 'OPERATOR', 'FINANCE', 'AUDITOR', 'HELPDESK'] as const),
});
type FormData = z.infer<typeof schema>;

interface UserCreateFormProps {
  open: boolean;
  onClose: () => void;
  editUser?: AdminUser;
}

export function UserCreateForm({ open, onClose, editUser }: UserCreateFormProps) {
  const createUser = useCreateUser();
  const updateUser = useUpdateUser();
  const isEdit = !!editUser;

  const { register, handleSubmit, watch, setValue, reset, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: editUser
      ? { email: editUser.email, name: editUser.name, role: editUser.role }
      : { role: 'OPERATOR' },
  });

  const role = watch('role');

  const onSubmit = (data: FormData) => {
    if (isEdit) {
      updateUser.mutate(
        { userId: editUser.id, data: { role: data.role } },
        { onSuccess: () => { reset(); onClose(); } },
      );
    } else {
      createUser.mutate(
        { email: data.email, name: data.name, role: data.role, password: data.password ?? '' },
        { onSuccess: () => { reset(); onClose(); } },
      );
    }
  };

  if (!open) return null;

  const isPending = createUser.isPending || updateUser.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-surface-card border border-surface-border rounded-xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto animate-fade-in">
        <div className="flex items-center justify-between p-6 border-b border-surface-border sticky top-0 bg-surface-card z-10">
          <h3 className="text-lg font-semibold text-slate-100">{isEdit ? 'Edit User' : 'Create User'}</h3>
          <button onClick={onClose} className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-surface-elevated min-w-[44px] min-h-[44px] flex items-center justify-center">
            <X className="w-4 h-4" />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-4">
          {!isEdit && (
            <>
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">Email</label>
                <input {...register('email')} type="email" className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500" />
                {errors.email && <p className="text-xs text-red-400 mt-1">{errors.email.message}</p>}
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">Full Name</label>
                <input {...register('name')} className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500" />
                {errors.name && <p className="text-xs text-red-400 mt-1">{errors.name.message}</p>}
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1.5">Password</label>
                <input {...register('password')} type="password" className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500" />
                {errors.password && <p className="text-xs text-red-400 mt-1">{errors.password.message}</p>}
              </div>
            </>
          )}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">Role</label>
            <RoleAssignment value={role} onChange={(r) => setValue('role', r)} />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium text-slate-300 border border-surface-border hover:bg-surface-elevated transition-colors min-h-[44px]">Cancel</button>
            <button type="submit" disabled={isPending} className="flex-1 px-4 py-2.5 rounded-lg text-sm font-medium bg-brand-500 text-white hover:bg-brand-600 disabled:opacity-50 transition-colors min-h-[44px]">
              {isPending ? 'Saving…' : isEdit ? 'Save Changes' : 'Create User'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
