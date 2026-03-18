import { useState } from 'react';
import { Mail, ChevronDown, ChevronUp } from 'lucide-react';
import { useEmailThreads } from '@/api/tickets';
import { useTimezone } from '@/hooks/use-timezone';
import type { EmailThread } from '@/types/ticket';

interface TicketEmailThreadPanelProps {
  ticketId: string;
}

function ThreadCard({ thread }: { thread: EmailThread }) {
  const [expanded, setExpanded] = useState(false);
  const { formatDateTime } = useTimezone();
  const bodyPreview = thread.bodyText?.slice(0, 200) ?? '(no body)';
  const hasMore = (thread.bodyText?.length ?? 0) > 200;

  return (
    <div className="bg-surface-elevated border border-surface-border rounded-lg p-4">
      <div className="flex items-start justify-between gap-3 mb-2">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium text-slate-200 truncate">{thread.subject}</p>
          <p className="text-xs text-slate-400 mt-0.5">
            From: <span className="text-slate-300">{thread.fromName ?? thread.fromAddress}</span>
            {thread.fromName && (
              <span className="text-slate-500"> &lt;{thread.fromAddress}&gt;</span>
            )}
          </p>
        </div>
        <span className="text-[11px] text-slate-500 flex-shrink-0">
          {formatDateTime(new Date(thread.receivedAt).getTime())}
        </span>
      </div>
      <p className="text-sm text-slate-400 whitespace-pre-wrap">
        {expanded ? thread.bodyText : bodyPreview}
        {!expanded && hasMore && '…'}
      </p>
      {hasMore && (
        <button
          onClick={() => setExpanded(!expanded)}
          className="mt-2 flex items-center gap-1 text-xs text-brand-400 hover:text-brand-300 transition-colors"
        >
          {expanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
          {expanded ? 'Show less' : 'Show more'}
        </button>
      )}
    </div>
  );
}

/** Renders nested email threads — child replies indented under parent. */
function buildThreadTree(threads: EmailThread[]): { thread: EmailThread; children: EmailThread[] }[] {
  const childrenMap = new Map<string, EmailThread[]>();
  const topLevel: EmailThread[] = [];

  for (const t of threads) {
    if (t.parentThreadId) {
      const existing = childrenMap.get(t.parentThreadId) ?? [];
      existing.push(t);
      childrenMap.set(t.parentThreadId, existing);
    } else {
      topLevel.push(t);
    }
  }

  return topLevel.map((t) => ({
    thread: t,
    children: childrenMap.get(t.id) ?? [],
  }));
}

export function TicketEmailThreadPanel({ ticketId }: TicketEmailThreadPanelProps) {
  const { data: threads = [], isLoading } = useEmailThreads(ticketId);

  if (isLoading) {
    return (
      <div className="space-y-3">
        {[0, 1].map((i) => (
          <div key={i} className="h-20 rounded-lg bg-surface-elevated animate-pulse" />
        ))}
      </div>
    );
  }

  const tree = buildThreadTree(threads);

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
        <Mail className="w-4 h-4" />
        Email History ({threads.length})
      </h3>

      {threads.length === 0 && (
        <p className="text-sm text-slate-500 py-4 text-center">No email threads linked to this ticket.</p>
      )}

      <div className="space-y-3">
        {tree.map(({ thread, children }) => (
          <div key={thread.id}>
            <ThreadCard thread={thread} />
            {children.length > 0 && (
              <div className="ml-6 mt-2 space-y-2 border-l-2 border-surface-border pl-4">
                {children.map((child) => (
                  <ThreadCard key={child.id} thread={child} />
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
