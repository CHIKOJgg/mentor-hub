import { useCallback, useState } from 'react';
import type React from 'react';
import { X, Save, Plus, Trash2 } from 'lucide-react';
import type { StudentDiscipline, ZoomSessionRecord } from '../../types';
import { AccessibleModal } from '../common/AccessibleModal';
import { StudentCategoryField } from '../common/StudentCategoryField';
import { useModalDismiss } from '../common/useModalDismiss';

interface EditStudentModalProps {
  student: StudentDiscipline | null;
  categories: string[];
  setEditingStudent: React.Dispatch<React.SetStateAction<StudentDiscipline | null>>;
  onClose: () => void;
  onSave: (student: StudentDiscipline) => Promise<void> | void;
  addEditingZoomSession: () => void;
  removeEditingZoomSession: (index: number) => void;
  updateEditingZoomSession: (index: number, changes: Partial<ZoomSessionRecord>) => void;
  onDelete?: (studentId: string, fullName: string) => void;
}

export const EditStudentModal = ({
  student,
  categories,
  setEditingStudent,
  onClose,
  onSave,
  addEditingZoomSession,
  removeEditingZoomSession,
  updateEditingZoomSession,
  onDelete,
}: EditStudentModalProps) => {
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const closeDialog = useCallback(() => { setHasUnsavedChanges(false); onClose(); }, [onClose]);
  const requestClose = useModalDismiss(closeDialog, hasUnsavedChanges);

  if (!student) return null;
  const updateEditingStudent = (updated: StudentDiscipline) => {
    setHasUnsavedChanges(true);
    setEditingStudent(updated);
  };

  return (
    <AccessibleModal title={`Профиль и созвоны: ${student.fullName}`} onClose={requestClose}>
      <div className="bg-white rounded-2xl max-w-lg w-full p-5 border border-slate-200 space-y-4 shadow-xl sm:p-6">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base font-bold text-slate-900">Профиль и созвоны</h3>
            <p className="text-xs text-slate-500 mt-0.5">{student.fullName}</p>
          </div>
          <button
            onClick={requestClose}
            type="button"
            aria-label="Закрыть профиль"
            className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-3.5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Имя и фамилия:</label>
              <input
                type="text"
                value={student.fullName}
                onChange={e => updateEditingStudent({ ...student, fullName: e.target.value })}
                className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label htmlFor={`student-category-${student.id}`} className="text-xs font-semibold text-slate-600 block mb-1">Категория ученицы:</label>
              <StudentCategoryField
                id={`student-category-${student.id}`}
                value={student.niche}
                categories={categories}
                onChange={niche => updateEditingStudent({ ...student, niche })}
              />
            </div>
          </div>

          {/* Telegram Connection Card */}
          <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-3.5 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-700">Рабочая Telegram-группа</span>
              {student.telegramChatId ? (
                <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                  🟢 Подключена
                </span>
              ) : (
                <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800">
                  <span className="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
                  ⏳ Ожидает группу
                </span>
              )}
            </div>
            <p className="text-xs text-slate-500 leading-relaxed">
              {student.telegramChatId
                ? `Телеграм-бот подключен к группе (ID: ${student.telegramChatId}). Все ответы ментора из дашборда отправляются в эту группу.`
                : `Чтобы привязать группу, добавьте бота в группу с ученицей (или отправьте в группе команду /bind ${student.fullName.split(' ')[0]}).`}
            </p>
            {student.telegramChatId && (
              <button
                type="button"
                onClick={() => updateEditingStudent({ ...student, telegramChatId: undefined })}
                className="text-xs text-rose-600 hover:text-rose-700 font-medium cursor-pointer"
              >
                Отвязать группу
              </button>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Дата старта:</label>
              <input
                type="date"
                value={student.startDate ?? ''}
                onChange={event => updateEditingStudent({ ...student, startDate: event.target.value })}
                className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
              />
            </div>
            <div className="sm:col-span-2">
              <label className="text-xs font-semibold text-slate-600 block mb-1">Причина паузы:</label>
              <input
                type="text"
                value={student.pauseReason ?? ''}
                onChange={event => updateEditingStudent({ ...student, pauseReason: event.target.value || undefined })}
                className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
              />
            </div>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={student.isOnPause} onChange={event => updateEditingStudent({ ...student, isOnPause: event.target.checked })} />
              На паузе
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" checked={student.isActive ?? true} onChange={event => updateEditingStudent({ ...student, isActive: event.target.checked })} />
              Активный профиль
            </label>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Точка А (старт, ₽):</label>
              <input
                type="number"
                value={student.initialIncome}
                onChange={e => updateEditingStudent({ ...student, initialIncome: Number(e.target.value) || 0 })}
                className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Точка Б (факт, ₽):</label>
              <input
                type="number"
                value={student.currentIncome}
                onChange={e => updateEditingStudent({ ...student, currentIncome: Number(e.target.value) || 0 })}
                className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500 font-bold text-emerald-600"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Средний чек на старте (₽):</label>
              <input
                type="number"
                value={student.initialAvgTicket}
                onChange={e => updateEditingStudent({ ...student, initialAvgTicket: Number(e.target.value) || 0 })}
                className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Текущий средний чек (₽):</label>
              <input
                type="number"
                value={student.currentAvgTicket}
                onChange={e => updateEditingStudent({ ...student, currentAvgTicket: Number(e.target.value) || 0 })}
                className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500 font-bold"
              />
            </div>
          </div>

          <section className="space-y-3 border-t border-slate-100 pt-4" aria-labelledby="zoom-session-settings-title">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h4 id="zoom-session-settings-title" className="text-sm font-semibold text-slate-900">Этапы созвонов</h4>
                <span className="sr-only">Количество этапов не ограничено</span>
              </div>
              <button
                type="button"
                onClick={addEditingZoomSession}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 text-xs font-semibold text-slate-800 hover:bg-slate-100 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Добавить этап</span>
              </button>
            </div>

            {(student.zoomSessions ?? []).length === 0 ? (
              <p className="rounded-xl border border-dashed border-slate-300 px-4 py-6 text-center text-xs text-slate-500">
                Этапов пока нет. Добавьте первый этап сопровождения.
              </p>
            ) : (
              <div className="space-y-3">
                {(student.zoomSessions ?? []).map((session, index) => (
                  <div key={`${student.id}-editing-session-${index}`} className="space-y-3 rounded-xl border border-slate-200 p-4">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-xs font-semibold uppercase tracking-wide">Этап {index + 1}</span>
                      <button
                        type="button"
                        onClick={() => removeEditingZoomSession(index)}
                        aria-label={`Удалить этап ${index + 1}`}
                        disabled={index < (student.contractZoomQuota ?? 8)}
                        title={index < (student.contractZoomQuota ?? 8) ? 'Базовые 8 консультаций по договору нельзя удалить' : 'Удалить дополнительный этап'}
                        className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 cursor-pointer disabled:cursor-not-allowed disabled:opacity-35"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="sm:col-span-2">
                        <label className="mb-1 block text-xs font-semibold text-slate-600">Название этапа</label>
                        <input
                          type="text"
                          value={session.title}
                          onChange={e => updateEditingZoomSession(index, { title: e.target.value })}
                          placeholder="Например: Разбор стратегии"
                          className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-semibold text-slate-600">Дата и время</label>
                        <input
                          type="text"
                          value={session.date}
                          onChange={e => updateEditingZoomSession(index, { date: e.target.value, scheduledAt: undefined })}
                          placeholder="Например: 12.11 в 19:00"
                          className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-semibold text-slate-600">Статус</label>
                        <select
                          value={session.isCompleted ? 'completed' : 'planned'}
                          onChange={e => updateEditingZoomSession(index, { isCompleted: e.target.value === 'completed' })}
                          className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        >
                          <option value="planned">Запланирован</option>
                          <option value="completed">Проведен</option>
                        </select>
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-semibold text-slate-600">Ссылка на созвон</label>
                        <input
                          type="url"
                          value={session.zoomLink ?? ''}
                          onChange={e => updateEditingZoomSession(index, { zoomLink: e.target.value || undefined })}
                          placeholder="https://…"
                          className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-semibold text-slate-600">Ссылка на запись</label>
                        <input
                          type="url"
                          value={session.recordingUrl ?? ''}
                          onChange={e => updateEditingZoomSession(index, { recordingUrl: e.target.value || undefined })}
                          placeholder="https://…"
                          className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-semibold text-slate-600">Когда ссылка отправлена в Telegram</label>
                        <input
                          type="text"
                          value={session.linkSentAt ?? ''}
                          onChange={e => updateEditingZoomSession(index, { linkSentAt: e.target.value || undefined })}
                          placeholder="11.11.2026 в 14:50"
                          className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-semibold text-slate-600">Подтверждение / ссылка на сообщение</label>
                        <input
                          type="url"
                          value={session.evidenceUrl ?? ''}
                          onChange={e => updateEditingZoomSession(index, { evidenceUrl: e.target.value || undefined })}
                          placeholder="https://t.me/..."
                          className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        />
                      </div>
                      <div className="sm:col-span-2">
                        <label className="mb-1 block text-xs font-semibold text-slate-600">Итог и заметки</label>
                        <textarea
                          rows={2}
                          value={session.keyTakeaway ?? ''}
                          onChange={e => updateEditingZoomSession(index, { keyTakeaway: e.target.value || undefined })}
                          placeholder="Ключевые договоренности и итоги этапа"
                          className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Финансовая цель потока (₽):</label>
            <input
              type="number"
              value={student.portalData?.targetIncome || 100000}
              onChange={e => {
                const val = Number(e.target.value) || 100000;
                updateEditingStudent({
                  ...student,
                  portalData: student.portalData
                    ? { ...student.portalData, targetIncome: val }
                    : {
                        targetIncome: val,
                        currentStreakDays: 2,
                        dailyChecklist: { contentSubmitted: false, mailingsDone: 8, mailingsTarget: 15, reflectionDone: false },
                        todayDeadline: '21:00',
                        scripts: [],
                        zoomSessions: [],
                      },
                });
              }}
              className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
            />
          </div>
        </div>

        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2 pt-3 border-t border-slate-100">
          <div>
            {onDelete && (
              <button
                type="button"
                onClick={() => {
                  const id = student.id;
                  const name = student.fullName;
                  closeDialog();
                  onDelete(id, name);
                }}
                className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-rose-700 hover:bg-rose-50 border border-rose-200 rounded-xl transition cursor-pointer"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Удалить ученицу из потока</span>
              </button>
            )}
          </div>
          <div className="flex items-center justify-end gap-2">
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
                void onSave(student);
                closeDialog();
              }}
              className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
            >
              <Save className="w-3.5 h-3.5" />
              <span>Сохранить профиль</span>
            </button>
          </div>
        </div>
      </div>
    </AccessibleModal>
  );
};
