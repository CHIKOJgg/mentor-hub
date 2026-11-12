import type React from 'react';
import { X, Send, Video, Check, ExternalLink } from 'lucide-react';
import type { StudentDiscipline, DailyReport } from '../../types';
import { AccessibleModal } from '../common/AccessibleModal';

interface StudentDossierModalProps {
  student: StudentDiscipline | null;
  inspectingTab: 'profile' | 'reports' | 'zooms';
  setInspectingTab: React.Dispatch<React.SetStateAction<'profile' | 'reports' | 'zooms'>>;
  allReports: DailyReport[];
  onClose: () => void;
  onSendMessage: (student: StudentDiscipline, message: string) => void;
  onManageZooms: (student: StudentDiscipline) => void;
  onToggleZoomCompleted: (studentId: string, callNumber: number) => void;
}

export const StudentDossierModal = ({
  student,
  inspectingTab,
  setInspectingTab,
  allReports,
  onClose,
  onSendMessage,
  onManageZooms,
  onToggleZoomCompleted,
}: StudentDossierModalProps) => {
  if (!student) return null;

  return (
    <AccessibleModal title={`Досье ученицы ${student.fullName}`} onClose={onClose}>
      <div className="bg-white rounded-2xl max-w-2xl w-full p-5 border border-slate-200 space-y-5 shadow-2xl sm:p-6">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-100">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-full bg-slate-900 text-white flex items-center justify-center text-sm font-bold">
              {student.avatarText}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-slate-900">{student.fullName}</h3>
              </div>
              <p className="text-xs text-slate-500 mt-0.5">
                {student.niche} · Онлайн
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            type="button"
            aria-label="Закрыть досье"
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Dossier Tabs */}
        <div className="flex items-center gap-1.5 p-1 bg-slate-100/70 rounded-xl">
          <button
            onClick={() => setInspectingTab('profile')}
            className={`flex-1 py-1.5 text-xs font-semibold rounded-lg transition cursor-pointer ${
              inspectingTab === 'profile' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Профиль и цели
          </button>
          <button
            onClick={() => setInspectingTab('reports')}
            className={`flex-1 py-1.5 text-xs font-semibold rounded-lg transition cursor-pointer ${
              inspectingTab === 'reports' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            История отчётов ({allReports.filter(r => r.studentId === student.id).length})
          </button>
          <button
            onClick={() => setInspectingTab('zooms')}
            className={`flex-1 py-1.5 text-xs font-semibold rounded-lg transition cursor-pointer ${
              inspectingTab === 'zooms' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Zoom-созвоны ({student.zoomSessions?.length || 0})
          </button>
        </div>

        {/* TAB: PROFILE & FINANCIALS */}
        {inspectingTab === 'profile' && (
          <div className="space-y-4">
            <div className="grid grid-cols-3 gap-3">
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100">
                <span className="text-[11px] text-slate-400 block font-medium">Точка А (старт)</span>
                <span className="text-base font-bold text-slate-900 font-mono mt-0.5 block">
                  {student.initialIncome.toLocaleString('ru-RU')} ₽
                </span>
              </div>
              <div className="p-3.5 rounded-xl bg-emerald-50/60 border border-emerald-100">
                <span className="text-[11px] text-emerald-600 block font-medium">Текущий факт</span>
                <span className="text-base font-bold text-emerald-800 font-mono mt-0.5 block">
                  {student.currentIncome.toLocaleString('ru-RU')} ₽
                </span>
              </div>
              <div className="p-3.5 rounded-xl bg-indigo-50/60 border border-indigo-100">
                <span className="text-[11px] text-indigo-600 block font-medium">Цель на поток (Б)</span>
                <span className="text-base font-bold text-indigo-800 font-mono mt-0.5 block">
                  {(student.portalData?.targetIncome || student.initialIncome * 2).toLocaleString('ru-RU')} ₽
                </span>
              </div>
            </div>

            {/* Progress bar to Point B */}
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-slate-700">Прогресс к цели Точки Б:</span>
                <span className="font-mono font-bold text-indigo-600">
                  {Math.min(100, Math.round((student.currentIncome / (student.portalData?.targetIncome || student.initialIncome * 2 || 1)) * 100))}%
                </span>
              </div>
              <div className="w-full h-2 rounded-full bg-slate-200 overflow-hidden">
                <div
                  className="h-full bg-indigo-600 rounded-full transition-all duration-500"
                  style={{
                    width: `${Math.min(100, Math.round((student.currentIncome / (student.portalData?.targetIncome || student.initialIncome * 2 || 1)) * 100))}%`
                  }}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-3 rounded-xl border border-slate-100 space-y-1">
                <span className="text-slate-400">Средний чек:</span>
                <div className="text-sm font-bold text-slate-900 font-mono">
                  {student.currentAvgTicket.toLocaleString('ru-RU')} ₽ (было {student.initialAvgTicket.toLocaleString('ru-RU')} ₽)
                </div>
              </div>
              <div className="p-3 rounded-xl border border-slate-100 space-y-1">
                <span className="text-slate-400">Новых клиентов / Рассылок:</span>
                <div className="text-sm font-bold text-slate-900 font-mono">
                  +{student.totalNewClients} клиентов · {student.totalMailings} рассылок
                </div>
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between text-xs">
              <div>
                <span className="font-semibold text-slate-900 block">Telegram мини-группа:</span>
                <span className="text-slate-500">{student.telegramChatId ? `Chat ID: ${student.telegramChatId}` : 'Авто-привязка через бота'}</span>
              </div>
              <button
                onClick={() => onSendMessage(student, `Привет, ${student.fullName}! Как продвигается выполнение заданий?`)}
                className="px-3 py-1.5 rounded-lg bg-slate-900 text-white text-xs font-semibold hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
              >
                <Send className="w-3 h-3" />
                <span>Написать</span>
              </button>
            </div>
          </div>
        )}

        {/* TAB: SUBMITTED REPORTS HISTORY */}
        {inspectingTab === 'reports' && (
          <div className="space-y-3 max-h-96 overflow-y-auto">
            {allReports.filter(r => r.studentId === student.id).length === 0 ? (
              <div className="text-center py-12 text-xs text-slate-400">
                Ученица пока не сдала ни одного отчёта
              </div>
            ) : (
              allReports
                .filter(r => r.studentId === student.id)
                .map(r => (
                  <div key={r.id} className="p-4 rounded-xl border border-slate-100 bg-slate-50/50 space-y-2.5">
                    <div className="flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-slate-900">{r.reportDate}</span>
                        <span className={`px-2 py-0.5 rounded-md font-semibold text-[10px] ${
                          r.status === 'APPROVED' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                        }`}>
                          {r.status === 'APPROVED' ? 'Одобрен' : 'На проверке'}
                        </span>
                      </div>
                      <span className="text-slate-400 font-mono">{r.submittedAt}</span>
                    </div>

                    <div className="flex items-center gap-3 text-xs font-semibold text-slate-700">
                      <span>Продажи: <strong className="text-emerald-700">{r.salesAmount.toLocaleString('ru-RU')} ₽</strong></span>
                      <span>·</span>
                      <span>Клиенты: +{r.newClientsCount}</span>
                      <span>·</span>
                      <span>Рассылки: {r.mailingsCount} шт</span>
                    </div>

                    {r.reflectionSummary && (
                      <div className="text-xs text-slate-600 bg-white p-2.5 rounded-lg border border-slate-100 space-y-1">
                        <div><strong>Действия:</strong> {r.reflectionSummary.actions}</div>
                        {r.reflectionSummary.questionToMentor && (
                          <div className="text-indigo-900 font-medium"><strong>Вопрос наставнику:</strong> {r.reflectionSummary.questionToMentor}</div>
                        )}
                      </div>
                    )}

                    {r.mentorReply && (
                      <div className="text-xs bg-emerald-50 text-emerald-950 p-2.5 rounded-lg border border-emerald-100">
                        <strong>Ответ наставника:</strong> {r.mentorReply}
                      </div>
                    )}
                  </div>
                ))
            )}
          </div>
        )}

        {/* TAB: ZOOM SESSIONS */}
        {inspectingTab === 'zooms' && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">Список назначенных и проведённых сессий</span>
              <button
                onClick={() => {
                  onManageZooms(student);
                  onClose();
                }}
                className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 transition cursor-pointer flex items-center gap-1"
              >
                <Video className="w-3.5 h-3.5" />
                <span>Управление созвонами</span>
              </button>
            </div>

            <div className="space-y-2">
              {(student.zoomSessions || []).map(sess => (
                <div
                  key={sess.callNumber}
                  className="p-3.5 rounded-xl border border-slate-100 bg-slate-50/50 flex items-center justify-between gap-3"
                >
                  <div className="flex items-center gap-2.5">
                    <button
                      onClick={() => onToggleZoomCompleted(student.id, sess.callNumber)}
                      className={`w-5 h-5 rounded-md border flex items-center justify-center transition cursor-pointer ${
                        sess.isCompleted ? 'bg-emerald-600 border-emerald-600 text-white' : 'border-slate-300 bg-white'
                      }`}
                    >
                      {sess.isCompleted && <Check className="w-3.5 h-3.5" />}
                    </button>
                    <div>
                      <span className={`text-xs font-bold block ${sess.isCompleted ? 'line-through text-slate-400' : 'text-slate-900'}`}>
                        {sess.title}
                      </span>
                      <span className="text-[11px] text-slate-400">
                        {sess.date ? `Назначен: ${sess.date}` : 'Дата не назначена'}
                        {sess.keyTakeaway ? ` · ${sess.keyTakeaway}` : ''}
                      </span>
                    </div>
                  </div>

                  {(sess.zoomLink || sess.recordingUrl) && (
                    <a
                      href={sess.zoomLink || sess.recordingUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="px-2.5 py-1 rounded-lg bg-white border border-slate-200 text-slate-700 text-xs font-semibold hover:bg-slate-50 transition cursor-pointer flex items-center gap-1 shrink-0"
                    >
                      <span>Zoom</span>
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="pt-3 border-t border-slate-100 flex items-center justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            Закрыть досье
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
};
