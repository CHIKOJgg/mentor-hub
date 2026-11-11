import type React from 'react';
import { X, Save } from 'lucide-react';

export interface ScriptItem {
  title: string;
  description: string;
  text: string;
}

export interface EditingScriptItem extends ScriptItem {
  index: number;
}

interface ScriptModalProps {
  isOpen: boolean;
  isAddingScript: boolean;
  editingScript: EditingScriptItem | null;
  newScript: ScriptItem;
  setNewScript: React.Dispatch<React.SetStateAction<ScriptItem>>;
  setEditingScript: React.Dispatch<React.SetStateAction<EditingScriptItem | null>>;
  onClose: () => void;
  onAdd: (script: ScriptItem) => void;
  onSave: (index: number, script: ScriptItem) => void;
  showToast: (msg: string) => void;
}

export const ScriptModal = ({
  isOpen,
  isAddingScript,
  editingScript,
  newScript,
  setNewScript,
  setEditingScript,
  onClose,
  onAdd,
  onSave,
  showToast,
}: ScriptModalProps) => {
  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4 animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl max-w-lg w-full p-6 border border-slate-200 space-y-4 shadow-xl max-h-[90vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base font-bold text-slate-900">
              {isAddingScript ? 'Добавить новый скрипт в Direct' : 'Редактировать скрипт'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-3">
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Название скрипта:</label>
            <input
              type="text"
              value={isAddingScript ? newScript.title : editingScript?.title || ''}
              onChange={e => {
                const val = e.target.value;
                if (isAddingScript) setNewScript(prev => ({ ...prev, title: val }));
                else if (editingScript) setEditingScript({ ...editingScript, title: val });
              }}
              onKeyDown={e => {
                if (e.key === 'Enter') {
                  if (isAddingScript) {
                    if (!newScript.title.trim()) {
                      showToast('Введите название скрипта');
                      return;
                    }
                    onAdd(newScript);
                    setNewScript({ title: '', description: '', text: '' });
                    onClose();
                  } else if (editingScript) {
                    onSave(editingScript.index, {
                      title: editingScript.title,
                      description: editingScript.description,
                      text: editingScript.text,
                    });
                    onClose();
                  }
                }
              }}
              placeholder="Например: Скрипт закрытия после созвона"
              className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Описание / Назначение:</label>
            <input
              type="text"
              value={isAddingScript ? newScript.description : editingScript?.description || ''}
              onChange={e => {
                const val = e.target.value;
                if (isAddingScript) setNewScript(prev => ({ ...prev, description: val }));
                else if (editingScript) setEditingScript({ ...editingScript, description: val });
              }}
              placeholder="Например: Формула дожима теплых лидов"
              className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Текст скрипта для копирования:</label>
            <textarea
              rows={4}
              value={isAddingScript ? newScript.text : editingScript?.text || ''}
              onChange={e => {
                const val = e.target.value;
                if (isAddingScript) setNewScript(prev => ({ ...prev, text: val }));
                else if (editingScript) setEditingScript({ ...editingScript, text: val });
              }}
              placeholder="Вставьте текст скрипта..."
              className="w-full text-xs sm:text-sm px-3.5 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500 font-mono leading-relaxed"
            />
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 transition cursor-pointer"
          >
            Отмена
          </button>
          <button
            type="button"
            onClick={() => {
              if (isAddingScript) {
                if (!newScript.title.trim()) {
                  showToast('Введите название скрипта');
                  return;
                }
                onAdd(newScript);
                setNewScript({ title: '', description: '', text: '' });
                onClose();
              } else if (editingScript) {
                onSave(editingScript.index, {
                  title: editingScript.title,
                  description: editingScript.description,
                  text: editingScript.text,
                });
                onClose();
              }
            }}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
          >
            <Save className="w-3.5 h-3.5" />
            <span>{isAddingScript ? 'Добавить скрипт' : 'Сохранить изменения'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};
