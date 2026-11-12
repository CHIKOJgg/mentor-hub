import { useState } from 'react';

const CUSTOM_CATEGORY = '__custom_student_category__';

interface StudentCategoryFieldProps {
  id: string;
  value: string;
  categories: string[];
  onChange: (category: string) => void;
  className?: string;
}

export function StudentCategoryField({ id, value, categories, onChange, className = '' }: StudentCategoryFieldProps) {
  const [isEnteringCustomCategory, setIsEnteringCustomCategory] = useState(false);
  const [customCategory, setCustomCategory] = useState('');
  const availableCategories = Array.from(new Set([...categories, ...(value ? [value] : [])]));

  return (
    <div className={`space-y-2 ${className}`}>
      <select
        id={id}
        value={isEnteringCustomCategory ? CUSTOM_CATEGORY : value}
        onChange={event => {
          if (event.target.value === CUSTOM_CATEGORY) {
            setIsEnteringCustomCategory(true);
            setCustomCategory('');
            return;
          }
          setIsEnteringCustomCategory(false);
          onChange(event.target.value);
        }}
        className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-indigo-500"
      >
        {availableCategories.map(category => (
          <option key={category} value={category}>{category}</option>
        ))}
        <option value={CUSTOM_CATEGORY}>＋ Добавить свою категорию</option>
      </select>
      {isEnteringCustomCategory && (
        <input
          type="text"
          value={customCategory}
          onChange={event => {
            const nextCategory = event.target.value;
            setCustomCategory(nextCategory);
            if (nextCategory.trim()) onChange(nextCategory.trim());
          }}
          maxLength={100}
          placeholder="Например: массажист"
          aria-label="Название новой категории ученицы"
          className="min-h-11 w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-indigo-500"
        />
      )}
    </div>
  );
}
