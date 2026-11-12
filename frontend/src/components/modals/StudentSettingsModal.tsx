import type React from 'react';
import { X, Plus, Pencil, Send, Coffee, Check, ShieldAlert } from 'lucide-react';
import type { StudentDiscipline } from '../../types';

interface StudentSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  students: StudentDiscipline[];
  onEditStudent: (student: StudentDiscipline) => void;
  onAddStudent: () => void;
}

export const StudentSettingsModal: React.FC<StudentSettingsModalProps> = ({
  isOpen,
  onClose,
  students,
  onEditStudent,
  onAddStudent,
}) => {
  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4 overflow-y-auto animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl max-w-2xl w-full p-6 border border-slate-200 space-y-5 my-8 shadow-2xl"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-start justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base sm:text-lg font-bold text-slate-900">Управление составом учениц потока</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Настройки профилей, редактирование ниши и защищённое исключение из трекинга
            </p>
          </div>
          <button
            onClick={onClose}
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
            onClick={() => {
              onClose();
              onAddStudent();
            }}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold transition cursor-pointer shrink-0 shadow-xs"
          >
            <Plus className="w-4 h-4" />
            <span>Добавить ученицу</span>
          </button>
        </div>

        {/* Students List */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs font-semibold text-slate-500 px-1">
            <span>Список учениц ({students.length}):</span>
            <span className="text-[11px] text-slate-400">Нажмите «Редактировать» для изменения параметров</span>
          </div>

          <div className="divide-y divide-slate-100 max-h-[380px] overflow-y-auto border border-slate-200 rounded-xl">
            {students.map(s => (
              <div
                key={s.id}
                className="p-3.5 hover:bg-slate-50/80 transition flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-10 h-10 rounded-full bg-slate-100 text-slate-800 flex items-center justify-center text-xs font-bold shrink-0">
                    {s.avatarText}
                  </div>
                  <div className="min-w-0">
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
                    <div className="text-xs text-slate-500 mt-0.5 flex items-center gap-2 flex-wrap">
                      <span>{s.niche}</span>
                      {s.telegram && (
                        <span className="inline-flex items-center gap-1 text-slate-600 font-mono text-[11px]">
                          <Send className="w-2.5 h-2.5 text-indigo-500" />
                          <span>{s.telegram}</span>
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                  <button
                    type="button"
                    onClick={() => {
                      onClose();
                      onEditStudent(s);
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold transition cursor-pointer"
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
            onClick={onClose}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            Закрыть
          </button>
        </div>
      </div>
    </div>
  );
};
