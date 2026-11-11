import type React from 'react';
import { X, Save } from 'lucide-react';
import type { SystemSettings } from '../../types';

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
  if (!editingScheduleTime) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4 animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl max-w-sm w-full p-6 border border-slate-200 space-y-4 shadow-xl max-h-[90vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base font-bold text-slate-900">Изменить время расписания</h3>
            <p className="text-xs text-slate-500 mt-0.5">{editingScheduleTime.label}</p>
          </div>
          <button
            onClick={onClose}
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
            onChange={e => setEditingScheduleTime({ ...editingScheduleTime, value: e.target.value })}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                onSave(editingScheduleTime.key, editingScheduleTime.value, editingScheduleTime.label);
                onClose();
              }
            }}
            placeholder="09:00"
            className="w-full text-sm px-3.5 py-2.5 rounded-xl border border-slate-200 font-mono text-center font-bold focus:outline-none focus:border-indigo-500"
          />
        </div>

        <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 transition cursor-pointer"
          >
            Отмена
          </button>
          <button
            type="button"
            onClick={() => {
              onSave(editingScheduleTime.key, editingScheduleTime.value, editingScheduleTime.label);
              onClose();
            }}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
          >
            <Save className="w-3.5 h-3.5" />
            <span>Сохранить</span>
          </button>
        </div>
      </div>
    </div>
  );
};
