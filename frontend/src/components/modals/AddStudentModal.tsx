import { X, Save } from 'lucide-react';
import { BEAUTY_NICHES } from '../../types';

export interface NewStudentForm {
  fullName: string;
  niche: string;
  telegram: string;
  telegramChatId: string;
  initialIncome: number;
  initialAvgTicket: number;
  tariff: 'Стандарт' | 'VIP';
}

export interface AddStudentModalProps {
  isOpen: boolean;
  newStudent: NewStudentForm;
  onClose: () => void;
  onUpdate: (updater: (prev: NewStudentForm) => NewStudentForm) => void;
  onSubmit: () => void | Promise<void>;
}

export function AddStudentModal({
  isOpen,
  newStudent,
  onClose,
  onUpdate,
  onSubmit,
}: AddStudentModalProps) {
  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4 animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="w-full max-w-xl space-y-5 rounded-2xl border border-slate-200 bg-white p-6 shadow-xl"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h3 className="text-base font-bold text-slate-900">Новая ученица</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Закрыть"
            className="rounded-lg p-1 text-slate-500 hover:bg-slate-100"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
            Имя и фамилия ученицы
            <input
              value={newStudent.fullName}
              onChange={event => onUpdate(prev => ({ ...prev, fullName: event.target.value }))}
              onKeyDown={e => {
                if (e.key === 'Enter') void onSubmit();
              }}
              placeholder="Например: Милана Горохова"
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500"
            />
          </label>

          <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
            Ниша (Бьюти & Стиль)
            <select
              value={newStudent.niche}
              onChange={event => onUpdate(prev => ({ ...prev, niche: event.target.value }))}
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500 bg-white"
            >
              {BEAUTY_NICHES.map(niche => (
                <option key={niche} value={niche}>{niche}</option>
              ))}
            </select>
          </label>

          <label className="text-xs font-semibold text-slate-600">
            Доход на старте (Точка А), ₽
            <input
              type="number"
              min={0}
              value={newStudent.initialIncome || ''}
              onChange={event => onUpdate(prev => ({ ...prev, initialIncome: Number(event.target.value) || 0 }))}
              placeholder="30000"
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500"
            />
          </label>

          <label className="text-xs font-semibold text-slate-600">
            Средний чек на старте, ₽
            <input
              type="number"
              min={0}
              value={newStudent.initialAvgTicket || ''}
              onChange={event => onUpdate(prev => ({ ...prev, initialAvgTicket: Number(event.target.value) || 0 }))}
              placeholder="3000"
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500"
            />
          </label>

          <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
            Тариф обучения
            <select
              value={newStudent.tariff}
              onChange={event => onUpdate(prev => ({ ...prev, tariff: event.target.value === 'VIP' ? 'VIP' : 'Стандарт' }))}
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500 bg-white"
            >
              <option value="Стандарт">Стандарт</option>
              <option value="VIP">VIP</option>
            </select>
          </label>
        </div>
        <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl px-4 py-2 text-sm font-semibold text-slate-600"
          >
            Отмена
          </button>
          <button
            type="button"
            onClick={() => void onSubmit()}
            className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white"
          >
            <Save className="h-4 w-4" />
            <span>Создать профиль</span>
          </button>
        </div>
      </div>
    </div>
  );
}
