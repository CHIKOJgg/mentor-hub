import { useCallback, useState } from 'react';
import type React from 'react';
import { X, Save } from 'lucide-react';
import { AccessibleModal } from '../common/AccessibleModal';
import { useModalDismiss } from '../common/useModalDismiss';

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
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const closeDialog = useCallback(() => { setHasUnsavedChanges(false); onClose(); }, [onClose]);
  const requestClose = useModalDismiss(closeDialog, hasUnsavedChanges);

  if (!isOpen) return null;

  return (
    <AccessibleModal title={isAddingScript ? 'Добавить скрипт' : 'Редактировать скрипт'} onClose={requestClose}>
      <div className="bg-white rounded-2xl max-w-lg w-full p-5 border border-slate-200 space-y-4 shadow-xl sm:p-6">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base font-bold text-slate-900">
              {isAddingScript ? 'Добавить новый скрипт в Direct' : 'Редактировать скрипт'}
            </h3>
          </div>
          <button
            onClick={requestClose}
            type="button"
            aria-label="Закрыть скрипт"
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
                setHasUnsavedChanges(true);
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
                    closeDialog();
                  } else if (editingScript) {
                    onSave(editingScript.index, {
                      title: editingScript.title,
                      description: editingScript.description,
                      text: editingScript.text,
                    });
                    closeDialog();
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
                setHasUnsavedChanges(true);
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
                setHasUnsavedChanges(true);
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
            onClick={requestClose}
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
                closeDialog();
              } else if (editingScript) {
                onSave(editingScript.index, {
                  title: editingScript.title,
                  description: editingScript.description,
                  text: editingScript.text,
                });
                closeDialog();
              }
            }}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
          >
            <Save className="w-3.5 h-3.5" />
            <span>{isAddingScript ? 'Добавить скрипт' : 'Сохранить изменения'}</span>
          </button>
        </div>
      </div>
    </AccessibleModal>
  );
};
