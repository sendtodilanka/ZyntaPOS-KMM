import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { ShieldCheck, ShieldOff, QrCode, Copy, Check, AlertTriangle } from 'lucide-react';
import { useAdminMfaSetup, useAdminMfaEnable, useAdminMfaDisable } from '@/api/auth';
import { useCurrentUser } from '@/api/auth';
import { toast } from '@/stores/ui-store';

export const Route = createFileRoute('/settings/mfa')({
  component: MfaSettingsPage,
});

function MfaSettingsPage() {
  const { data: user, refetch } = useCurrentUser();
  const setupMfa = useAdminMfaSetup();
  const enableMfa = useAdminMfaEnable();
  const disableMfa = useAdminMfaDisable();

  const [step, setStep] = useState<'idle' | 'setup' | 'disable'>('idle');
  const [setupData, setSetupData] = useState<{ secret: string; qrCodeUrl: string; backupCodes: string[] } | null>(null);
  const [verifyCode, setVerifyCode] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [copiedSecret, setCopiedSecret] = useState(false);
  const [copiedCodes, setCopiedCodes] = useState(false);

  const isMfaEnabled = user?.mfaEnabled ?? false;

  const handleStartSetup = async () => {
    try {
      const data = await setupMfa.mutateAsync();
      setSetupData(data);
      setStep('setup');
    } catch {
      toast.error('Failed to generate MFA setup', 'Please try again.');
    }
  };

  const handleEnable = async () => {
    if (!setupData) return;
    try {
      await enableMfa.mutateAsync({ secret: setupData.secret, code: verifyCode });
      setStep('idle');
      setSetupData(null);
      setVerifyCode('');
      await refetch();
    } catch {
      // error toast handled by hook
    }
  };

  const handleDisable = async () => {
    try {
      await disableMfa.mutateAsync(disableCode);
      setStep('idle');
      setDisableCode('');
      await refetch();
    } catch {
      // error toast handled by hook
    }
  };

  const copyToClipboard = async (text: string, type: 'secret' | 'codes') => {
    await navigator.clipboard.writeText(text);
    if (type === 'secret') {
      setCopiedSecret(true);
      setTimeout(() => setCopiedSecret(false), 2000);
    } else {
      setCopiedCodes(true);
      setTimeout(() => setCopiedCodes(false), 2000);
    }
  };

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="panel-title">Two-Factor Authentication</h1>
        <p className="panel-subtitle">
          Protect your account with a time-based one-time password (TOTP) authenticator.
        </p>
      </div>

      {/* Status card */}
      <div className="bg-surface-card border border-surface-border rounded-xl p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            {isMfaEnabled ? (
              <div className="flex items-center justify-center w-10 h-10 rounded-full bg-green-500/10 border border-green-500/30">
                <ShieldCheck className="w-5 h-5 text-green-400" />
              </div>
            ) : (
              <div className="flex items-center justify-center w-10 h-10 rounded-full bg-amber-500/10 border border-amber-500/30">
                <ShieldOff className="w-5 h-5 text-amber-400" />
              </div>
            )}
            <div>
              <p className="text-sm font-semibold text-slate-100">
                {isMfaEnabled ? 'MFA is enabled' : 'MFA is not enabled'}
              </p>
              <p className="text-xs text-slate-400 mt-0.5">
                {isMfaEnabled
                  ? 'Your account requires a TOTP code on every login.'
                  : 'Enable MFA to add an extra layer of security to your account.'}
              </p>
            </div>
          </div>

          {step === 'idle' && (
            <button
              onClick={isMfaEnabled ? () => setStep('disable') : handleStartSetup}
              disabled={setupMfa.isPending}
              className={[
                'px-4 py-2 rounded-lg text-sm font-medium transition-colors min-h-[36px] whitespace-nowrap',
                isMfaEnabled
                  ? 'bg-red-500/10 text-red-400 border border-red-500/30 hover:bg-red-500/20'
                  : 'bg-brand-700 text-white hover:bg-brand-600',
                'disabled:opacity-50 disabled:cursor-not-allowed',
              ].join(' ')}
            >
              {isMfaEnabled ? 'Disable MFA' : 'Enable MFA'}
            </button>
          )}
        </div>
      </div>

      {/* Setup flow */}
      {step === 'setup' && setupData && (
        <div className="bg-surface-card border border-surface-border rounded-xl p-6 space-y-5">
          <div className="flex items-center gap-2.5">
            <QrCode className="w-4 h-4 text-brand-400" />
            <h2 className="text-sm font-semibold text-slate-100">Set up authenticator</h2>
          </div>

          <ol className="text-sm text-slate-400 space-y-2 list-decimal list-inside">
            <li>Install an authenticator app (Google Authenticator, Authy, 1Password)</li>
            <li>Scan the QR code or enter the secret key manually</li>
            <li>Enter the 6-digit code to confirm setup</li>
          </ol>

          {/* QR Code — scan with authenticator app */}
          <div className="rounded-lg border border-surface-border bg-surface-elevated p-4 space-y-3">
            <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Scan QR Code</p>
            <div className="flex justify-center">
              <img
                src={setupData.qrCodeUrl}
                alt="Scan with your authenticator app"
                className="w-40 h-40 rounded-lg bg-white p-2"
              />
            </div>
            <p className="text-xs text-slate-500 text-center">Or enter the secret key manually:</p>
            <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Secret Key</p>
            <div className="flex items-center gap-2">
              <code className="flex-1 text-sm font-mono text-brand-400 bg-surface-base px-3 py-2 rounded-lg border border-surface-border break-all">
                {setupData.secret}
              </code>
              <button
                onClick={() => copyToClipboard(setupData.secret, 'secret')}
                className="p-2 rounded-lg border border-surface-border text-slate-400 hover:text-slate-200 transition-colors"
              >
                {copiedSecret ? <Check size={14} className="text-green-400" /> : <Copy size={14} />}
              </button>
            </div>
          </div>

          {/* Backup codes */}
          <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-4 space-y-3">
            <div className="flex items-center gap-2">
              <AlertTriangle size={14} className="text-amber-400" />
              <p className="text-xs font-semibold text-amber-400">Save your backup codes</p>
            </div>
            <p className="text-xs text-slate-400">
              These one-time codes let you access your account if you lose your authenticator device. Store them securely.
            </p>
            <div className="grid grid-cols-2 gap-2">
              {setupData.backupCodes.map((code) => (
                <code key={code} className="text-xs font-mono text-slate-300 bg-surface-base px-2 py-1 rounded">
                  {code}
                </code>
              ))}
            </div>
            <button
              onClick={() => copyToClipboard(setupData.backupCodes.join('\n'), 'codes')}
              className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200 transition-colors"
            >
              {copiedCodes ? <Check size={12} className="text-green-400" /> : <Copy size={12} />}
              Copy all codes
            </button>
          </div>

          {/* Verify code */}
          <div className="space-y-2">
            <label className="block text-sm font-medium text-slate-300">
              Verification code
            </label>
            <input
              type="text"
              inputMode="numeric"
              value={verifyCode}
              onChange={(e) => setVerifyCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="000000"
              maxLength={6}
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500 font-mono tracking-widest text-center"
            />
          </div>

          <div className="flex gap-3">
            <button
              onClick={handleEnable}
              disabled={verifyCode.length !== 6 || enableMfa.isPending}
              className="flex-1 px-4 py-2.5 rounded-lg text-sm font-semibold bg-brand-700 text-white hover:bg-brand-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {enableMfa.isPending ? 'Verifying…' : 'Enable MFA'}
            </button>
            <button
              onClick={() => { setStep('idle'); setSetupData(null); setVerifyCode(''); }}
              className="px-4 py-2.5 rounded-lg text-sm font-medium border border-surface-border text-slate-400 hover:text-slate-200 transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Disable flow */}
      {step === 'disable' && (
        <div className="bg-surface-card border border-red-500/30 rounded-xl p-6 space-y-4">
          <div className="flex items-center gap-2.5">
            <ShieldOff className="w-4 h-4 text-red-400" />
            <h2 className="text-sm font-semibold text-slate-100">Disable two-factor authentication</h2>
          </div>
          <p className="text-sm text-slate-400">
            Enter your current TOTP code to confirm. After disabling, your account will only require a password to log in.
          </p>

          <div className="space-y-2">
            <label className="block text-sm font-medium text-slate-300">Authenticator code</label>
            <input
              type="text"
              inputMode="numeric"
              value={disableCode}
              onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="000000"
              maxLength={6}
              className="w-full h-10 bg-surface-elevated border border-surface-border rounded-lg px-3 text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-red-500 font-mono tracking-widest text-center"
            />
          </div>

          <div className="flex gap-3">
            <button
              onClick={handleDisable}
              disabled={disableCode.length !== 6 || disableMfa.isPending}
              className="flex-1 px-4 py-2.5 rounded-lg text-sm font-semibold bg-red-600 text-white hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {disableMfa.isPending ? 'Disabling…' : 'Disable MFA'}
            </button>
            <button
              onClick={() => { setStep('idle'); setDisableCode(''); }}
              className="px-4 py-2.5 rounded-lg text-sm font-medium border border-surface-border text-slate-400 hover:text-slate-200 transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
