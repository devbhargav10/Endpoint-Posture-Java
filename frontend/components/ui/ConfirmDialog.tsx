"use client";

import { useEffect, useId, useRef } from "react";

export function ConfirmDialog({
  open,
  title,
  message,
  danger,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  message: string;
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const titleId = useId();
  const messageId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return;

    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    cancelRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onCancel();
        return;
      }

      if (event.key !== "Tab" || !dialogRef.current) return;
      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
        ),
      );
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      previousFocus?.focus();
    };
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={messageId}
        className="w-full max-w-sm rounded-lg border border-border bg-panel p-6 shadow-2xl"
      >
        <h2 id={titleId} className="text-base font-bold text-ink">
          {title}
        </h2>
        <p id={messageId} className="mt-2 text-sm leading-relaxed text-muted">
          {message}
        </p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            ref={cancelRef}
            type="button"
            onClick={onCancel}
            className="min-h-10 rounded-md border border-border bg-panel px-3 py-2 text-sm font-medium text-ink hover:bg-ink/[0.04]"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className={`min-h-10 rounded-md px-3 py-2 text-sm font-semibold ${danger ? "bg-bad text-white hover:bg-bad/90" : "bg-accent text-base hover:bg-accent/90"}`}
          >
            Confirm
          </button>
        </div>
      </div>
    </div>
  );
}
