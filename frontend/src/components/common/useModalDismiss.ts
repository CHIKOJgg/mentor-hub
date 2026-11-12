import { useCallback } from 'react';

export function useModalDismiss(onClose: () => void, hasUnsavedChanges: boolean) {
  return useCallback(() => {
    if (hasUnsavedChanges && !window.confirm('Есть несохранённые изменения. Закрыть без сохранения?')) return;
    onClose();
  }, [hasUnsavedChanges, onClose]);
}
