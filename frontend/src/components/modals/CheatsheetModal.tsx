import type React from 'react';
import { X, Save } from 'lucide-react';
import type { CheatsheetItem } from '../../types';

interface CheatsheetModalProps {
  isOpen: boolean;
  isAddingCheatsheet: boolean;
  editingCheatsheet: CheatsheetItem | null;
  newCheatsheet: Omit<CheatsheetItem, 'id' | 'createdAt'>;
  setEditingCheatsheet: React.Dispatch<React.SetStateAction<CheatsheetItem | null>>;
  setNewCheatsheet: React.Dispatch<React.SetStateAction<Omit<CheatsheetItem, 'id' | 'createdAt'>>>;
  onClose: () => void;
  onSave: (item: CheatsheetItem) => void;
  onAdd: () => void;
}

export const CheatsheetModal = ({
  isOpen,
  isAddingCheatsheet,
  editingCheatsheet,
  newCheatsheet,
  setEditingCheatsheet,
  setNewCheatsheet,
  onClose,
  onSave,
  onAdd,
}: CheatsheetModalProps) => {
  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4 overflow-y-auto animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl max-w-xl w-full p-6 border border-slate-200 space-y-4 my-8 shadow-2xl"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h3 className="text-base font-bold text-slate-900">
              {isAddingCheatsheet ? 'Новая шпаргалка для учениц' : 'Редактировать шпаргалку'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-3 text-xs">
          <div>
            <label className="font-semibold text-slate-700 block mb-1">Категория:</label>
            <select
              value={editingCheatsheet ? editingCheatsheet.category : newCheatsheet.category}
              onChange={e => {
                const cat = e.target.value as CheatsheetItem['category'];
                if (editingCheatsheet) setEditingCheatsheet({ ...editingCheatsheet, category: cat });
                else setNewCheatsheet({ ...newCheatsheet, category: cat });
              }}
              className="w-full px-3.5 py-2 rounded-xl border border-slate-200 bg-white"
            >
              <option value="reels">Reels и контент</option>
              <option value="sales">Продажи в Direct</option>
              <option value="packaging">Упаковка профиля</option>
              <option value="rules">Регламент и отчёты</option>
            </select>
          </div>

          <div>
            <label className="font-semibold text-slate-700 block mb-1">Заголовок шпаргалки:</label>
            <input
              type="text"
              value={editingCheatsheet ? editingCheatsheet.title : newCheatsheet.title}
              onChange={e => {
                if (editingCheatsheet) setEditingCheatsheet({ ...editingCheatsheet, title: e.target.value });
                else setNewCheatsheet({ ...newCheatsheet, title: e.target.value });
              }}
              placeholder="Например: Чек-лист проверки рилс перед публикацией"
              className="w-full px-3.5 py-2 rounded-xl border border-slate-200 bg-white"
            />
          </div>

          <div>
            <label className="font-semibold text-slate-700 block mb-1">Краткое описание (для кого и когда):</label>
            <input
              type="text"
              value={editingCheatsheet ? editingCheatsheet.description : newCheatsheet.description}
              onChange={e => {
                if (editingCheatsheet) setEditingCheatsheet({ ...editingCheatsheet, description: e.target.value });
                else setNewCheatsheet({ ...newCheatsheet, description: e.target.value });
              }}
              placeholder="Отправлять ученицам, когда проседает глубина досмотра"
              className="w-full px-3.5 py-2 rounded-xl border border-slate-200 bg-white"
            />
          </div>

          <div>
            <label className="font-semibold text-slate-700 block mb-1">Текст шпаргалки:</label>
            <textarea
              rows={8}
              value={editingCheatsheet ? editingCheatsheet.content : newCheatsheet.content}
              onChange={e => {
                if (editingCheatsheet) setEditingCheatsheet({ ...editingCheatsheet, content: e.target.value });
                else setNewCheatsheet({ ...newCheatsheet, content: e.target.value });
              }}
              placeholder="Вставьте готовый текст, структуру сообщений или чек-лист..."
              className="w-full px-3.5 py-2 rounded-xl border border-slate-200 bg-white font-mono text-xs leading-relaxed"
            />
          </div>
        </div>

        <div className="pt-3 border-t border-slate-100 flex items-center justify-end gap-2">
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
              if (editingCheatsheet) {
                onSave(editingCheatsheet);
                setEditingCheatsheet(null);
              } else {
                onAdd();
              }
            }}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
          >
            <Save className="w-3.5 h-3.5" />
            <span>{isAddingCheatsheet ? 'Добавить шпаргалку' : 'Сохранить изменения'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};
