import { useState } from 'react';
import type React from 'react';
import { X, Plus, Pencil, Send, Coffee, Check, ShieldAlert } from 'lucide-react';
import type { StudentDiscipline } from '../../types';
import { AccessibleModal } from '../common/AccessibleModal';
import { ConfirmDialog } from '../common/ConfirmDialog';
import { StudentCategoryField } from '../common/StudentCategoryField';

interface StudentSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  students: StudentDiscipline[];
  categories: string[];
  onEditStudent: (student: StudentDiscipline) => void;
  onSaveCategory: (studentId: string, category: string) => Promise<void>;
  onAddStudent: () => void;
}

export const StudentSettingsModal: React.FC<StudentSettingsModalProps> = ({
  isOpen,
  onClose,
  students,
  categories,
  onEditStudent,
  onSaveCategory,
  onAddStudent,
}) => {
  const [categoryDrafts, setCategoryDrafts] = useState<Record<string, string>>({});
  const [savingCategoryId, setSavingCategoryId] = useState<string | null>(null);
  const [isDiscardConfirmationOpen, setIsDiscardConfirmationOpen] = useState(false);
  const hasUnsavedCategoryChanges = students.some(student =>
    categoryDrafts[student.id] !== undefined
    && categoryDrafts[student.id].trim() !== student.niche.trim());
  const requestClose = () => {
    if (savingCategoryId) return;
    if (hasUnsavedCategoryChanges) {
      setIsDiscardConfirmationOpen(true);
      return;
    }
    onClose();
  };

  if (!isOpen) return null;

  return (
    <>
    <AccessibleModal title="Управление составом учениц потока" onClose={requestClose}>
      <div className="bg-white rounded-2xl max-w-2xl w-full p-5 border border-slate-200 space-y-5 shadow-2xl sm:p-6">
        {/* Header */}
        <div className="flex items-start justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base sm:text-lg font-bold text-slate-900">Управление составом учениц потока</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Профили, категории учениц и состав потока
            </p>
          </div>
          <button
            onClick={requestClose}
            type="button"
            aria-label="Закрыть управление ученицами"
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Add Student Quick Trigger */}
        <div className="flex items-center justify-between gap-3 p-3.5 rounded-xl bg-slate-50 border border-slate-200">
          <div>
            <span className="text-xs font-bold text-slate-900 block">Новая подопечная в потоке?</span>
            <span className="text-[11px] text-slate-500">Добавление профиля, Точки А и привязка Telegram-группы</span>
          </div>
          <button
            type="button"
            disabled={hasUnsavedCategoryChanges || savingCategoryId !== null}
            onClick={() => {
              onClose();
              onAddStudent();
            }}
            title={hasUnsavedCategoryChanges ? 'Сначала сохраните или отмените изменение категории' : undefined}
            className="inline-flex min-h-11 items-center gap-1.5 rounded-xl bg-slate-900 px-3.5 py-2 text-white text-xs font-semibold shadow-xs transition cursor-pointer shrink-0 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Plus className="w-4 h-4" />
            <span>Добавить ученицу</span>
          </button>
        </div>
        {hasUnsavedCategoryChanges && (
          <p className="-mt-3 px-1 text-xs text-amber-700" role="status">
            Сначала сохраните или отмените изменение категории.
          </p>
        )}

        {/* Students List */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs font-semibold text-slate-500 px-1">
            <span>Список учениц ({students.length}):</span>
            <span className="text-[11px] text-slate-400">Категорию можно сменить прямо здесь</span>
          </div>

          <div className="divide-y divide-slate-100 max-h-[380px] overflow-y-auto border border-slate-200 rounded-xl">
            {students.map(s => (
              <div
                key={s.id}
                className="p-3.5 hover:bg-slate-50/80 transition flex flex-col sm:flex-row sm:items-start justify-between gap-3"
              >
                <div className="flex items-start gap-3 min-w-0 flex-1">
                  <div className="w-10 h-10 rounded-full bg-slate-100 text-slate-800 flex items-center justify-center text-xs font-bold shrink-0">
                    {s.avatarText}
                  </div>
                  <div className="min-w-0 flex-1 space-y-2">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-bold text-slate-900">{s.fullName}</span>
                      {s.isOnPause ? (
                        <span className="text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200 px-2 py-0.5 rounded-md inline-flex items-center gap-1">
                          <Coffee className="w-3 h-3" />
                          <span>на паузе</span>
                        </span>
                      ) : (
                        <span className="text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200 px-2 py-0.5 rounded-md inline-flex items-center gap-1">
                          <Check className="w-3 h-3" />
                          <span>активна</span>
                        </span>
                      )}
                    </div>
                    <div className="text-xs text-slate-500 flex items-center gap-2 flex-wrap">
                      {s.telegram && (
                        <span className="inline-flex items-center gap-1 text-slate-600 font-mono text-[11px]">
                          <Send className="w-2.5 h-2.5 text-indigo-500" />
                          <span>{s.telegram}</span>
                        </span>
                      )}
                    </div>
                    <div className="max-w-xl">
                      <label htmlFor={`roster-category-${s.id}`} className="mb-1 block text-xs font-semibold text-slate-600">
                        Категория ученицы
                      </label>
                      <StudentCategoryField
                        id={`roster-category-${s.id}`}
                        value={categoryDrafts[s.id] ?? s.niche}
                        categories={categories}
                        onChange={category => setCategoryDrafts(previous => ({ ...previous, [s.id]: category }))}
                      />
                      <p className="mt-1 text-[11px] text-slate-400">Своя категория станет доступна всему потоку после сохранения.</p>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 self-end sm:self-start shrink-0">
                  {(categoryDrafts[s.id] ?? s.niche).trim() !== s.niche.trim() && (
                    <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={async () => {
                        const category = categoryDrafts[s.id]?.trim();
                        if (!category || savingCategoryId) return;
                        setSavingCategoryId(s.id);
                        try {
                          await onSaveCategory(s.id, category);
                          setCategoryDrafts(previous => {
                            const next = { ...previous };
                            delete next[s.id];
                            return next;
                          });
                        } finally {
                          setSavingCategoryId(null);
                        }
                      }}
                      disabled={savingCategoryId !== null}
                      className="inline-flex min-h-11 items-center gap-1.5 rounded-xl bg-indigo-600 px-3.5 text-xs font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-wait disabled:opacity-60"
                    >
                      {savingCategoryId === s.id ? 'Сохраняю…' : 'Сохранить категорию'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setCategoryDrafts(previous => {
                        const next = { ...previous };
                        delete next[s.id];
                        return next;
                      })}
                      disabled={savingCategoryId !== null}
                      className="min-h-11 rounded-xl px-3 text-xs font-semibold text-slate-600 hover:bg-slate-100 disabled:opacity-50"
                    >
                      Отмена
                    </button>
                    </div>
                  )}
                  <button
                    type="button"
                    disabled={hasUnsavedCategoryChanges || savingCategoryId !== null}
                    onClick={() => {
                      onClose();
                      onEditStudent(s);
                    }}
                    title={hasUnsavedCategoryChanges ? 'Сначала сохраните или отмените изменение категории' : undefined}
                    className="inline-flex min-h-11 items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 hover:bg-slate-50 text-slate-700 text-xs font-semibold transition cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <Pencil className="w-3.5 h-3.5 text-slate-500" />
                    <span>Настройки</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Safety Note */}
        <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80 flex items-start gap-2.5 text-xs text-slate-600">
          <ShieldAlert className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
          <p className="leading-relaxed">
            Удаление ученицы защищено двойным подтверждением, чтобы исключить случайные нажатия со стилуса или смартфона наставника.
          </p>
        </div>

        {/* Footer */}
        <div className="pt-2 border-t border-slate-100 flex items-center justify-end">
          <button
            type="button"
            onClick={requestClose}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            Закрыть
          </button>
        </div>
      </div>
    </AccessibleModal>
    <ConfirmDialog
      isOpen={isDiscardConfirmationOpen}
      title="Закрыть без сохранения?"
      message="Изменение категории ещё не сохранено. Можно вернуться к списку и сохранить его или отменить правку."
      confirmLabel="Закрыть без сохранения"
      cancelLabel="Вернуться к списку"
      onClose={() => setIsDiscardConfirmationOpen(false)}
      onConfirm={() => {
        setCategoryDrafts({});
        setIsDiscardConfirmationOpen(false);
        onClose();
      }}
    />
    </>
  );
};
