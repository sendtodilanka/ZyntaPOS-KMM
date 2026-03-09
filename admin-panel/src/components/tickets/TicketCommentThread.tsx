import { useState } from 'react';
import { Lock, MessageSquare } from 'lucide-react';
import { useTicketComments, useAddComment } from '@/api/tickets';
import { useTimezone } from '@/hooks/use-timezone';
import { useAuth } from '@/hooks/use-auth';
import { cn } from '@/lib/utils';

interface TicketCommentThreadProps {
  ticketId: string;
}

export function TicketCommentThread({ ticketId }: TicketCommentThreadProps) {
  const { data: comments = [], isLoading } = useTicketComments(ticketId);
  const addComment = useAddComment();
  const { formatDateTime } = useTimezone();
  const { user, hasPermission } = useAuth();
  const [body, setBody] = useState('');
  const [isInternal, setIsInternal] = useState(false);

  const canMarkInternal = hasPermission('audit:read'); // ADMIN, OPERATOR, AUDITOR

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!body.trim()) return;
    addComment.mutate(
      { ticketId, body: { body: body.trim(), isInternal } },
      { onSuccess: () => { setBody(''); setIsInternal(false); } },
    );
  };

  if (isLoading) {
    return (
      <div className="space-y-3">
        {[0, 1, 2].map((i) => (
          <div key={i} className="h-16 rounded-lg bg-surface-elevated animate-pulse" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
        <MessageSquare className="w-4 h-4" />
        Comments ({comments.length})
      </h3>

      {comments.length === 0 && (
        <p className="text-sm text-slate-500 py-4 text-center">No comments yet. Be the first to add one.</p>
      )}

      <div className="space-y-3">
        {comments.map((comment) => (
          <div
            key={comment.id}
            className={cn(
              'rounded-lg border p-4',
              comment.isInternal
                ? 'bg-amber-500/5 border-amber-500/20'
                : 'bg-surface-elevated border-surface-border',
            )}
          >
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-full bg-brand-500/20 flex items-center justify-center text-[10px] font-semibold text-brand-400 flex-shrink-0">
                  {comment.authorName.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)}
                </div>
                <span className="text-xs font-medium text-slate-300">{comment.authorName}</span>
                {comment.isInternal && (
                  <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-amber-400 bg-amber-500/10 border border-amber-500/20 px-1.5 py-0.5 rounded">
                    <Lock className="w-2.5 h-2.5" /> Internal
                  </span>
                )}
              </div>
              <span className="text-[11px] text-slate-500">{formatDateTime(comment.createdAt)}</span>
            </div>
            <p className="text-sm text-slate-300 whitespace-pre-wrap">{comment.body}</p>
          </div>
        ))}
      </div>

      {hasPermission('tickets:comment') && (
        <form onSubmit={handleSubmit} className="space-y-3 pt-2 border-t border-surface-border">
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            rows={3}
            placeholder="Add a comment…"
            className="w-full bg-surface-elevated border border-surface-border rounded-lg px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500 resize-none"
          />
          <div className="flex items-center justify-between">
            {canMarkInternal && (
              <label className="flex items-center gap-2 text-xs text-slate-400 cursor-pointer">
                <input
                  type="checkbox"
                  checked={isInternal}
                  onChange={(e) => setIsInternal(e.target.checked)}
                  className="rounded border-surface-border bg-surface-elevated"
                />
                <Lock className="w-3 h-3" />
                Internal only
              </label>
            )}
            <button
              type="submit"
              disabled={!body.trim() || addComment.isPending}
              className="ml-auto px-4 py-2 rounded-lg text-sm font-medium bg-brand-500 text-white hover:bg-brand-600 disabled:opacity-50 transition-colors min-h-[36px]"
            >
              {addComment.isPending ? 'Posting…' : 'Add Comment'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
