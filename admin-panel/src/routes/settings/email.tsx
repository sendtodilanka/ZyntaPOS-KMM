import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import {
  Mail,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Clock,
  Filter,
  X,
  FileText,
  Settings,
  Save,
  Eye,
} from 'lucide-react';
import {
  useEmailDeliveryLogs,
  useEmailTemplates,
  useEmailTemplate,
  useUpdateEmailTemplate,
  useEmailPreferences,
  useUpdateEmailPreferences,
  type EmailDeliveryLog,
  type EmailStatus,
  type EmailLogFilters,
  type EmailTemplate,
} from '@/api/email';
import { useTimezone } from '@/hooks/use-timezone';
import { toast } from '@/stores/ui-store';

export const Route = createFileRoute('/settings/email')({
  component: EmailSettingsPage,
});

type Tab = 'logs' | 'templates' | 'preferences';

const STATUS_CONFIG = {
  SENT: { icon: Clock, color: 'text-blue-500', bg: 'bg-blue-50', label: 'Sent' },
  DELIVERED: { icon: CheckCircle, color: 'text-green-500', bg: 'bg-green-50', label: 'Delivered' },
  BOUNCED: { icon: AlertTriangle, color: 'text-yellow-500', bg: 'bg-yellow-50', label: 'Bounced' },
  FAILED: { icon: XCircle, color: 'text-red-500', bg: 'bg-red-50', label: 'Failed' },
} as const;

const STATUSES: EmailStatus[] = ['SENT', 'DELIVERED', 'BOUNCED', 'FAILED'];

function EmailSettingsPage() {
  const [activeTab, setActiveTab] = useState<Tab>('logs');

  const tabs: { id: Tab; label: string; icon: React.ElementType }[] = [
    { id: 'logs', label: 'Delivery Logs', icon: Mail },
    { id: 'templates', label: 'Templates', icon: FileText },
    { id: 'preferences', label: 'Preferences', icon: Settings },
  ];

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h2 className="text-xl font-semibold flex items-center gap-2">
          <Mail className="h-5 w-5" />
          Email Management
        </h2>
        <p className="text-sm text-muted-foreground mt-1">
          Manage email delivery, templates, and notification preferences.
        </p>
      </div>

      {/* Tab Navigation */}
      <div className="flex gap-1 border-b">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px ${
                activeTab === tab.id
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-muted-foreground hover:text-foreground hover:border-muted-foreground/30'
              }`}
            >
              <Icon className="h-4 w-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {activeTab === 'logs' && <DeliveryLogsTab />}
      {activeTab === 'templates' && <TemplatesTab />}
      {activeTab === 'preferences' && <PreferencesTab />}
    </div>
  );
}

// ── Delivery Logs Tab ─────────────────────────────────────────────────────────

function DeliveryLogsTab() {
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState<EmailLogFilters>({});
  const [showFilters, setShowFilters] = useState(false);
  const { data, isLoading, isError } = useEmailDeliveryLogs(page, 20, filters);
  const { formatDateTime } = useTimezone();

  const hasActiveFilters = filters.status || filters.startDate || filters.endDate;

  const updateFilter = (key: keyof EmailLogFilters, value: string | undefined) => {
    setPage(1);
    setFilters((prev) => ({ ...prev, [key]: value || undefined }));
  };

  const clearFilters = () => {
    setPage(1);
    setFilters({});
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Track transactional email delivery status and troubleshoot failures.
        </p>
        <button
          onClick={() => setShowFilters(!showFilters)}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-sm border rounded-md transition-colors ${
            hasActiveFilters
              ? 'border-blue-300 bg-blue-50 text-blue-700'
              : 'hover:bg-muted/50'
          }`}
        >
          <Filter className="h-4 w-4" />
          Filters
          {hasActiveFilters && (
            <span className="ml-1 px-1.5 py-0.5 text-xs bg-blue-200 text-blue-800 rounded-full">
              {[filters.status, filters.startDate, filters.endDate].filter(Boolean).length}
            </span>
          )}
        </button>
      </div>

      {showFilters && (
        <div className="flex flex-wrap items-end gap-4 p-4 bg-muted/30 rounded-lg border">
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground">Status</label>
            <select
              value={filters.status || ''}
              onChange={(e) => updateFilter('status', e.target.value as EmailStatus)}
              className="px-3 py-1.5 text-sm border rounded-md bg-background"
            >
              <option value="">All statuses</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_CONFIG[s].label}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground">From</label>
            <input
              type="date"
              value={filters.startDate || ''}
              onChange={(e) => updateFilter('startDate', e.target.value)}
              className="px-3 py-1.5 text-sm border rounded-md bg-background"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground">To</label>
            <input
              type="date"
              value={filters.endDate || ''}
              onChange={(e) => updateFilter('endDate', e.target.value)}
              className="px-3 py-1.5 text-sm border rounded-md bg-background"
            />
          </div>

          {hasActiveFilters && (
            <button
              onClick={clearFilters}
              className="flex items-center gap-1 px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
            >
              <X className="h-3.5 w-3.5" />
              Clear
            </button>
          )}
        </div>
      )}

      {isLoading && (
        <div className="text-center py-12 text-muted-foreground">Loading delivery logs...</div>
      )}

      {isError && (
        <div className="text-center py-12 text-destructive">
          Failed to load delivery logs. The email delivery log endpoint may not be configured yet.
        </div>
      )}

      {data && data.logs.length === 0 && (
        <div className="text-center py-12 text-muted-foreground">
          {hasActiveFilters
            ? 'No emails match the current filters.'
            : 'No email delivery logs found.'}
        </div>
      )}

      {data && data.logs.length > 0 && (
        <>
          <div className="border rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="text-left px-4 py-3 font-medium">Recipient</th>
                  <th className="text-left px-4 py-3 font-medium">Subject</th>
                  <th className="text-left px-4 py-3 font-medium">Template</th>
                  <th className="text-left px-4 py-3 font-medium">Status</th>
                  <th className="text-left px-4 py-3 font-medium">Sent At</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {data.logs.map((log: EmailDeliveryLog) => {
                  const config = STATUS_CONFIG[log.status];
                  const StatusIcon = config.icon;
                  return (
                    <tr key={log.id} className="hover:bg-muted/30" title={log.error || undefined}>
                      <td className="px-4 py-3 font-mono text-xs">{log.to}</td>
                      <td className="px-4 py-3 truncate max-w-[200px]">{log.subject}</td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs bg-muted">
                          {log.template}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs ${config.bg} ${config.color}`}
                        >
                          <StatusIcon className="h-3 w-3" />
                          {config.label}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground text-xs">
                        {log.sentAt ? formatDateTime(new Date(log.sentAt).getTime()) : '\u2014'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between text-sm text-muted-foreground">
            <span>
              Showing {(page - 1) * 20 + 1}\u2013{Math.min(page * 20, data.total)} of {data.total}
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page <= 1}
                className="px-3 py-1 border rounded disabled:opacity-50"
              >
                Previous
              </button>
              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={page * 20 >= data.total}
                className="px-3 py-1 border rounded disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

// ── Templates Tab ─────────────────────────────────────────────────────────────

function TemplatesTab() {
  const { data: templates, isLoading, isError } = useEmailTemplates();
  const [editingSlug, setEditingSlug] = useState<string | null>(null);

  if (isLoading) {
    return <div className="text-center py-12 text-muted-foreground">Loading templates...</div>;
  }

  if (isError) {
    return (
      <div className="text-center py-12 text-destructive">
        Failed to load email templates.
      </div>
    );
  }

  if (editingSlug) {
    return <TemplateEditor slug={editingSlug} onClose={() => setEditingSlug(null)} />;
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Edit email templates used for transactional emails. Use {'{{variable}}'} placeholders for dynamic content.
      </p>

      {templates && templates.length === 0 && (
        <div className="text-center py-12 text-muted-foreground">No email templates found.</div>
      )}

      {templates && templates.length > 0 && (
        <div className="grid gap-3">
          {templates.map((tpl: EmailTemplate) => (
            <div
              key={tpl.slug}
              className="flex items-center justify-between p-4 border rounded-lg hover:bg-muted/30 transition-colors"
            >
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <FileText className="h-4 w-4 text-muted-foreground shrink-0" />
                  <span className="font-medium text-sm">{tpl.name}</span>
                  <span className="text-xs text-muted-foreground font-mono bg-muted px-1.5 py-0.5 rounded">
                    {tpl.slug}
                  </span>
                </div>
                <p className="text-sm text-muted-foreground mt-1 truncate">{tpl.subject}</p>
              </div>
              <button
                onClick={() => setEditingSlug(tpl.slug)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-sm border rounded-md hover:bg-muted/50 transition-colors shrink-0 ml-4"
              >
                <FileText className="h-3.5 w-3.5" />
                Edit
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function TemplateEditor({ slug, onClose }: { slug: string; onClose: () => void }) {
  const { data: template, isLoading } = useEmailTemplate(slug);
  const updateMutation = useUpdateEmailTemplate();
  const [subject, setSubject] = useState('');
  const [htmlBody, setHtmlBody] = useState('');
  const [showPreview, setShowPreview] = useState(false);
  const [initialized, setInitialized] = useState(false);

  // Initialize form when template loads
  if (template && !initialized) {
    setSubject(template.subject);
    setHtmlBody(template.htmlBody);
    setInitialized(true);
  }

  const handleSave = async () => {
    try {
      await updateMutation.mutateAsync({ slug, subject, htmlBody });
      toast.success('Template updated', 'Email template saved successfully.');
      onClose();
    } catch {
      toast.error('Save failed', 'Could not update the email template.');
    }
  };

  if (isLoading) {
    return <div className="text-center py-12 text-muted-foreground">Loading template...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <button
            onClick={onClose}
            className="text-sm text-muted-foreground hover:text-foreground"
          >
            Templates
          </button>
          <span className="text-muted-foreground">/</span>
          <span className="text-sm font-medium">{template?.name}</span>
          <span className="text-xs text-muted-foreground font-mono bg-muted px-1.5 py-0.5 rounded">
            {slug}
          </span>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setShowPreview(!showPreview)}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm border rounded-md hover:bg-muted/50"
          >
            <Eye className="h-3.5 w-3.5" />
            {showPreview ? 'Editor' : 'Preview'}
          </button>
          <button
            onClick={handleSave}
            disabled={updateMutation.isPending}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            <Save className="h-3.5 w-3.5" />
            {updateMutation.isPending ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>

      <div className="space-y-3">
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">Subject Line</label>
          <input
            type="text"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            className="px-3 py-2 text-sm border rounded-md bg-background"
            placeholder="Email subject..."
          />
        </div>

        {showPreview ? (
          <div className="border rounded-lg p-4">
            <label className="text-xs font-medium text-muted-foreground mb-2 block">
              Preview
            </label>
            <div
              className="bg-white p-4 rounded border text-sm"
              dangerouslySetInnerHTML={{ __html: htmlBody }}
            />
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-muted-foreground">
              HTML Body
            </label>
            <textarea
              value={htmlBody}
              onChange={(e) => setHtmlBody(e.target.value)}
              rows={16}
              className="px-3 py-2 text-sm border rounded-md bg-background font-mono resize-y"
              placeholder="HTML template body..."
            />
            <p className="text-xs text-muted-foreground">
              Available placeholders: {'{{name}}'}, {'{{resetLink}}'}, {'{{ticketNumber}}'}, {'{{title}}'},{' '}
              {'{{newStatus}}'}, {'{{priority}}'}, {'{{agentName}}'}, {'{{messageBody}}'},{' '}
              {'{{customerName}}'}, {'{{adminPanelUrl}}'}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Preferences Tab ───────────────────────────────────────────────────────────

function PreferencesTab() {
  const { data: prefs, isLoading, isError } = useEmailPreferences();
  const updateMutation = useUpdateEmailPreferences();

  const handleToggle = async (key: 'marketingEmails' | 'ticketNotifications', value: boolean) => {
    if (!prefs) return;
    try {
      await updateMutation.mutateAsync({
        marketingEmails: key === 'marketingEmails' ? value : prefs.marketingEmails,
        ticketNotifications: key === 'ticketNotifications' ? value : prefs.ticketNotifications,
      });
      toast.success('Preferences updated', 'Your email preferences have been saved.');
    } catch {
      toast.error('Update failed', 'Could not update email preferences.');
    }
  };

  if (isLoading) {
    return <div className="text-center py-12 text-muted-foreground">Loading preferences...</div>;
  }

  if (isError) {
    return (
      <div className="text-center py-12 text-destructive">Failed to load email preferences.</div>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Control which email notifications you receive.
      </p>

      {prefs?.unsubscribed && (
        <div className="p-3 bg-yellow-50 border border-yellow-200 rounded-lg text-sm text-yellow-800">
          You are currently unsubscribed from all emails via the unsubscribe link. Toggle preferences below to
          re-subscribe.
        </div>
      )}

      <div className="space-y-3">
        <PreferenceToggle
          label="Marketing Emails"
          description="Receive product updates, feature announcements, and newsletters."
          checked={prefs?.marketingEmails ?? true}
          onChange={(v) => handleToggle('marketingEmails', v)}
          disabled={updateMutation.isPending}
        />
        <PreferenceToggle
          label="Ticket Notifications"
          description="Receive email notifications when support tickets are created, updated, or assigned to you."
          checked={prefs?.ticketNotifications ?? true}
          onChange={(v) => handleToggle('ticketNotifications', v)}
          disabled={updateMutation.isPending}
        />
      </div>
    </div>
  );
}

function PreferenceToggle({
  label,
  description,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (value: boolean) => void;
  disabled: boolean;
}) {
  return (
    <div className="flex items-center justify-between p-4 border rounded-lg">
      <div>
        <div className="text-sm font-medium">{label}</div>
        <div className="text-xs text-muted-foreground mt-0.5">{description}</div>
      </div>
      <button
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        disabled={disabled}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors disabled:opacity-50 ${
          checked ? 'bg-blue-600' : 'bg-gray-200'
        }`}
      >
        <span
          className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow transform transition-transform ${
            checked ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </button>
    </div>
  );
}
