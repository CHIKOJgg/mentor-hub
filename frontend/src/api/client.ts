const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
const ADMIN_TOKEN_KEY = 'mentor-tracker.admin-token.v1';

export class ApiError extends Error {
  readonly status: number;
  readonly body?: unknown;

  constructor(
    message: string,
    status: number,
    body?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

export const getAdminToken = (): string => {
  const configuredToken = import.meta.env.VITE_ADMIN_TOKEN?.trim();
  if (configuredToken) return configuredToken;

  try {
    return sessionStorage.getItem(ADMIN_TOKEN_KEY)?.trim() ?? '';
  } catch {
    return '';
  }
};

export const saveAdminToken = (token: string): void => {
  try {
    if (token.trim()) sessionStorage.setItem(ADMIN_TOKEN_KEY, token.trim());
    else sessionStorage.removeItem(ADMIN_TOKEN_KEY);
  } catch {
    // Storage can be unavailable in private or restricted browser contexts.
  }
};

export const clearAdminToken = (): void => saveAdminToken('');

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  options: { authenticated?: boolean } = {},
): Promise<T> {
  const authenticated = options.authenticated !== false;
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');

  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (authenticated) {
    const token = getAdminToken();
    if (!token) throw new ApiError('Требуется токен администратора', 401);
    headers.set('X-Admin-Token', token);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
      credentials: 'same-origin',
    });
  } catch (error) {
    throw new ApiError(error instanceof Error ? error.message : 'Сервер недоступен', 0);
  }

  const responseBody = response.status === 204
    ? undefined
    : await response.json().catch(() => undefined);

  if (!response.ok) {
    const message = typeof responseBody === 'object' && responseBody !== null && 'message' in responseBody
      ? String(responseBody.message)
      : `Ошибка API (${response.status})`;
    throw new ApiError(message, response.status, responseBody);
  }

  return responseBody as T;
}

const json = (method: string, payload?: unknown): RequestInit => ({
  method,
  ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
});

export const api = {
  getSystemStatus: <T>() => apiRequest<T>('/system/status', {}, { authenticated: false }),
  getCurrentSession: <T>() => apiRequest<T>('/system/session'),
  getInbox: <T>() => apiRequest<T>('/inbox?status=PENDING'),
  getAllReports: <T>() => apiRequest<T>('/inbox?status=ALL'),
  approveReport: <T>(id: string, replyText?: string) =>
    apiRequest<T>(`/inbox/${encodeURIComponent(id)}/approve`, json('POST', { replyText })),
  rejectReport: <T>(id: string, reason: string) =>
    apiRequest<T>(`/inbox/${encodeURIComponent(id)}/reject`, json('POST', { reason })),
  getStudents: <T>() => apiRequest<T>('/students'),
  createStudent: <T>(payload: unknown) => apiRequest<T>('/students', json('POST', payload)),
  updateStudent: <T>(id: string, payload: unknown) =>
    apiRequest<T>(`/students/${encodeURIComponent(id)}`, json('PUT', payload)),
  togglePause: <T>(id: string, payload: unknown) =>
    apiRequest<T>(`/students/${encodeURIComponent(id)}/toggle-pause`, json('POST', payload)),
  sendStudentMessage: <T>(id: string, text: string) =>
    apiRequest<T>(`/students/${encodeURIComponent(id)}/telegram-message`, json('POST', { text })),
  deleteStudent: <T>(id: string) =>
    apiRequest<T>(`/students/${encodeURIComponent(id)}`, json('DELETE')),
  getCalendar: <T>(id: string) => apiRequest<T>(`/students/${encodeURIComponent(id)}/discipline-calendar`),
  updateCalendarDay: <T>(id: string, payload: unknown) =>
    apiRequest<T>(`/students/${encodeURIComponent(id)}/discipline-calendar`, json('PUT', payload)),
  replaceStudentZoomSessions: <T>(id: string, sessions: unknown[]) =>
    apiRequest<T>(`/students/${encodeURIComponent(id)}/zoom-meetings`, json('PUT', { sessions })),
  getTaskRules: <T>() => apiRequest<T>('/tasks/rules?includeInactive=true'),
  createTaskRule: <T>(payload: unknown) => apiRequest<T>('/tasks/rules', json('POST', payload)),
  updateTaskRule: <T>(id: string, payload: unknown) =>
    apiRequest<T>(`/tasks/rules/${encodeURIComponent(id)}`, json('PUT', payload)),
  deleteTaskRule: <T>(id: string) =>
    apiRequest<T>(`/tasks/rules/${encodeURIComponent(id)}`, json('DELETE')),
  getSettings: <T>() => apiRequest<T>('/system/settings'),
  updateSettings: <T>(payload: unknown) => apiRequest<T>('/system/settings', json('PUT', payload)),
  syncTelegram: <T>() => apiRequest<T>('/system/sync-telegram', json('POST')),
  getZoomTracker: <T>() => apiRequest<T>('/system/zoom-tracker'),
  createZoomMeeting: <T>(payload: unknown) => apiRequest<T>('/system/zoom-tracker', json('POST', payload)),
  updateZoomMeeting: <T>(id: string, payload: unknown) =>
    apiRequest<T>(`/system/zoom-tracker/${encodeURIComponent(id)}`, json('PUT', payload)),
  deleteZoomMeeting: <T>(id: string) =>
    apiRequest<T>(`/system/zoom-tracker/${encodeURIComponent(id)}`, json('DELETE')),
  getAnalyticsSummary: <T>() => apiRequest<T>('/analytics/summary'),
  getStoryCase: <T>(id: string) => apiRequest<T>(`/analytics/case/${encodeURIComponent(id)}`),
  getScripts: <T>() => apiRequest<T>('/scripts'),
  createScript: <T>(payload: unknown) => apiRequest<T>('/scripts', json('POST', payload)),
  updateScript: <T>(id: string, payload: unknown) =>
    apiRequest<T>(`/scripts/${encodeURIComponent(id)}`, json('PUT', payload)),
  deleteScript: <T>(id: string) => apiRequest<T>(`/scripts/${encodeURIComponent(id)}`, json('DELETE')),
};
