import { X, Save } from 'lucide-react';
import { AccessibleModal } from '../common/AccessibleModal';
import { StudentCategoryField } from '../common/StudentCategoryField';
import { useModalDismiss } from '../common/useModalDismiss';

export interface NewStudentForm {
  fullName: string;
  niche: string;
  telegram: string;
  telegramChatId: string;
  initialIncome: number;
  initialAvgTicket: number;
  tariff?: string;
  currency?: string;
  timezone?: string;
}

export interface AddStudentModalProps {
  isOpen: boolean;
  newStudent: NewStudentForm;
  categories: string[];
  onClose: () => void;
  onUpdate: (updater: (prev: NewStudentForm) => NewStudentForm) => void;
  onSubmit: () => void | Promise<void>;
}

export function AddStudentModal({
  isOpen,
  newStudent,
  categories,
  onClose,
  onUpdate,
  onSubmit,
}: AddStudentModalProps) {
  const hasUnsavedChanges = Boolean(
    newStudent.fullName.trim() ||
    newStudent.telegram.trim() ||
    newStudent.initialIncome > 0 ||
    newStudent.initialAvgTicket > 0
  );
  const requestClose = useModalDismiss(onClose, hasUnsavedChanges);
  if (!isOpen) return null;

  return (
    <AccessibleModal title="Новая ученица" onClose={requestClose}>
      <div className="w-full max-w-xl space-y-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-xl sm:p-6">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h3 className="text-base font-bold text-slate-900">Новая ученица</h3>
          </div>
          <button
            type="button"
            onClick={requestClose}
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
            Telegram (@username или без @)
            <div className="relative mt-1">
              <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-sm font-medium text-slate-400">@</span>
              <input
                value={newStudent.telegram.replace(/^@/, '')}
                onChange={event => onUpdate(prev => ({ ...prev, telegram: event.target.value.trim() }))}
                onKeyDown={e => {
                  if (e.key === 'Enter') void onSubmit();
                }}
                placeholder="username_tg"
                className="w-full rounded-xl border border-slate-200 pl-8 pr-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500"
              />
            </div>
          </label>

          <div className="text-xs font-semibold text-slate-600 sm:col-span-2">
            <label htmlFor="new-student-category" className="mb-1 block">Категория ученицы</label>
            <StudentCategoryField
              id="new-student-category"
              value={newStudent.niche}
              categories={categories}
              onChange={niche => onUpdate(prev => ({ ...prev, niche }))}
            />
          </div>

          <label className="text-xs font-semibold text-slate-600">
            Валюта расчетов
            <select
              value={newStudent.currency || 'BYN'}
              onChange={event => onUpdate(prev => ({ ...prev, currency: event.target.value }))}
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500 bg-white"
            >
              <option value="BYN">BYN (Белорусский рубль)</option>
              <option value="RUB">RUB (Российский рубль, ₽)</option>
            </select>
          </label>

          <label className="text-xs font-semibold text-slate-600">
            Часовой пояс
            <select
              value={newStudent.timezone || 'Europe/Minsk'}
              onChange={event => onUpdate(prev => ({ ...prev, timezone: event.target.value }))}
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500 bg-white"
            >
              <option value="Europe/Minsk">Минск / Москва (UTC+3)</option>
              <option value="Asia/Yekaterinburg">Екатеринбург (UTC+5, +2ч)</option>
              <option value="Asia/Omsk">Омск (UTC+6, +3ч)</option>
              <option value="Asia/Novosibirsk">Новосибирск (UTC+7, +4ч)</option>
              <option value="Asia/Irkutsk">Иркутск (UTC+8, +5ч)</option>
              <option value="Asia/Vladivostok">Владивосток (UTC+10, +7ч)</option>
            </select>
          </label>

          <label className="text-xs font-semibold text-slate-600">
            Доход на старте (Точка А), {newStudent.currency === 'RUB' ? '₽' : 'BYN'}
            <input
              type="number"
              min={0}
              value={newStudent.initialIncome || ''}
              onChange={event => onUpdate(prev => ({ ...prev, initialIncome: Number(event.target.value) || 0 }))}
              placeholder="1000"
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500"
            />
          </label>

          <label className="text-xs font-semibold text-slate-600">
            Средний чек на старте, {newStudent.currency === 'RUB' ? '₽' : 'BYN'}
            <input
              type="number"
              min={0}
              value={newStudent.initialAvgTicket || ''}
              onChange={event => onUpdate(prev => ({ ...prev, initialAvgTicket: Number(event.target.value) || 0 }))}
              placeholder="150"
              className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal focus:outline-none focus:border-indigo-500"
            />
          </label>
        </div>
        <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
          <button
            type="button"
            onClick={requestClose}
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
    </AccessibleModal>
  );
}
