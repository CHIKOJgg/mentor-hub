const STORAGE_VERSION = 'v1';

const storageKey = (key: string) => `mentor-tracker.${key}.${STORAGE_VERSION}`;

export function readPersisted<T>(key: string, fallback: T): T {
  try {
    const saved = localStorage.getItem(storageKey(key));
    return saved ? (JSON.parse(saved) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function writePersisted<T>(key: string, value: T): boolean {
  try {
    localStorage.setItem(storageKey(key), JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}
