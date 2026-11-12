import { useState } from 'react';
import { X, Save } from 'lucide-react';
import type { DayInfo } from '../../types';
import { AccessibleModal } from '../common/AccessibleModal';
import { useModalDismiss } from '../common/useModalDismiss';

export interface DayStatusModalProps {
  editingDay: {
    studentId: string;
    studentName: string;
    dayIndex: number;
    day: DayInfo;
  } | null;
  onClose: () => void;
  onSave: (studentId: string, dayIndex: number, day: DayInfo) => void;
  onUpdateDay: (day: DayInfo) => void;
}

export function DayStatusModal({ editingDay, onClose, onSave, onUpdateDay }: DayStatusModalProps) {
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const closeDialog = () => { setHasUnsavedChanges(false); onClose(); };
  const requestClose = useModalDismiss(closeDialog, hasUnsavedChanges);
  if (!editingDay) return null;
  const updateDay = (day: DayInfo) => {
    setHasUnsavedChanges(true);
    onUpdateDay(day);
  };

  return (
    <AccessibleModal title="Редактировать статус дня" onClose={requestClose}>
      <div className="bg-white rounded-2xl max-w-md w-full p-5 border border-slate-200 space-y-4 shadow-xl sm:p-6">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base font-bold text-slate-900">Редактировать статус дня</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              {editingDay.studentName} · {editingDay.day.day}, {editingDay.day.date}
            </p>
          </div>
          <button
            onClick={requestClose}
            type="button"
            aria-label="Закрыть изменение статуса"
            className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-3">
          <label className="text-xs font-semibold text-slate-600 block">Статус сдачи:</label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {[
              { status: 'COMPLETED_ON_TIME', label: 'В срок (до 21:00)', color: 'border-emerald-300 bg-emerald-50 text-emerald-800' },
              { status: 'COMPLETED_LATE', label: 'Опоздание', color: 'border-amber-300 bg-amber-50 text-amber-800' },
              { status: 'MISSED', label: 'Пропуск', color: 'border-rose-300 bg-rose-50 text-rose-800' },
              { status: 'PAUSED', label: 'Пауза (болезнь)', color: 'border-slate-300 bg-slate-100 text-slate-800' },
              { status: 'WEEKEND', label: 'Выходной', color: 'border-slate-200 bg-slate-50 text-slate-600' },
            ].map(opt => (
              <button
                key={opt.status}
                type="button"
                onClick={() => {
                  updateDay({ ...editingDay.day, status: opt.status as DayInfo['status'] });
                }}
                className={`text-xs font-semibold p-2.5 rounded-xl border text-left transition cursor-pointer flex items-center gap-2 ${
                  editingDay.day.status === opt.status
                    ? opt.color + ' ring-2 ring-indigo-500/20'
                    : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                }`}
              >
                <span
                  className={`w-2 h-2 rounded-full ${
                    opt.status === 'COMPLETED_ON_TIME'
                      ? 'bg-emerald-500'
                      : opt.status === 'COMPLETED_LATE'
                      ? 'bg-amber-400'
                      : opt.status === 'MISSED'
                      ? 'bg-red-400'
                      : opt.status === 'PAUSED'
                      ? 'bg-neutral-300'
                      : 'bg-neutral-200'
                  }`}
                />
                <span>{opt.label}</span>
              </button>
            ))}
          </div>

          <div className="pt-2">
            <label className="text-xs font-semibold text-slate-600 block mb-1.5">
              Детали отчёта / причина (для тултипа):
            </label>
            <input
              type="text"
              value={editingDay.day.missedDetails || ''}
              onChange={e => {
                updateDay({ ...editingDay.day, missedDetails: e.target.value });
              }}
              onKeyDown={e => {
                if (e.key === 'Enter') {
                  onSave(editingDay.studentId, editingDay.dayIndex, editingDay.day);
                  closeDialog();
                }
              }}
              placeholder="Например: Reels #14, карусель, 25 рассылок, +2 клиента (Enter чтобы сохранить)"
              className="w-full text-xs sm:text-sm px-3.5 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
            />
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
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
              onSave(editingDay.studentId, editingDay.dayIndex, editingDay.day);
              closeDialog();
            }}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
          >
            <Save className="w-3.5 h-3.5" />
            <span>Сохранить статус</span>
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
}
