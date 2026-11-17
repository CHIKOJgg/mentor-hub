import { useState, type Dispatch, type SetStateAction } from 'react';
import { X, Send, Video, Check, ExternalLink, Target, CheckCheck } from 'lucide-react';
import type { StudentDiscipline, DailyReport } from '../../types';
import { AccessibleModal } from '../common/AccessibleModal';

interface StudentDossierModalProps {
  student: StudentDiscipline | null;
  inspectingTab: 'profile' | 'reports' | 'zooms';
  setInspectingTab: Dispatch<SetStateAction<'profile' | 'reports' | 'zooms'>>;
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
  const [customMessage, setCustomMessage] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [sentSuccess, setSentSuccess] = useState(false);

  if (!student) return null;

  const targetIncome = student.portalData?.targetIncome ?? student.initialIncome * 2;
  const hasTargetIncome = targetIncome > 0;
  const currSymbol = student.currency === 'RUB' ? '₽' : 'BYN';
  const goalProgress = hasTargetIncome
    ? Math.min(100, Math.round((student.currentIncome / targetIncome) * 100))
    : null;

  const handleSend = () => {
    const text = customMessage.trim();
    if (!text) return;
    setIsSending(true);
    onSendMessage(student, text);
    setSentSuccess(true);
    setIsSending(false);
    setTimeout(() => {
      setSentSuccess(false);
    }, 3000);
  };

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
                  {student.initialIncome.toLocaleString('ru-RU')} {currSymbol}
                </span>
              </div>
              <div className="p-3.5 rounded-xl bg-emerald-50/60 border border-emerald-100">
                <span className="text-[11px] text-emerald-600 block font-medium">Текущий факт</span>
                <span className="text-base font-bold text-emerald-800 font-mono mt-0.5 block">
                  {student.currentIncome.toLocaleString('ru-RU')} {currSymbol}
                </span>
              </div>
              <div className="p-3.5 rounded-xl bg-indigo-50/60 border border-indigo-100">
                <span className="text-[11px] text-indigo-600 block font-medium">Цель на поток (Б)</span>
                <span className="text-base font-bold text-indigo-800 font-mono mt-0.5 block">
                  {hasTargetIncome ? `${targetIncome.toLocaleString('ru-RU')} ${currSymbol}` : 'Не задана'}
                </span>
              </div>
            </div>

            {/* Progress bar to Point B */}
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-slate-700">Прогресс к цели Точки Б:</span>
                <span className="font-mono font-bold text-indigo-600">
                  {goalProgress === null ? 'Задайте цель' : `${goalProgress}%`}
                </span>
              </div>
              <div className="w-full h-2 rounded-full bg-slate-200 overflow-hidden">
                {goalProgress !== null && (
                  <div
                    className="h-full bg-indigo-600 rounded-full transition-all duration-500"
                    style={{ width: `${goalProgress}%` }}
                  />
                )}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-3 rounded-xl border border-slate-100 space-y-1">
                <span className="text-slate-400">Средний чек:</span>
                <div className="text-sm font-bold text-slate-900 font-mono">
                  {student.currentAvgTicket.toLocaleString('ru-RU')} {currSymbol} (было {student.initialAvgTicket.toLocaleString('ru-RU')} {currSymbol})
                </div>
              </div>
              <div className="p-3 rounded-xl border border-slate-100 space-y-1">
                <span className="text-slate-400">Новых клиентов / Рассылок:</span>
                <div className="text-sm font-bold text-slate-900 font-mono">
                  +{student.totalNewClients} клиентов · {student.totalMailings} рассылок
                </div>
              </div>
            </div>

            {/* Task assignment & Telegram Messaging */}
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Target className="w-4 h-4 text-indigo-600" />
                  <span className="text-xs font-bold text-slate-900">Поставить задачу или написать в Telegram</span>
                </div>
                <span className="text-[11px] text-slate-500 font-mono">
                  {student.telegramChatId ? `Чат ID: ${student.telegramChatId}` : 'Авто-привязка бота'}
                </span>
              </div>

              {/* Quick Preset Chips */}
              <div className="flex flex-wrap gap-1.5">
                {[
                  { label: '🎯 1 Reels', text: '🎯 Задача на сегодня: Снять и смонтировать 1 Reels до 19:00' },
                  { label: '🎯 25 рассылок', text: '🎯 Задача на сегодня: Сделать 25 рассылок в Direct потенциальным клиентам' },
                  { label: '🎯 Сторис с кейсом', text: '🎯 Задача на сегодня: Выложить сторителлинг с разбором кейса или отзывом' },
                  { label: '⏰ Напомнить об отчёте', text: `Привет, ${student.fullName}! Не забудь сдать отчёт сегодня до 21:00 🌸` },
                ].map(preset => (
                  <button
                    key={preset.label}
                    type="button"
                    onClick={() => setCustomMessage(preset.text)}
                    className="text-[11px] px-2.5 py-1 rounded-lg bg-white border border-slate-200 text-slate-700 hover:border-indigo-400 hover:text-indigo-600 transition cursor-pointer font-medium"
                  >
                    {preset.label}
                  </button>
                ))}
              </div>

              {/* Input & Send */}
              <div className="flex gap-2">
                <input
                  type="text"
                  value={customMessage}
                  onChange={e => {
                    setCustomMessage(e.target.value);
                    if (sentSuccess) setSentSuccess(false);
                  }}
                  onKeyDown={e => {
                    if (e.key === 'Enter' && customMessage.trim()) {
                      handleSend();
                    }
                  }}
                  placeholder="Введите задачу на день или сообщение ученице..."
                  className="flex-1 text-xs px-3.5 py-2 rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-indigo-500 transition"
                />
                <button
                  type="button"
                  disabled={!customMessage.trim() || isSending}
                  onClick={handleSend}
                  className="px-3.5 py-2 rounded-xl bg-slate-900 text-white text-xs font-semibold hover:bg-slate-800 disabled:opacity-50 disabled:cursor-not-allowed transition cursor-pointer flex items-center gap-1.5 shrink-0"
                >
                  {sentSuccess ? <CheckCheck className="w-3.5 h-3.5 text-emerald-400" /> : <Send className="w-3.5 h-3.5" />}
                  <span>{sentSuccess ? 'Отправлено!' : 'Отправить'}</span>
                </button>
              </div>

              <div className="text-[11px] text-slate-500 bg-indigo-50/50 p-2.5 rounded-lg border border-indigo-100 flex items-start gap-1.5 leading-relaxed">
                <span className="text-indigo-600 font-bold shrink-0">💡</span>
                <span>
                  <strong>В Telegram:</strong> Ксения и Катя могут в любой момент написать прямо в группе ученицы: <code className="text-indigo-700 bg-indigo-100/70 px-1 py-0.5 rounded font-mono font-bold">/задание [текст]</code> или <code className="text-indigo-700 bg-indigo-100/70 px-1 py-0.5 rounded font-mono font-bold">/задача [текст]</code> — бот автоматически примет его и зафиксирует цель в отчёте.
                </span>
              </div>
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
                      <span>Продажи: <strong className="text-emerald-700">{r.salesAmount.toLocaleString('ru-RU')} {currSymbol}</strong></span>
                      <span>·</span>
                      <span>Клиенты: +{r.newClientsCount}</span>
                      <span>·</span>
                      <span>Рассылки: {r.mailingsCount} шт</span>
                    </div>

                    {r.reflectionSummary && (
                      <div className="text-xs text-slate-600 bg-white p-2.5 rounded-lg border border-slate-100 space-y-1">
                        <div><strong>Действия:</strong> {r.reflectionSummary.actions}</div>
                        {r.reflectionSummary.questionToMentor &&
                         r.reflectionSummary.questionToMentor !== 'null' &&
                         r.reflectionSummary.questionToMentor !== 'undefined' &&
                         r.reflectionSummary.questionToMentor !== 'None' && (
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

            <div className="space-y-2 max-h-96 overflow-y-auto pr-1">
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
