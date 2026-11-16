import type { ReactNode } from "react";
import { AlertCircle, Inbox, LoaderCircle, ShieldAlert } from "lucide-react";

export function LoadingState({ label = "Loading", className = "" }: { label?: string; className?: string }) {
  return (
    <div className={`flex items-center justify-center gap-2 p-8 text-sm text-muted ${className}`} role="status">
      <LoaderCircle size={16} aria-hidden="true" className="animate-spin text-accent" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({
  title = "No records found",
  description,
  action,
}: {
  title?: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <section className="flex flex-col items-center gap-2 p-8 text-center" aria-labelledby="empty-state-title">
      <Inbox size={20} aria-hidden="true" className="text-muted" />
      <h2 id="empty-state-title" className="text-sm font-semibold text-ink">
        {title}
      </h2>
      {description && <p className="max-w-lg text-sm text-muted">{description}</p>}
      {action}
    </section>
  );
}

export function ErrorState({
  title = "We could not load this information",
  description = "Try again. Your existing data has not been changed.",
  onRetry,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
}) {
  return (
    <section className="flex flex-col items-center gap-2 p-8 text-center" role="alert">
      <AlertCircle size={20} aria-hidden="true" className="text-bad" />
      <h2 className="text-sm font-semibold text-ink">{title}</h2>
      <p className="max-w-lg text-sm text-muted">{description}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 min-h-10 rounded-md border border-border px-3 py-2 text-sm font-medium text-ink hover:border-accent"
        >
          Try again
        </button>
      )}
    </section>
  );
}

export function UnauthorizedState({
  title = "Access restricted",
  description = "Your account does not have permission to view this information.",
}: {
  title?: string;
  description?: string;
}) {
  return (
    <section className="flex flex-col items-center gap-2 p-8 text-center" role="alert">
      <ShieldAlert size={20} aria-hidden="true" className="text-warn" />
      <h2 className="text-sm font-semibold text-ink">{title}</h2>
      <p className="max-w-lg text-sm text-muted">{description}</p>
    </section>
  );
}
