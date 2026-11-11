import type React from 'react';
import { X, Check, ExternalLink, Plus } from 'lucide-react';
import type { StudentDiscipline } from '../../types';

export interface ZoomFormState {
  title: string;
  scheduledAt: string;
  zoomUrl: string;
  meetingNotes: string;
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
  if (!student) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4 overflow-y-auto animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl max-w-xl w-full p-6 border border-slate-200 space-y-5 my-8 shadow-2xl"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base font-bold text-slate-900">Zoom-созвоны ученицы</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              {student.fullName}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* List of current Zoom sessions */}
        <div className="space-y-2.5">
          <div className="flex items-center justify-between text-xs font-semibold text-slate-500">
            <span>Этапы наставничества:</span>
            <span>Статус / Запись</span>
          </div>

          {(student.zoomSessions || []).map(sess => (
            <div
              key={sess.callNumber}
              className="p-3.5 rounded-xl border border-slate-100 bg-slate-50/50 flex items-center justify-between gap-3"
            >
              <div className="flex items-center gap-2.5">
                <button
                  onClick={() => onToggleCompleted(student.id, sess.callNumber)}
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
                    {sess.date || 'Дата не назначена'}
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
                  <span>Ссылка</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              )}
            </div>
          ))}
        </div>

        {/* Schedule New Call Form Toggle */}
        {!isAddingZoom ? (
          <button
            onClick={() => setIsAddingZoom(true)}
            className="w-full py-2.5 rounded-xl border-2 border-dashed border-slate-200 text-slate-600 hover:border-slate-400 hover:text-slate-900 text-xs font-semibold transition cursor-pointer flex items-center justify-center gap-1.5"
          >
            <Plus className="w-4 h-4" />
            <span>Запланировать новый созвон</span>
          </button>
        ) : (
          <div className="p-4 rounded-xl border border-slate-200 bg-slate-50/80 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-900">Новый созвон</span>
              <button
                onClick={() => setIsAddingZoom(false)}
                className="text-xs text-slate-400 hover:text-slate-600"
              >
                Скрыть
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <div>
                <label className="text-[11px] font-semibold text-slate-600 block mb-1">Название этапа:</label>
                <input
                  type="text"
                  value={zoomForm.title}
                  onChange={e => setZoomForm({ ...zoomForm, title: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white"
                  placeholder="Например: Аудит воронки и оффера"
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-[11px] font-semibold text-slate-600 block mb-1">Дата и время:</label>
                  <input
                    type="text"
                    value={zoomForm.scheduledAt}
                    onChange={e => setZoomForm({ ...zoomForm, scheduledAt: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white"
                    placeholder="12.11 в 15:00"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-semibold text-slate-600 block mb-1">Zoom / Запись ссылка:</label>
                  <input
                    type="text"
                    value={zoomForm.zoomUrl}
                    onChange={e => setZoomForm({ ...zoomForm, zoomUrl: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white"
                    placeholder="https://zoom.us/..."
                  />
                </div>
              </div>
              <div>
                <label className="text-[11px] font-semibold text-slate-600 block mb-1">Заметки наставника:</label>
                <input
                  type="text"
                  value={zoomForm.meetingNotes}
                  onChange={e => setZoomForm({ ...zoomForm, meetingNotes: e.target.value })}
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-white"
                  placeholder="Фокус созвона, домашнее задание..."
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setIsAddingZoom(false)}
                className="px-3 py-1.5 text-xs text-slate-600 hover:text-slate-800"
              >
                Отмена
              </button>
              <button
                onClick={() => void onAddZoomSession(student.id)}
                className="px-3 py-1.5 bg-slate-900 text-white text-xs font-semibold rounded-lg hover:bg-slate-800 transition cursor-pointer"
              >
                Сохранить созвон
              </button>
            </div>
          </div>
        )}

        <div className="pt-3 border-t border-slate-100 flex items-center justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer"
          >
            Готово
          </button>
        </div>
      </div>
    </div>
  );
};
