import { useCallback, useEffect, useLayoutEffect, useRef } from 'react';
import type { ReactNode } from 'react';

interface AccessibleModalProps {
  title: string;
  onClose: () => void;
  overlayClassName?: string;
  children: ReactNode;
  hasUnsavedChanges?: boolean;
  confirmDiscardMessage?: string;
}

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

export function AccessibleModal({
  title,
  onClose,
  overlayClassName = '',
  children,
  hasUnsavedChanges,
  confirmDiscardMessage,
}: AccessibleModalProps) {
  const dialogRef = useRef<HTMLElement>(null);
  const onCloseRef = useRef(onClose);
  const isDomDirtyRef = useRef(false);
  const hasUnsavedChangesRef = useRef(hasUnsavedChanges);
  const confirmDiscardMessageRef = useRef(confirmDiscardMessage);

  useLayoutEffect(() => {
    onCloseRef.current = onClose;
    hasUnsavedChangesRef.current = hasUnsavedChanges;
    confirmDiscardMessageRef.current = confirmDiscardMessage;
  }, [onClose, hasUnsavedChanges, confirmDiscardMessage]);

  const requestClose = useCallback(() => {
    const isDirty = hasUnsavedChangesRef.current ?? isDomDirtyRef.current;
    if (isDirty) {
      const message =
        confirmDiscardMessageRef.current || 'Есть несохранённые изменения. Закрыть без сохранения?';
      const confirmed =
        typeof window !== 'undefined' && typeof window.confirm === 'function'
          ? window.confirm(message)
          : true;
      if (!confirmed) return;
    }
    isDomDirtyRef.current = false;
    onCloseRef.current();
  }, []);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    const markDirty = () => {
      isDomDirtyRef.current = true;
    };
    dialog.addEventListener('input', markDirty);
    dialog.addEventListener('change', markDirty);

    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const focusable = () => [...dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)];
    const first = focusable()[0];
    (first ?? dialog).focus({ preventScroll: true });

    const handleKeyDown = (event: KeyboardEvent) => {
      const openDialogs = [...document.querySelectorAll<HTMLElement>('[role="dialog"][aria-modal="true"]')];
      if (openDialogs.at(-1) !== dialog) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopImmediatePropagation();
        requestClose();
        return;
      }
      if (event.key !== 'Tab') return;
      const items = focusable();
      if (items.length === 0) {
        event.preventDefault();
        dialog.focus();
        return;
      }
      const firstItem = items[0];
      const lastItem = items[items.length - 1];
      if (event.shiftKey && (document.activeElement === firstItem || !dialog.contains(document.activeElement))) {
        event.preventDefault();
        lastItem.focus();
      } else if (!event.shiftKey && (document.activeElement === lastItem || !dialog.contains(document.activeElement))) {
        event.preventDefault();
        firstItem.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown, true);
    return () => {
      dialog.removeEventListener('input', markDirty);
      dialog.removeEventListener('change', markDirty);
      document.removeEventListener('keydown', handleKeyDown, true);
      if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true });
    };
  }, [requestClose]);

  return (
    <div
      className={`fixed inset-0 z-50 overflow-y-auto bg-slate-950/70 p-3 sm:p-6 backdrop-blur-sm ${overlayClassName}`}
      onMouseDown={event => {
        if (event.target === event.currentTarget) requestClose();
      }}
    >
      <div
        className="flex min-h-full items-center justify-center p-1 sm:p-2"
        onMouseDown={event => {
          if (event.target === event.currentTarget) requestClose();
        }}
      >
        <section
          ref={dialogRef}
          role="dialog"
          aria-modal="true"
          aria-label={title}
          tabIndex={-1}
          onInputCapture={() => {
            isDomDirtyRef.current = true;
          }}
          onChangeCapture={() => {
            isDomDirtyRef.current = true;
          }}
          className="w-full flex justify-center items-start outline-none my-auto py-2"
        >
          {children}
        </section>
      </div>
    </div>
  );
}
