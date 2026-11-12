const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

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

const readCsrfCookie = (): string | null => {
  if (typeof document === 'undefined') return null;
  const cookie = document.cookie.split(';').map(part => part.trim()).find(part => part.startsWith('XSRF-TOKEN='));
  if (!cookie) return null;
  return decodeURIComponent(cookie.slice('XSRF-TOKEN='.length));
};

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');

  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const method = (init.method ?? 'GET').toUpperCase();
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const csrfToken = readCsrfCookie();
    if (!csrfToken) throw new ApiError('CSRF token is missing; reload and retry', 403);
    headers.set('X-XSRF-TOKEN', csrfToken);
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20_000);
  const abortRequest = () => controller.abort();
  if (init.signal?.aborted) abortRequest();
  else init.signal?.addEventListener('abort', abortRequest, { once: true });

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
      signal: controller.signal,
      credentials: 'same-origin',
    });
  } catch (error) {
    clearTimeout(timeout);
    init.signal?.removeEventListener('abort', abortRequest);
    throw new ApiError(
      controller.signal.aborted ? 'Сервер не ответил за 20 секунд. Попробуйте ещё раз.' : 'Не удалось подключиться к серверу. Проверьте соединение.',
      0,
      error,
    );
  }

  let responseBody: unknown;
  try {
    responseBody = response.status === 204 ? undefined : await response.json().catch(() => undefined);

    if (!response.ok) {
      const message = typeof responseBody === 'object' && responseBody !== null && 'message' in responseBody
        ? String(responseBody.message)
        : `Ошибка API (${response.status})`;
      throw new ApiError(message, response.status, responseBody);
    }

    return responseBody as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError('Не удалось обработать ответ сервера. Повторите попытку.', 0, error);
  } finally {
    clearTimeout(timeout);
    init.signal?.removeEventListener('abort', abortRequest);
  }
}

const json = (method: string, payload?: unknown): RequestInit => ({
  method,
  ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
});

export const api = {
  getCsrfToken: () => apiRequest<{ token: string }>('/auth/csrf'),
  login: async <T>(accessKey: string): Promise<T> => {
    await api.getCsrfToken();
    return apiRequest<T>('/auth/login', json('POST', { accessKey }));
  },
  logout: () => apiRequest<void>('/auth/logout', json('POST')),
  getCurrentSession: <T>() => apiRequest<T>('/auth/session'),
  getInbox: <T>() => apiRequest<T>('/inbox?status=PENDING'),
  getAllReports: <T>() => apiRequest<T>('/inbox?status=ALL'),
  approveReport: <T>(id: string, replyText?: string) =>
    apiRequest<T>(`/inbox/${encodeURIComponent(id)}/approve`, json('POST', { replyText })),
  rejectReport: <T>(id: string, reason: string) =>
    apiRequest<T>(`/inbox/${encodeURIComponent(id)}/reject`, json('POST', { reason })),
  getStudents: <T>() => apiRequest<T>('/students?active=true'),
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
