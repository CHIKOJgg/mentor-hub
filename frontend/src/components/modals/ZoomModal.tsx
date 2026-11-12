import { useState } from 'react';
import type React from 'react';
import { X, Check, ExternalLink, Plus, Copy, Sparkles, Clock, ShieldCheck } from 'lucide-react';
import type { StudentDiscipline } from '../../types';
import { AccessibleModal } from '../common/AccessibleModal';
import { useModalDismiss } from '../common/useModalDismiss';

export interface ZoomFormState {
  title: string;
  scheduledAt: string;
  zoomUrl: string;
  meetingNotes: string;
  linkSentAt?: string;
  evidenceUrl?: string;
}

interface ZoomModalProps {
  student: StudentDiscipline | null;
  isAddingZoom: boolean;
  setIsAddingZoom: (val: boolean) => void;
  zoomForm: ZoomFormState;
  setZoomForm: React.Dispatch<React.SetStateAction<ZoomFormState>>;
  onToggleCompleted: (studentId: string, callNumber: number) => void;
  onAddZoomSession: (studentId: string) => Promise<void> | void;
  onClose: () => void;
}

export const ZoomModal = ({
  student,
  isAddingZoom,
  setIsAddingZoom,
  zoomForm,
  setZoomForm,
  onToggleCompleted,
  onAddZoomSession,
  onClose,
}: ZoomModalProps) => {
  const [copiedLink, setCopiedLink] = useState<string | null>(null);

  const formatLinkSentAt = (value?: string) => {
    if (!value) return '';
    const date = new Date(value);
    return /[Tz]/i.test(value) && !Number.isNaN(date.getTime())
      ? date.toLocaleString('ru-RU', { timeZone: 'Europe/Minsk', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
      : value;
  };

  const hasUnsavedZoom = isAddingZoom && (
    zoomForm.title.trim() !== 'Разбор оффера и чека'
    || Boolean(zoomForm.scheduledAt.trim() || zoomForm.zoomUrl.trim() || zoomForm.meetingNotes.trim() || zoomForm.evidenceUrl?.trim() || zoomForm.linkSentAt?.trim())
  );
  const requestClose = useModalDismiss(onClose, hasUnsavedZoom);
  if (!student) return null;
  const contractQuota = student.contractZoomQuota || 8;
  const sessions = student.zoomSessions || [];
  const completedCount = sessions.filter(s => s.isCompleted).length || student.completedZoomsCount || 0;
  const isOverachieved = completedCount > contractQuota;

  const copyToClipboard = (text: string) => {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        setCopiedLink(text);
        setTimeout(() => setCopiedLink(null), 2000);
      }).catch(() => {});
    }
  };

  return (
    <AccessibleModal title="Реестр консультаций и Zoom-созвонов" onClose={requestClose}>
      <div className="bg-white rounded-2xl max-w-2xl w-full p-5 border border-slate-200 space-y-5 shadow-2xl sm:p-6">
        {/* Header */}
        <div className="flex items-start justify-between pb-3 border-b border-slate-100">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-base sm:text-lg font-bold text-slate-900">Реестр консультаций и Zoom-созвонов</h3>
              <span className="text-[11px] font-semibold bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded-md border border-indigo-100 flex items-center gap-1">
                <ShieldCheck className="w-3 h-3 text-indigo-600" />
                <span>Юр. защита договора</span>
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-1">
              {student.fullName} · {student.niche}
            </p>
          </div>
          <button
            onClick={requestClose}
            type="button"
            aria-label="Закрыть реестр созвонов"
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Contract Quota & Legal Fulfillment Banner */}
        <div className="p-3.5 rounded-xl border bg-slate-50 border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-sm shrink-0 ${
              isOverachieved
                ? 'bg-emerald-100 text-emerald-800'
                : completedCount >= contractQuota
                ? 'bg-emerald-50 text-emerald-700'
                : 'bg-indigo-50 text-indigo-700'
            }`}>
              {completedCount}/{contractQuota}
            </div>
            <div>
              <div className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                <span>Проведено консультаций:</span>
                <span className="font-mono text-indigo-700">{completedCount} из {contractQuota}</span>
                <span className="text-slate-400 font-normal">(по договору)</span>
              </div>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {isOverachieved
                  ? `Обязательства по договору перевыполнены на ${completedCount - contractQuota} созвона`
                  : completedCount === contractQuota
                  ? 'Обязательства по договору выполнены в полном объёме (100%)'
                  : `До выполнения нормы договора осталось провести: ${contractQuota - completedCount} созвона`}
              </p>
            </div>
          </div>

          {isOverachieved && (
            <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-700 bg-emerald-50 px-3 py-1.5 rounded-xl border border-emerald-200 shrink-0">
              <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
              <span>План перевыполнен!</span>
            </span>
          )}
        </div>

        {/* List of current Zoom sessions with full metadata & visible URLs */}
        <div className="space-y-3">
          <div className="flex items-center justify-between text-xs font-semibold text-slate-500">
            <span>Список консультаций (№, этап, время отправки, ссылка):</span>
            <span className="text-[11px] font-normal text-slate-400">{sessions.length} записей</span>
          </div>

          {sessions.length === 0 ? (
            <div className="text-center py-8 text-xs text-slate-400 border border-dashed border-slate-200 rounded-xl">
              Созвоны еще не запланированы. Нажмите кнопку ниже, чтобы зафиксировать первый созвон.
            </div>
          ) : (
            <div className="space-y-2.5 max-h-[420px] overflow-y-auto pr-1">
              {sessions.map(sess => (
                <div
                  key={sess.callNumber}
                  className={`p-3.5 rounded-xl border transition flex flex-col gap-2.5 ${
                    sess.isCompleted
                      ? 'border-emerald-200/80 bg-emerald-50/20'
                      : 'border-slate-200 bg-white hover:border-slate-300'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-start gap-2.5 min-w-0">
                      <button
                        onClick={() => onToggleCompleted(student.id, sess.callNumber)}
                        title={sess.isCompleted ? 'Отметить как не проведённый' : 'Зачесть созвон проведённым'}
                        className={`w-5 h-5 rounded-md border flex items-center justify-center transition cursor-pointer mt-0.5 shrink-0 ${
                          sess.isCompleted
                            ? 'bg-emerald-600 border-emerald-600 text-white'
                            : 'border-slate-300 bg-white hover:border-slate-400'
                        }`}
                      >
                        {sess.isCompleted && <Check className="w-3.5 h-3.5" />}
                      </button>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-[11px] font-mono font-bold text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                            № {sess.callNumber}
                          </span>
                          <span className={`text-xs font-bold ${sess.isCompleted ? 'line-through text-slate-400' : 'text-slate-900'}`}>
                            {sess.title}
                          </span>
                          {sess.callNumber <= contractQuota ? (
                            <span className="text-[10px] font-semibold text-slate-400 bg-slate-50 px-1.5 py-0.2 rounded border border-slate-100">
                              в рамках 8
                            </span>
                          ) : (
                            <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-1.5 py-0.2 rounded border border-emerald-200">
                              сверх нормы
                            </span>
                          )}
                        </div>

                        <div className="flex items-center gap-2 text-[11px] text-slate-500 mt-1 flex-wrap">
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3 text-slate-400" />
                            <span>{sess.date || 'Дата не назначена'}</span>
                          </span>
                          {sess.linkSentAt && (
                            <span className="text-slate-600 font-medium">
                              · ссылка отправлена в чат: <strong>{formatLinkSentAt(sess.linkSentAt)}</strong>
                            </span>
                          )}
                          {sess.keyTakeaway && (
                            <span className="text-slate-500 truncate max-w-xs">
                              · {sess.keyTakeaway}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full border ${
                        sess.isCompleted
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : 'bg-amber-50 text-amber-700 border-amber-200'
                      }`}>
                        {sess.isCompleted ? 'Проведён' : 'Запланирован'}
                      </span>
                    </div>
                  </div>

                  {/* Visible Link Strip (Key Legal Proof Requirement from Katya) */}
                  {(sess.zoomLink || sess.recordingUrl) ? (
                    <div className="flex items-center justify-between gap-2 p-2 rounded-lg bg-slate-50 border border-slate-100 text-xs">
                      <div className="flex items-center gap-1.5 min-w-0">
                        <span className="text-[10px] uppercase font-bold text-slate-400 shrink-0">Ссылка:</span>
                        <a
                          href={sess.zoomLink || sess.recordingUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="font-mono text-[11px] text-indigo-700 hover:underline truncate"
                          title={sess.zoomLink || sess.recordingUrl}
                        >
                          {sess.zoomLink || sess.recordingUrl}
                        </a>
                      </div>
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          type="button"
                          onClick={() => copyToClipboard(sess.zoomLink || sess.recordingUrl || '')}
                          className="p-1 rounded text-slate-500 hover:text-slate-800 hover:bg-slate-200/60 transition cursor-pointer"
                          title="Скопировать ссылку"
                        >
                          {copiedLink === (sess.zoomLink || sess.recordingUrl) ? (
                            <Check className="w-3.5 h-3.5 text-emerald-600" />
                          ) : (
                            <Copy className="w-3.5 h-3.5" />
                          )}
                        </button>
                        <a
                          href={sess.zoomLink || sess.recordingUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="p-1 rounded text-slate-500 hover:text-slate-800 hover:bg-slate-200/60 transition cursor-pointer"
                          title="Открыть встречу"
                        >
                          <ExternalLink className="w-3.5 h-3.5" />
                        </a>
                      </div>
                    </div>
                  ) : (
                    <div className="text-[11px] text-slate-400 italic px-2 py-1 bg-slate-50/50 rounded border border-dashed border-slate-200">
                      Ссылка на созвон ещё не внесена
                    </div>
                  )}
                  {(sess.evidenceUrl || sess.sourceMessageId) && (
                    <div className="flex flex-wrap items-center gap-2 text-[11px]">
                      {sess.evidenceUrl ? (
                        <a
                          href={sess.evidenceUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 font-semibold text-emerald-700 hover:underline"
                        >
                          <ShieldCheck className="w-3.5 h-3.5" />
                          <span>Подтверждение сообщения / скриншота</span>
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      ) : (
                        <span className="text-slate-500">
                          Сообщение Telegram #{sess.sourceMessageId}{sess.sourceChatId ? ` · чат ${sess.sourceChatId}` : ''}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Schedule New Call Form Toggle */}
        {!isAddingZoom ? (
          <button
            onClick={() => setIsAddingZoom(true)}
            className="w-full py-2.5 rounded-xl border-2 border-dashed border-slate-200 text-slate-600 hover:border-slate-400 hover:text-slate-900 text-xs font-semibold transition cursor-pointer flex items-center justify-center gap-1.5"
          >
            <Plus className="w-4 h-4" />
            <span>Запланировать / внести созвон (консультацию)</span>
          </button>
        ) : (
          <div className="p-4 rounded-xl border border-slate-200 bg-slate-50/80 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-900">Новая консультация / созвон</span>
              <button
                onClick={() => setIsAddingZoom(false)}
                className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer"
              >
                Скрыть
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <div>
                <label className="text-[11px] font-semibold text-slate-600 block mb-1">Название этапа / темы:</label>
                <input
                  type="text"
                  value={zoomForm.title}
                  onChange={e => setZoomForm({ ...zoomForm, title: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white"
                  placeholder="Например: Аудит воронки и разбор оффера"
                />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <div>
                  <label className="text-[11px] font-semibold text-slate-600 block mb-1">Дата консультации:</label>
                  <input
                    type="text"
                    value={zoomForm.scheduledAt}
                    onChange={e => setZoomForm({ ...zoomForm, scheduledAt: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white"
                    placeholder="12.11 в 15:00"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-semibold text-slate-600 block mb-1">Время отправки ссылки в чат:</label>
                  <input
                    type="text"
                    value={zoomForm.linkSentAt || ''}
                    onChange={e => setZoomForm({ ...zoomForm, linkSentAt: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white"
                    placeholder="14:50 (время отправки в TG)"
                  />
                </div>
              </div>
              <div>
                <label className="text-[11px] font-semibold text-slate-600 block mb-1">Ссылка на созвон (Zoom / Meet / Телемост):</label>
                <input
                  type="text"
                  value={zoomForm.zoomUrl}
                  onChange={e => setZoomForm({ ...zoomForm, zoomUrl: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white font-mono text-[11px]"
                  placeholder="https://us05web.zoom.us/j/... или https://telemost.yandex.ru/..."
                />
              </div>
              <div>
                <label className="text-[11px] font-semibold text-slate-600 block mb-1">Подтверждение (ссылка на Telegram-сообщение или скриншот):</label>
                <input
                  type="url"
                  value={zoomForm.evidenceUrl || ''}
                  onChange={e => setZoomForm({ ...zoomForm, evidenceUrl: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white font-mono text-[11px]"
                  placeholder="https://t.me/... или ссылка на скриншот"
                />
              </div>
              <div>
                <label className="text-[11px] font-semibold text-slate-600 block mb-1">Заметки наставника / подтверждение:</label>
                <input
                  type="text"
                  value={zoomForm.meetingNotes}
                  onChange={e => setZoomForm({ ...zoomForm, meetingNotes: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white"
                  placeholder="Фокус созвона, ключевые договорённости..."
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setIsAddingZoom(false)}
                className="px-3 py-1.5 text-xs text-slate-600 hover:text-slate-800 cursor-pointer"
              >
                Отмена
              </button>
              <button
                onClick={() => void onAddZoomSession(student.id)}
                className="px-3 py-1.5 bg-slate-900 text-white text-xs font-semibold rounded-lg hover:bg-slate-800 transition cursor-pointer"
              >
                Сохранить в реестр
              </button>
            </div>
          </div>
        )}

        <div className="pt-3 border-t border-slate-100 flex items-center justify-end">
          <button
            onClick={requestClose}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            Готово
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
};
