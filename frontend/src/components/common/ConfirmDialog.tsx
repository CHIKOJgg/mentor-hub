import { AlertTriangle, Info } from 'lucide-react';
import { AccessibleModal } from './AccessibleModal';

export interface ConfirmDialogProps {
  isOpen: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  isDanger?: boolean;
  onConfirm: () => void | Promise<void>;
  onClose: () => void;
}

export function ConfirmDialog({
  isOpen,
  title,
  message,
  confirmLabel = 'Подтвердить',
  cancelLabel = 'Отмена',
  isDanger = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  if (!isOpen) return null;

  return (
    <AccessibleModal title={title} onClose={onClose} overlayClassName="z-[60] animate-in fade-in duration-150">
      <div className="bg-white rounded-2xl max-w-md w-full p-5 border border-slate-200/80 shadow-2xl space-y-4 sm:p-6">
        <div className="flex items-start gap-3.5">
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
            isDanger ? 'bg-rose-100 text-rose-600' : 'bg-indigo-100 text-indigo-600'
          }`}>
            {isDanger ? <AlertTriangle className="w-5 h-5" /> : <Info className="w-5 h-5" />}
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-base font-bold text-slate-900">{title}</h3>
            <p className="text-xs sm:text-sm text-slate-600 mt-1 leading-relaxed">{message}</p>
          </div>
        </div>
        <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl border border-slate-200 text-xs sm:text-sm font-semibold text-slate-700 hover:bg-slate-50 transition cursor-pointer"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={() => {
              onClose();
              void onConfirm();
            }}
            className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-semibold text-white transition cursor-pointer ${
              isDanger
                ? 'bg-rose-600 hover:bg-rose-700 shadow-xs'
                : 'bg-slate-900 hover:bg-slate-800 shadow-xs'
            }`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
}
