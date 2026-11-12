import type React from 'react';
import { useState } from 'react';
import { X, Save } from 'lucide-react';
import type { SystemSettings } from '../../types';
import { AccessibleModal } from '../common/AccessibleModal';
import { useModalDismiss } from '../common/useModalDismiss';

export interface ScheduleTimeItem {
  key: keyof SystemSettings;
  label: string;
  value: string;
}

interface ScheduleTimeModalProps {
  editingScheduleTime: ScheduleTimeItem | null;
  setEditingScheduleTime: React.Dispatch<React.SetStateAction<ScheduleTimeItem | null>>;
  onClose: () => void;
  onSave: (key: keyof SystemSettings, value: string, label: string) => void;
}

export const ScheduleTimeModal = ({
  editingScheduleTime,
  setEditingScheduleTime,
  onClose,
  onSave,
}: ScheduleTimeModalProps) => {
  const [error, setError] = useState('');
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const closeDialog = () => { setHasUnsavedChanges(false); onClose(); };
  const requestClose = useModalDismiss(closeDialog, hasUnsavedChanges);
  if (!editingScheduleTime) return null;

  const isValidTime = (value: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(value);

  return (
    <AccessibleModal title="Изменить время расписания" onClose={requestClose}>
      <div className="bg-white rounded-2xl max-w-sm w-full p-5 border border-slate-200 space-y-4 shadow-xl sm:p-6">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base font-bold text-slate-900">Изменить время расписания</h3>
            <p className="text-xs text-slate-500 mt-0.5">{editingScheduleTime.label}</p>
          </div>
          <button
            onClick={requestClose}
            type="button"
            aria-label="Закрыть изменение времени"
            className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div>
          <label className="text-xs font-semibold text-slate-600 block mb-1">Время (ЧЧ:ММ):</label>
          <input
            type="text"
            value={editingScheduleTime.value}
            onChange={e => { setHasUnsavedChanges(true); setEditingScheduleTime({ ...editingScheduleTime, value: e.target.value }); setError(''); }}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                if (!isValidTime(editingScheduleTime.value)) { setError('Формат ЧЧ:ММ, например 09:00'); return; }
                onSave(editingScheduleTime.key, editingScheduleTime.value, editingScheduleTime.label);
                closeDialog();
              }
            }}
            placeholder="09:00"
            className="w-full text-sm px-3.5 py-2.5 rounded-xl border border-slate-200 font-mono text-center font-bold focus:outline-none focus:border-indigo-500"
          />
          {error && <p className="text-xs text-rose-600 mt-1">{error}</p>}
        </div>

        <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
          <button
            type="button"
            onClick={requestClose}
            className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 transition cursor-pointer"
          >
            Отмена
          </button>
          <button
            type="button"
            onClick={() => {
              if (!isValidTime(editingScheduleTime.value)) { setError('Формат ЧЧ:ММ, например 09:00'); return; }
              onSave(editingScheduleTime.key, editingScheduleTime.value, editingScheduleTime.label);
              closeDialog();
            }}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
          >
            <Save className="w-3.5 h-3.5" />
            <span>Сохранить</span>
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
};
