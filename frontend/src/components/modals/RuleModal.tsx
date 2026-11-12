import type React from 'react';
import { X, Save } from 'lucide-react';
import type { TaskRule } from '../../types';

export type NewRuleForm = Omit<TaskRule, 'id'>;

interface RuleModalProps {
  isOpen: boolean;
  isAddingRule: boolean;
  editingRule: TaskRule | null;
  newRule: NewRuleForm;
  setNewRule: React.Dispatch<React.SetStateAction<NewRuleForm>>;
  setEditingRule: React.Dispatch<React.SetStateAction<TaskRule | null>>;
  onClose: () => void;
  onAdd: (rule: NewRuleForm) => void;
  onSave: (rule: TaskRule) => void;
  showToast: (msg: string) => void;
}

export const RuleModal = ({
  isOpen,
  isAddingRule,
  editingRule,
  newRule,
  setNewRule,
  setEditingRule,
  onClose,
  onAdd,
  onSave,
  showToast,
}: RuleModalProps) => {
  if (!isOpen) return null;
  const isReelsRule = ((isAddingRule ? newRule.hashtag : editingRule?.hashtag) ?? '').trim().toLowerCase() === '#рилс';

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
              {isAddingRule ? 'Новый хэштег / задание в боте' : `Редактирование: ${editingRule?.hashtag}`}
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
          {isReelsRule && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-3 text-xs text-amber-900" role="note">
              <strong className="block">Черновик Reels нужно отправить до публикации</strong>
              <span className="mt-0.5 block">Наставник проверяет ролик в трекере; публикация — после проверки и правок.</span>
            </div>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Хэштег (с символом #):</label>
              <input
                type="text"
                value={isAddingRule ? newRule.hashtag : editingRule?.hashtag || ''}
                onChange={e => {
                  const val = e.target.value.startsWith('#') ? e.target.value : `#${e.target.value}`;
                  const required = val.trim().toLowerCase() === '#рилс';
                  if (isAddingRule) setNewRule(prev => ({ ...prev, hashtag: val, required: required || prev.required }));
                  else if (editingRule) setEditingRule({ ...editingRule, hashtag: val, required: required || editingRule.required });
                }}
                placeholder="#хэштег"
                className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500 font-mono font-bold"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Название задания:</label>
              <input
                type="text"
                value={isAddingRule ? newRule.title : editingRule?.title || ''}
                onChange={e => {
                  const val = e.target.value;
                  if (isAddingRule) setNewRule(prev => ({ ...prev, title: val }));
                  else if (editingRule) setEditingRule({ ...editingRule, title: val });
                }}
                onKeyDown={e => {
                  if (e.key === 'Enter') {
                    if (isAddingRule) {
                      if (!newRule.hashtag.trim() || newRule.hashtag === '#') {
                        showToast('Введите хэштег');
                        return;
                      }
                      onAdd({ ...newRule, required: newRule.required || isReelsRule });
                      setNewRule({ hashtag: '#', title: '', description: '', required: false, example: '' });
                      onClose();
                    } else if (editingRule) {
                      onSave({ ...editingRule, required: editingRule.required || isReelsRule });
                      onClose();
                    }
                  }
                }}
                placeholder="Например: Отзыв от клиента"
                className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Описание регламента сдачи:</label>
            <textarea
              rows={2}
              value={isAddingRule ? newRule.description : editingRule?.description || ''}
              onChange={e => {
                const val = e.target.value;
                if (isAddingRule) setNewRule(prev => ({ ...prev, description: val }));
                else if (editingRule) setEditingRule({ ...editingRule, description: val });
              }}
              placeholder="Опишите, что бот принимает по этому тегу..."
              className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500 leading-relaxed"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">Пример для чата:</label>
            <input
              type="text"
              value={isAddingRule ? newRule.example : editingRule?.example || ''}
              onChange={e => {
                const val = e.target.value;
                if (isAddingRule) setNewRule(prev => ({ ...prev, example: val }));
                else if (editingRule) setEditingRule({ ...editingRule, example: val });
              }}
              placeholder="Например: #отзыв скриншот переписки с клиентом"
              className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500 font-mono text-slate-700"
            />
          </div>

          <div className="flex items-center justify-between pt-1">
            <span className="text-xs font-semibold text-slate-700">Обязательное задание потока:</span>
            <button
              type="button"
              disabled={isReelsRule}
              onClick={() => {
                if (isAddingRule) setNewRule(prev => ({ ...prev, required: !prev.required }));
                else if (editingRule) setEditingRule({ ...editingRule, required: !editingRule.required });
              }}
              className={`text-xs font-semibold px-3 py-1 rounded-lg border transition cursor-pointer disabled:cursor-not-allowed ${
                isReelsRule || (isAddingRule ? newRule.required : editingRule?.required)
                  ? 'bg-rose-50 text-rose-700 border-rose-200'
                  : 'bg-slate-100 text-slate-600 border-slate-200'
              }`}
            >
              {isReelsRule || (isAddingRule ? newRule.required : editingRule?.required) ? 'обязательно' : 'по факту'}
            </button>
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
              if (isAddingRule) {
                if (!newRule.hashtag.trim() || newRule.hashtag === '#') {
                  showToast('Введите хэштег');
                  return;
                }
                onAdd({ ...newRule, required: newRule.required || isReelsRule });
                setNewRule({ hashtag: '#', title: '', description: '', required: false, example: '' });
                onClose();
              } else if (editingRule) {
                onSave({ ...editingRule, required: editingRule.required || isReelsRule });
                onClose();
              }
            }}
            className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
          >
            <Save className="w-3.5 h-3.5" />
            <span>{isAddingRule ? 'Добавить правило' : 'Сохранить правило'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};
