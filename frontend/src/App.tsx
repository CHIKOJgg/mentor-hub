import { useState, useRef, useEffect, useMemo } from 'react';
import { initialReports, studentDisciplines, taskRules, defaultSystemSettings } from './mockData';
import type { DailyReport, StudentDiscipline, TabType, ContentType, TaskRule, SystemSettings, DayInfo, ZoomSessionRecord } from './types';
import type { ApiAdminSession, ApiCuratorScript, ApiDailyReport, ApiStudent, ApiSystemSettings, ApiTaskRule, ApiZoomSession } from './api/contracts';
import { api, getAdminToken, saveAdminToken } from './api/client';
import { readPersisted, writePersisted } from './persistence';
import {
  ExternalLink,
  Copy,
  TrendingUp,
  Users,
  Inbox,
  Hash,
  AlertTriangle,
  Check,
  Search,
  RefreshCw,
  BarChart3,
  RotateCcw,
  FileText,
  Video,
  Coffee,
  CheckCheck,
  Layers,
  Smartphone,
  HelpCircle,
  Flame,
  ArrowUpRight,
  MessageSquare,
  Clock,
  ShieldCheck,
  Send,
  BookOpen,
  Pencil,
  Plus,
  Trash2,
  X,
  Save,
  Settings,
  Calendar,
} from 'lucide-react';

const createLocalId = (prefix: string): string => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now().toString(36)}`;
};

const getTodayIsoDate = (): string => {
  return new Date().toISOString().slice(0, 10);
};

const calculateMultiplier = (current: number, initial: number): string => {
  if (!initial || initial <= 0 || !Number.isFinite(initial)) return '1.0';
  const ratio = current / initial;
  if (!Number.isFinite(ratio) || Number.isNaN(ratio)) return '1.0';
  return ratio.toFixed(1);
};

const getContentTypeBadge = (type: ContentType) => {
  switch (type) {
    case 'reel':
      return {
        label: 'Reels',
        icon: Video,
        bg: 'bg-indigo-50 text-indigo-700 border-indigo-100/80',
      };
    case 'carousel':
      return {
        label: 'Карусель',
        icon: Layers,
        bg: 'bg-slate-100 text-slate-700 border-slate-200/60',
      };
    case 'storytell':
      return {
        label: 'Сторителл',
        icon: Smartphone,
        bg: 'bg-indigo-50 text-indigo-700 border-indigo-100/80',
      };
  }
};

const defaultZoomSessionTitles = [
  'Стратегия и декомпозиция цели',
  'Позиционирование и упаковка предложения',
  'Контент и привлечение клиентов',
  'Продажи и работа с возражениями',
  'Итоги и следующий план действий',
];

const getStudentZoomSessions = (student: StudentDiscipline): ZoomSessionRecord[] => {
  const configuredSessions = student.zoomSessions ?? student.portalData?.zoomSessions;
  if (configuredSessions) {
    return configuredSessions.map((session, index) => ({ ...session, callNumber: index + 1 }));
  }

  const sessionCount = Math.max(
    defaultZoomSessionTitles.length,
    student.completedZoomsCount + (student.nextZoomDate ? 1 : 0),
  );

  return Array.from({ length: sessionCount }, (_, index) => ({
    callNumber: index + 1,
    title: defaultZoomSessionTitles[index] ?? `Этап сопровождения ${index + 1}`,
    date: index === student.completedZoomsCount ? student.nextZoomDate ?? '' : '',
    isCompleted: index < student.completedZoomsCount,
  }));
};

const withZoomSessionSummary = (
  student: StudentDiscipline,
  sessions: ZoomSessionRecord[],
): StudentDiscipline => {
  const zoomSessions = sessions.map((session, index) => ({ ...session, callNumber: index + 1 }));
  const nextSession = zoomSessions.find(session => !session.isCompleted);
  const nextZoomDate = nextSession?.date.trim() || undefined;

  return {
    ...student,
    zoomSessions,
    completedZoomsCount: zoomSessions.filter(session => session.isCompleted).length,
    nextZoomDate,
  };
};

const newStudentTemplate = {
  fullName: '',
  niche: '',
  telegram: '',
  telegramChatId: '',
  initialIncome: 0,
  initialAvgTicket: 0,
  tariff: 'Стандарт' as const,
};

const makeInitialWeekDays = (): DayInfo[] => {
  const today = new Date();
  const monday = new Date(today);
  const offset = (today.getDay() + 6) % 7;
  monday.setDate(today.getDate() - offset);
  const dayNames = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];

  return dayNames.map((day, index) => {
    const date = new Date(monday);
    date.setDate(monday.getDate() + index);
    return {
      day,
      date: `${String(date.getDate()).padStart(2, '0')}.${String(date.getMonth() + 1).padStart(2, '0')}`,
      status: index === 6 ? 'WEEKEND' : 'PENDING',
    };
  });
};

const makeAvatarText = (fullName: string): string =>
  fullName.trim().split(/\s+/).slice(0, 2).map(part => part[0]?.toLocaleUpperCase('ru-RU') ?? '').join('') || 'У';

const parseZoomDate = (value: string): string | null => {
  const raw = value.trim();
  if (!raw) return null;
  if (/^\d{4}-\d{2}-\d{2}T/.test(raw)) {
    const parsed = new Date(raw);
    return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
  }
  const match = raw.match(/^(\d{1,2})\.(\d{1,2})(?:\.(\d{4}))?(?:\s*в\s*(\d{1,2}:\d{2}))?$/i);
  if (!match) return null;
  const day = Number(match[1]);
  const month = Number(match[2]);
  const year = Number(match[3] || new Date().getFullYear());
  const time = match[4] || '12:00';
  const parsed = new Date(`${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}T${time}:00+03:00`);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
};

const mapApiZoomSession = (session: ApiZoomSession, index: number): ZoomSessionRecord => ({
  callNumber: session.callNumber ?? index + 1,
  title: session.title,
  date: session.scheduledAt ? new Date(session.scheduledAt).toLocaleString('ru-RU', { timeZone: 'Europe/Minsk', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }).replace(',', ' в') : '',
  isCompleted: session.isCompleted,
  zoomLink: session.zoomUrl,
  recordingUrl: session.recordingUrl,
  keyTakeaway: session.meetingNotes,
});

const serializeZoomSessions = (sessions: ZoomSessionRecord[]) => sessions.map((session, index) => ({
  callNumber: index + 1,
  title: session.title,
  zoomUrl: session.zoomLink ?? null,
  recordingUrl: session.recordingUrl ?? null,
  scheduledAt: parseZoomDate(session.date),
  isCompleted: session.isCompleted,
  meetingNotes: session.keyTakeaway ?? null,
}));

const defaultCuratorScripts = [
  {
    id: 'local-script-seed-1',
    title: 'Скрипт Direct: Первое касание (шаблон для учениц)',
    description: 'Холодное касание экспертам и блогерам без давления и навязывания',
    category: 'Продажи',
    text: '«Привет, [Имя]! Увидела твой сторителл про запуск — очень откликается глубина. Заметила, что многие сильные эксперты на масштабе ловят выгорание и синдром самозванца. Я клинический психолог, помогаю экспертам пробить плато дохода без надрыва. Хочу подарить 15-минутный экспресс-разбор: где сейчас утекает энергия. Актуально на этой неделе?»',
  },
  {
    id: 'local-script-seed-2',
    title: 'Офер пакета консультаций за 15 000 ₽ (шаблон)',
    description: 'Формула закрытия после бесплатного экспресс-разбора на высокий чек',
    category: 'Продажи',
    text: '«Мы вскрыли корень: страх отказа и обесценивание своего опыта. Чтобы убрать это системно за 3 недели и зафиксировать чек, предлагаю пакет из 4 сессий с моим личным сопровождением в Telegram между встречами. Стоимость — 15 000 ₽. Стартуем в четверг?»',
  },
  {
    id: 'local-script-seed-3',
    title: 'Напоминание о дедлайне в личку (от куратора Кати)',
    description: 'Мягкий пуш ученицам за 1 час до дедлайна с фокусом на результат',
    category: 'Кураторские',
    text: '«Привет! Напоминаю, что сегодня ждём твой черновик контента и количество касаний до 21:00. Ксения сейчас на связи и разбирает отчёты в первую очередь. Если есть затык — отправляй голосовое с #вопрос, разберём!»',
  },
  {
    id: 'local-script-seed-4',
    title: 'Поддержка при спаде энергии и амнистия (от наставника Ксении)',
    description: 'Личное сообщение ученице в риске слёта для возврата в стрик',
    category: 'Удержание',
    text: '«Привет, дорогая! Вижу, что накопилась усталость от совмещения. Давай снизим темп: на сегодня план-минимум — всего 15 рассылок и короткий Reels без сложного монтажа. Твоя Точка Б ближе, чем кажется, давай не сливать стрик!»',
  },
];

type LocalDataDomain = 'reports' | 'students' | 'task-rules' | 'system-settings' | 'curator-scripts';

const mapApiReport = (report: ApiDailyReport): DailyReport => ({
  ...report,
  id: report.id.startsWith('rep-') ? report.id : `rep-${report.id}`,
  studentId: report.studentId.startsWith('st-') ? report.studentId : `st-${report.studentId}`,
  tariff: report.tariff.toUpperCase() === 'VIP' ? 'VIP' : 'Стандарт',
  salesAmount: Number(report.salesAmount) || 0,
  contentDrafts: (report.contentDrafts ?? []).map(draft => ({
    ...draft,
    type: (draft.type?.toLowerCase() === 'storytell' ? 'storytell' : draft.type?.toLowerCase() === 'carousel' ? 'carousel' : 'reel') as ContentType,
  })),
  reels: (report.reels ?? []).map(reel => ({ ...reel })),
});

const mapApiStudent = (student: ApiStudent): StudentDiscipline => {
  const mapped: StudentDiscipline = {
    ...student,
    telegram: student.tgUsername ? (student.tgUsername.startsWith('@') ? student.tgUsername : `@${student.tgUsername}`) : undefined,
    telegramChatId: student.tgChatId,
    tariff: student.tariff?.toUpperCase() === 'VIP' ? 'VIP' : 'Стандарт',
    initialIncome: Number(student.initialIncome) || 0,
    currentIncome: Number(student.currentIncome) || 0,
    initialAvgTicket: Number(student.initialAvgTicket) || 0,
    currentAvgTicket: Number(student.currentAvgTicket) || 0,
    weekDays: student.weekDays ?? [],
    zoomSessions: student.zoomSessions?.map(mapApiZoomSession),
  };
  return withZoomSessionSummary(mapped, mapped.zoomSessions ?? getStudentZoomSessions(mapped));
};

const toApiStudentPayload = (student: StudentDiscipline) => ({
  fullName: student.fullName,
  avatarText: student.avatarText,
  tgUsername: student.telegram?.replace(/^@/, '') ?? null,
  tgChatId: student.telegramChatId ?? null,
  niche: student.niche,
  tariff: student.tariff === 'VIP' ? 'VIP' : 'STANDARD',
  initialIncome: student.initialIncome,
  currentIncome: student.currentIncome,
  initialAvgTicket: student.initialAvgTicket,
  currentAvgTicket: student.currentAvgTicket,
  startDate: student.startDate ?? new Date().toISOString().slice(0, 10),
  isActive: student.isActive ?? true,
  isPaused: student.isOnPause,
  pauseReason: student.pauseReason ?? null,
});

const mapApiTaskRule = (rule: ApiTaskRule): TaskRule => ({ ...rule, enabled: rule.isActive ?? true });

const mapApiSettings = (settings: ApiSystemSettings): SystemSettings => ({
  ...settings,
  morningPushTime: settings.morningPushTime.slice(0, 5),
  eveningReminderTime: settings.eveningReminderTime.slice(0, 5),
  dailyDeadlineTime: settings.dailyDeadlineTime.slice(0, 5),
  saturdayPollTime: settings.saturdayPollTime.slice(0, 5),
});

const getReportContentCount = (reports: DailyReport[], type: ContentType): number =>
  reports.reduce((count, report) => count + (report.contentDrafts ?? []).filter(draft => draft.type === type).length, 0);

const getReportDate = (report: DailyReport): string => report.reportDate ?? new Date().toISOString().slice(0, 10);

const getReportRange = (reports: DailyReport[], period: 'week' | 'month' | 'all'): DailyReport[] => {
  if (period === 'all') return reports;
  const end = new Date();
  end.setHours(23, 59, 59, 999);
  const start = new Date(end);
  start.setDate(start.getDate() - (period === 'week' ? 6 : 29));
  start.setHours(0, 0, 0, 0);
  return reports.filter(report => {
    const date = new Date(`${getReportDate(report)}T12:00:00`);
    return !Number.isNaN(date.getTime()) && date >= start && date <= end;
  });
};

export default function App() {
  const [activeTab, setActiveTab] = useState<'inbox' | 'students' | 'analytics' | 'tasks'>('inbox');
  const [reports, setReports] = useState<DailyReport[]>(() => readPersisted('reports', initialReports));
  const [allReports, setAllReports] = useState<DailyReport[]>(() => readPersisted('all-reports', initialReports));
  const [students, setStudents] = useState<StudentDiscipline[]>(() =>
    readPersisted('students', studentDisciplines.map(student => withZoomSessionSummary(student, getStudentZoomSessions(student)))),
  );
  const [replies, setReplies] = useState<Record<string, string>>(() => readPersisted('replies', {}));
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [selectedStudentId, setSelectedStudentId] = useState<string>(() =>
    readPersisted('selected-student-id', studentDisciplines[0].id),
  );
  const selectedStudent = useMemo(
    () => students.find(student => student.id === selectedStudentId) ?? students[0] ?? studentDisciplines[0],
    [students, selectedStudentId],
  );
  const setSelectedStudent = (student: StudentDiscipline) => {
    setSelectedStudentId(student.id);
  };
  const [searchQuery, setSearchQuery] = useState('');
  const [activeAdmin, setActiveAdmin] = useState<'Ксения' | 'Катя'>(() => readPersisted('active-admin', 'Ксения'));
  const [analyticsCategory, setAnalyticsCategory] = useState<'cohort' | 'students'>('cohort');
  const [analyticsPeriod, setAnalyticsPeriod] = useState<'week' | 'month' | 'all'>('all');
  const [rules, setRules] = useState<TaskRule[]>(() => readPersisted('task-rules', taskRules));
  const [systemSettings, setSystemSettings] = useState<SystemSettings>(() => readPersisted('system-settings', defaultSystemSettings));
  const [tasksSubTab, setTasksSubTab] = useState<'hashtags' | 'scripts' | 'system'>('hashtags');
  const [studentsFilter, setStudentsFilter] = useState<'all' | 'perfect' | 'attention' | 'pause' | 'zooms'>('all');
  const [studentsViewMode, setStudentsViewMode] = useState<'calendar' | 'zooms'>('calendar');
  const [selectedScriptCategory, setSelectedScriptCategory] = useState<string>('Все');
  const [curatorScripts, setCuratorScripts] = useState<ApiCuratorScript[]>(() => readPersisted('curator-scripts', defaultCuratorScripts));
  const [adminToken, setAdminToken] = useState(getAdminToken);
  const [adminTokenDraft, setAdminTokenDraft] = useState(getAdminToken);
  const [apiSession, setApiSession] = useState<ApiAdminSession | null>(null);
  const [apiConnection, setApiConnection] = useState<'checking' | 'connected' | 'offline' | 'unauthorized' | 'needs-token'>('checking');
  const [apiConnectionMessage, setApiConnectionMessage] = useState('Проверяем подключение');
  const [isApiSyncing, setIsApiSyncing] = useState(false);
  const [apiRefreshKey, setApiRefreshKey] = useState(0);
  const [pendingSyncDomains, setPendingSyncDomains] = useState<string[]>(() => readPersisted('pending-sync-domains', []));
  const [isAddingStudent, setIsAddingStudent] = useState(false);
  const [newStudent, setNewStudent] = useState<{ fullName: string; niche: string; telegram: string; telegramChatId: string; initialIncome: number; initialAvgTicket: number; tariff: 'VIP' | 'Стандарт' }>(newStudentTemplate);

  // Edit Modals State
  const [editingStudent, setEditingStudent] = useState<StudentDiscipline | null>(null);
  const [editingDay, setEditingDay] = useState<{ studentId: string; studentName: string; dayIndex: number; day: DayInfo } | null>(null);
  const [editingScript, setEditingScript] = useState<{ index: number; title: string; description: string; text: string } | null>(null);
  const [isAddingScript, setIsAddingScript] = useState(false);
  const [newScript, setNewScript] = useState({ title: '', description: '', text: '' });
  const [editingRule, setEditingRule] = useState<TaskRule | null>(null);
  const [isAddingRule, setIsAddingRule] = useState(false);
  const [newRule, setNewRule] = useState({ hashtag: '#', title: '', description: '', required: false, example: '' });
  const [editingScheduleTime, setEditingScheduleTime] = useState<{ key: keyof SystemSettings; label: string; value: string } | null>(null);

  useEffect(() => {
    writePersisted('reports', reports);
    writePersisted('all-reports', allReports);
    writePersisted('students', students);
    writePersisted('replies', replies);
    writePersisted('selected-student-id', selectedStudentId);
    writePersisted('selected-student', selectedStudent);
    writePersisted('active-admin', activeAdmin);
    writePersisted('task-rules', rules);
    writePersisted('system-settings', systemSettings);
    writePersisted('curator-scripts', curatorScripts);
    writePersisted('pending-sync-domains', pendingSyncDomains);
  }, [reports, allReports, students, replies, selectedStudentId, selectedStudent, activeAdmin, rules, systemSettings, curatorScripts, pendingSyncDomains]);

  useEffect(() => {
    saveAdminToken(adminToken);
    let isCurrent = true;

    const loadRemoteData = async () => {
      setApiConnection('checking');
      setApiConnectionMessage('Проверяем подключение');
      setApiSession(null);

      try {
        await api.getSystemStatus<Record<string, unknown>>();
      } catch {
        if (isCurrent) {
          setApiConnection('offline');
          setApiConnectionMessage('Сервер недоступен; изменения сохраняются в этом браузере');
        }
        return;
      }

      if (!adminToken.trim()) {
        if (isCurrent) {
          setApiConnection('needs-token');
          setApiConnectionMessage('Сервер доступен. Для синхронизации введите токен администратора');
        }
        return;
      }

      let currentSession: ApiAdminSession;
      try {
        currentSession = await api.getCurrentSession<ApiAdminSession>();
      } catch (error) {
        if (isCurrent) {
          const unauthorized = error instanceof Error && 'status' in error && error.status === 401;
          setApiConnection(unauthorized ? 'unauthorized' : 'offline');
          setApiConnectionMessage(unauthorized ? 'Токен не принят' : 'Не удалось проверить права администратора');
        }
        return;
      }
      if (!isCurrent) return;
      setApiSession(currentSession);
      setActiveAdmin(currentSession.adminName === 'Катя' ? 'Катя' : 'Ксения');

      const [reportsResult, studentsResult, rulesResult, settingsResult, scriptsResult] = await Promise.allSettled([
        api.getAllReports<ApiDailyReport[]>(),
        api.getStudents<ApiStudent[]>(),
        api.getTaskRules<ApiTaskRule[]>(),
        api.getSettings<ApiSystemSettings>(),
        api.getScripts<ApiCuratorScript[]>(),
      ]);

      if (!isCurrent) return;

      let loadedCount = 0;
      let unauthorized = false;
      const pending = new Set(pendingSyncDomains);
      const hasPending = (domain: LocalDataDomain) => pending.has(domain) || [...pending].some(key => key.startsWith(`${domain}:`));
      const pendingDomainCount = (['reports', 'students', 'task-rules', 'system-settings', 'curator-scripts'] as LocalDataDomain[])
        .filter(domain => hasPending(domain)).length;
      const results = [reportsResult, studentsResult, rulesResult, settingsResult, scriptsResult];
      results.forEach(result => {
        if (result.status === 'fulfilled') loadedCount += 1;
        else if (result.reason?.status === 401) unauthorized = true;
      });

      if (reportsResult.status === 'fulfilled' && !hasPending('reports')) {
        const mappedReports = reportsResult.value.map(mapApiReport);
        setAllReports(mappedReports);
        setReports(mappedReports.filter(report => report.status === 'PENDING'));
      }
      if (studentsResult.status === 'fulfilled' && !hasPending('students')) {
        setStudents(studentsResult.value.map(mapApiStudent));
      }
      if (rulesResult.status === 'fulfilled' && !hasPending('task-rules')) setRules(rulesResult.value.map(mapApiTaskRule));
      if (settingsResult.status === 'fulfilled' && !hasPending('system-settings')) setSystemSettings(mapApiSettings(settingsResult.value));
      if (scriptsResult.status === 'fulfilled' && !hasPending('curator-scripts')) setCuratorScripts(scriptsResult.value);

      if (unauthorized) {
        setApiConnection('unauthorized');
        setApiConnectionMessage('Токен не принят; локальные данные доступны, синхронизация отключена');
      } else if (loadedCount === results.length) {
        setApiConnection('connected');
        setApiConnectionMessage(pendingDomainCount > 0
          ? `Подключено; локальные изменения сохранены (${pendingDomainCount} раздела ожидают отправки)`
          : 'Подключено к серверу; данные синхронизированы');
      } else if (loadedCount > 0) {
        setApiConnection('connected');
        setApiConnectionMessage(pendingDomainCount > 0
          ? `Подключено; локальные изменения сохранены (${pendingDomainCount} раздела ожидают отправки)`
          : 'Подключено частично; недоступные данные восстановлены из локальной копии');
      } else {
        setApiConnection('offline');
        setApiConnectionMessage('API недоступно; используется локальная копия');
      }
    };

    void loadRemoteData();
    return () => {
      isCurrent = false;
    };
  }, [adminToken, apiRefreshKey, pendingSyncDomains]);

  const markLocalChange = (domain: LocalDataDomain, entityId?: string) => {
    const key = entityId ? `${domain}:${entityId}` : domain;
    setPendingSyncDomains(prev => prev.includes(key) ? prev : [...prev, key]);
  };

  const clearLocalChange = (domain: LocalDataDomain, entityId?: string) => {
    const key = entityId ? `${domain}:${entityId}` : domain;
    setPendingSyncDomains(prev => prev.filter(item => entityId
      ? item !== key
      : item !== domain && !item.startsWith(`${domain}:`)));
  };

  const saveDayEdit = async (studentId: string, dayIndex: number, updatedDay: DayInfo) => {
    const student = students.find(item => item.id === studentId);
    if (!student) return;
    const nextWeekDays = [...student.weekDays];
    nextWeekDays[dayIndex] = updatedDay;
    const activeDays = nextWeekDays.filter(day => day.status !== 'WEEKEND' && day.status !== 'PAUSED' && day.status !== 'PENDING');
    const onTimeCount = activeDays.filter(day => day.status === 'COMPLETED_ON_TIME').length;
    const lateCount = activeDays.filter(day => day.status === 'COMPLETED_LATE').length;
    const updatedStudent = {
      ...student,
      weekDays: nextWeekDays,
      disciplineRate: activeDays.length > 0 ? Math.round((onTimeCount * 100 + lateCount * 70) / activeDays.length) : 100,
    };
    setStudents(prev => prev.map(item => item.id === studentId ? updatedStudent : item));

    if (apiConnection === 'connected' && !studentId.startsWith('local-')) {
      try {
        await api.updateCalendarDay(studentId, {
          date: updatedDay.isoDate ?? updatedDay.date,
          day: updatedDay.day,
          status: updatedDay.status,
          details: updatedDay.missedDetails ?? '',
        });
        clearLocalChange('students', studentId);
        showToast(`Статус за ${updatedDay.date} сохранен`);
        return;
      } catch {
        markLocalChange('students', studentId);
        setApiConnectionMessage('Изменение дня сохранено локально; сервер не принял обновление');
      }
    } else {
      markLocalChange('students', studentId);
    }
    showToast(`Статус за ${updatedDay.date} сохранен локально`);
  };

  const saveStudentProfile = async (updated: StudentDiscipline) => {
    const normalized = withZoomSessionSummary(updated, updated.zoomSessions ?? []);
    const localKey = normalized.id;
    setStudents(prev => prev.map(s => (s.id === normalized.id ? normalized : s)));
    if (selectedStudent.id === normalized.id) {
      setSelectedStudent(normalized);
    }

    if (apiConnection === 'connected') {
      const payload = toApiStudentPayload(normalized);

      try {
        const response = normalized.id.startsWith('local-')
          ? await api.createStudent<ApiStudent>(payload)
          : await api.updateStudent<ApiStudent>(normalized.id, payload);
        const serverStudent = mapApiStudent(response);
        const saved = withZoomSessionSummary({ ...normalized, id: serverStudent.id, weekDays: serverStudent.weekDays }, normalized.zoomSessions ?? []);
        setStudents(prev => prev.map(student => student.id === normalized.id ? saved : student));
        if (selectedStudent.id === normalized.id) setSelectedStudent(saved);
        await api.replaceStudentZoomSessions(saved.id, serializeZoomSessions(saved.zoomSessions ?? []));
        clearLocalChange('students', localKey);
        showToast(`Профиль ${saved.fullName} сохранен на сервере`);
        return;
      } catch {
        markLocalChange('students', localKey);
        setApiConnectionMessage('Профиль сохранен в браузере; сервер пока недоступен');
      }
    } else {
      markLocalChange('students', localKey);
    }
    showToast(`Профиль ${normalized.fullName} сохранен локально`);
  };

  const addStudent = async () => {
    const fullName = newStudent.fullName.trim();
    const niche = newStudent.niche.trim();
    if (!fullName || !niche) {
      showToast('Укажите имя ученицы и ее нишу');
      return;
    }

    const localId = createLocalId('local');
    const initialIncome = Math.max(0, newStudent.initialIncome);
    const initialAvgTicket = Math.max(0, newStudent.initialAvgTicket);
    const localStudent: StudentDiscipline = {
      id: localId,
      fullName,
      avatarText: makeAvatarText(fullName),
      telegram: newStudent.telegram.trim() ? `@${newStudent.telegram.trim().replace(/^@/, '')}` : undefined,
      telegramChatId: newStudent.telegramChatId ? Number(newStudent.telegramChatId) : undefined,
      niche,
      tariff: newStudent.tariff,
      startDate: getTodayIsoDate(),
      isActive: true,
      initialIncome,
      currentIncome: initialIncome,
      initialAvgTicket,
      currentAvgTicket: initialAvgTicket,
      totalReels: 0,
      totalCarousels: 0,
      totalStorytells: 0,
      totalMailings: 0,
      totalNewClients: 0,
      disciplineRate: 100,
      weekDays: makeInitialWeekDays(),
      isOnPause: false,
      completedZoomsCount: 0,
      zoomSessions: [],
    };

    setStudents(prev => [...prev, localStudent]);
    setNewStudent(newStudentTemplate);
    setIsAddingStudent(false);

    if (apiConnection === 'connected') {
      try {
        const created = await api.createStudent<ApiStudent>({
          fullName,
          avatarText: localStudent.avatarText,
          tgUsername: localStudent.telegram?.replace(/^@/, '') ?? null,
          tgChatId: localStudent.telegramChatId ?? null,
          niche,
          tariff: newStudent.tariff === 'VIP' ? 'VIP' : 'STANDARD',
          initialIncome,
          initialAvgTicket,
          startDate: localStudent.startDate,
        });
        const serverStudent = withZoomSessionSummary(mapApiStudent(created), []);
        setStudents(prev => prev.map(student => student.id === localId ? serverStudent : student));
        clearLocalChange('students', localId);
        showToast(`Ученица ${fullName} создана на сервере`);
        return;
      } catch {
        markLocalChange('students', localId);
        setApiConnectionMessage('Новая ученица сохранена локально; сервер пока недоступен');
      }
    } else {
      markLocalChange('students', localId);
    }
    showToast(`Ученица ${fullName} сохранена локально`);
  };

  const saveScript = async (index: number, scriptData: { title: string; description: string; text: string }) => {
    const previous = curatorScripts[index];
    const updated = { ...previous, ...scriptData };
    const localKey = previous.id ?? `index-${index}`;
    setCuratorScripts(prev => prev.map((script, itemIndex) => itemIndex === index ? updated : script));

    if (apiConnection === 'connected') {
      try {
        const saved = updated.id
          ? await api.updateScript<ApiCuratorScript>(updated.id, updated)
          : await api.createScript<ApiCuratorScript>(updated);
        setCuratorScripts(prev => prev.map((script, itemIndex) => itemIndex === index ? saved : script));
        clearLocalChange('curator-scripts', localKey);
        showToast(`Скрипт «${scriptData.title}» сохранен на сервере`);
        return;
      } catch {
        markLocalChange('curator-scripts', localKey);
        setApiConnectionMessage('Скрипт сохранен локально; сервер не принял обновление');
      }
    } else {
      markLocalChange('curator-scripts', localKey);
    }
    showToast(`Скрипт «${scriptData.title}» сохранен локально`);
  };

  const addScript = async (scriptData: { title: string; description: string; text: string }) => {
    const localId = createLocalId('local-script');
    setCuratorScripts(prev => [...prev, { ...scriptData, id: localId }]);
    if (apiConnection === 'connected') {
      try {
        const saved = await api.createScript<ApiCuratorScript>(scriptData);
        setCuratorScripts(prev => prev.map(script => script.id === localId ? saved : script));
        clearLocalChange('curator-scripts', localId);
        showToast(`Скрипт «${scriptData.title}» добавлен на сервере`);
        return;
      } catch {
        markLocalChange('curator-scripts', localId);
        setApiConnectionMessage('Скрипт сохранен локально; сервер не принял создание');
      }
    } else {
      markLocalChange('curator-scripts', localId);
    }
    showToast(`Скрипт «${scriptData.title}» сохранен локально`);
  };

  const deleteScript = async (index: number) => {
    const script = curatorScripts[index];
    const localKey = script?.id?.startsWith('local-')
      ? `deleted-title:${encodeURIComponent(script.title)}`
      : script?.id ?? `index-${index}`;
    setCuratorScripts(prev => prev.filter((_, itemIndex) => itemIndex !== index));
    if (apiConnection === 'connected' && script?.id && !script.id.startsWith('local-')) {
      try {
        await api.deleteScript(script.id);
        clearLocalChange('curator-scripts', localKey);
        showToast('Скрипт удален с сервера');
        return;
      } catch {
        markLocalChange('curator-scripts', localKey);
        setApiConnectionMessage('Скрипт скрыт локально; сервер не принял удаление');
      }
    } else {
      markLocalChange('curator-scripts', localKey);
    }
    showToast('Скрипт удален локально');
  };

  const saveRule = async (updatedRule: TaskRule) => {
    setRules(prev => prev.map(r => (r.id === updatedRule.id ? updatedRule : r)));
    if (apiConnection === 'connected' && !updatedRule.id.startsWith('local-')) {
      try {
        const saved = await api.updateTaskRule<ApiTaskRule>(updatedRule.id, {
          hashtag: updatedRule.hashtag,
          title: updatedRule.title,
          description: updatedRule.description,
          required: updatedRule.required,
          example: updatedRule.example,
          isActive: updatedRule.enabled !== false,
        });
        setRules(prev => prev.map(rule => rule.id === updatedRule.id ? mapApiTaskRule(saved) : rule));
        clearLocalChange('task-rules', updatedRule.id);
        showToast(`Правило ${updatedRule.hashtag} сохранено на сервере`);
        return;
      } catch {
        markLocalChange('task-rules', updatedRule.id);
        setApiConnectionMessage('Правило сохранено локально; сервер не принял обновление');
      }
    } else {
      markLocalChange('task-rules', updatedRule.id);
    }
    showToast(`Правило ${updatedRule.hashtag} сохранено локально`);
  };

  const addRule = async (ruleData: { hashtag: string; title: string; description: string; required: boolean; example: string }) => {
    const localRule: TaskRule = { ...ruleData, id: createLocalId('local-rule'), enabled: true };
    setRules(prev => [...prev, localRule]);
    if (apiConnection === 'connected') {
      try {
        const saved = await api.createTaskRule<ApiTaskRule>(ruleData);
        setRules(prev => prev.map(rule => rule.id === localRule.id ? mapApiTaskRule(saved) : rule));
        clearLocalChange('task-rules', localRule.id);
        showToast(`Хэштег ${ruleData.hashtag} добавлен на сервере`);
        return;
      } catch {
        markLocalChange('task-rules', localRule.id);
        setApiConnectionMessage('Правило сохранено локально; сервер не принял создание');
      }
    } else {
      markLocalChange('task-rules', localRule.id);
    }
    showToast(`Хэштег ${ruleData.hashtag} сохранен локально`);
  };

  const deleteRule = async (ruleId: string, tag: string) => {
    setRules(prev => prev.filter(r => r.id !== ruleId));
    if (apiConnection === 'connected' && !ruleId.startsWith('local-')) {
      try {
        await api.deleteTaskRule(ruleId);
        clearLocalChange('task-rules', ruleId);
        showToast(`Хэштег ${tag} удален с сервера`);
        return;
      } catch {
        markLocalChange('task-rules', ruleId);
        setApiConnectionMessage('Правило удалено локально; сервер не принял удаление');
      }
    } else {
      markLocalChange('task-rules', ruleId);
    }
    showToast(`Хэштег ${tag} удален локально`);
  };

  const persistSystemSettings = async (settings: SystemSettings, label: string) => {
    if (apiConnection === 'connected') {
      try {
        const saved = await api.updateSettings<ApiSystemSettings>(settings);
        setSystemSettings(mapApiSettings(saved));
        clearLocalChange('system-settings');
        showToast(`${label}: сохранено на сервере`);
        return;
      } catch {
        markLocalChange('system-settings');
        setApiConnectionMessage('Настройки сохранены локально; сервер не принял обновление');
      }
    } else {
      markLocalChange('system-settings');
    }
    showToast(`${label}: сохранено локально`);
  };

  const updateScheduleTime = (key: keyof SystemSettings, val: string, label: string) => {
    const nextSettings = { ...systemSettings, [key]: val };
    setSystemSettings(nextSettings);
    void persistSystemSettings(nextSettings, `${label} (${val})`);
  };

  const toggleRuleEnabled = (ruleId: string) => {
    const rule = rules.find(item => item.id === ruleId);
    if (rule) void saveRule({ ...rule, enabled: rule.enabled === false });
  };

  const toggleRuleRequired = (ruleId: string) => {
    const rule = rules.find(item => item.id === ruleId);
    if (rule) void saveRule({ ...rule, required: !rule.required });
  };

  const toggleSystemSetting = (key: keyof SystemSettings, label: string) => {
    const nextSettings = { ...systemSettings, [key]: !systemSettings[key] };
    setSystemSettings(nextSettings);
    void persistSystemSettings(nextSettings, label);
  };

  const markStudentZoomCompleted = async (studentId: string, studentName: string) => {
    const student = students.find(item => item.id === studentId);
    if (!student) return;
    const sessions = getStudentZoomSessions(student);
    const nextSessionIndex = sessions.findIndex(session => !session.isCompleted);
    if (nextSessionIndex < 0) {
      showToast(`Для ${studentName} нет незавершенных созвонов`);
      return;
    }
    const nextSessions = sessions.map((session, index) =>
      index === nextSessionIndex ? { ...session, isCompleted: true } : session,
    );
    const updated = withZoomSessionSummary(student, nextSessions);
    setStudents(prev => prev.map(item => item.id === studentId ? updated : item));

    if (apiConnection === 'connected' && !studentId.startsWith('local-')) {
      try {
        await api.replaceStudentZoomSessions(studentId, serializeZoomSessions(nextSessions));
        clearLocalChange('students', studentId);
        showToast(`Созвон ${nextSessionIndex + 1}/${sessions.length} сохранен на сервере`);
        return;
      } catch {
        markLocalChange('students', studentId);
        setApiConnectionMessage('Статус созвона сохранен локально; сервер не принял обновление');
      }
    } else {
      markLocalChange('students', studentId);
    }
    showToast(`Созвон ${nextSessionIndex + 1}/${sessions.length} сохранен локально`);
  };

  const updateEditingZoomSession = (index: number, changes: Partial<ZoomSessionRecord>) => {
    setEditingStudent(current => {
      if (!current) return current;
      const sessions = (current.zoomSessions ?? []).map((session, sessionIndex) =>
        sessionIndex === index ? { ...session, ...changes } : session,
      );
      return withZoomSessionSummary(current, sessions);
    });
  };

  const addEditingZoomSession = () => {
    setEditingStudent(current => {
      if (!current) return current;
      const sessions = current.zoomSessions ?? [];
      return withZoomSessionSummary(current, [
        ...sessions,
        {
          callNumber: sessions.length + 1,
          title: `Новый этап ${sessions.length + 1}`,
          date: '',
          isCompleted: false,
        },
      ]);
    });
  };

  const removeEditingZoomSession = (index: number) => {
    setEditingStudent(current => {
      if (!current) return current;
      const sessions = (current.zoomSessions ?? []).filter((_, sessionIndex) => sessionIndex !== index);
      return withZoomSessionSummary(current, sessions);
    });
  };

  const showToast = (msg: string) => {
    if (toastTimerRef.current) {
      clearTimeout(toastTimerRef.current);
    }
    setToastMessage(msg);
    toastTimerRef.current = setTimeout(() => {
      setToastMessage(null);
      toastTimerRef.current = null;
    }, 3000);
  };

  const syncPendingChanges = async (): Promise<boolean> => {
    if (pendingSyncDomains.length === 0) return true;
    const completedKeys: string[] = [];
    const getEntityKey = (domain: LocalDataDomain, key: string) => key.startsWith(`${domain}:`) ? key.slice(domain.length + 1) : '';

    for (const key of pendingSyncDomains) {
      try {
        if (key.startsWith('students:')) {
          const localId = getEntityKey('students', key);
          const localStudent = students.find(student => student.id === localId);
          if (!localStudent) {
            completedKeys.push(key);
            continue;
          }

          const remoteStudents = await api.getStudents<ApiStudent[]>();
          const username = localStudent.telegram?.replace(/^@/, '').toLowerCase();
          const matchingRemote = localStudent.id.startsWith('local-')
            ? remoteStudents.find(remote =>
                (localStudent.telegramChatId && remote.tgChatId === localStudent.telegramChatId)
                || (username && remote.tgUsername?.replace(/^@/, '').toLowerCase() === username)
                || remote.fullName.trim().toLowerCase() === localStudent.fullName.trim().toLowerCase(),
              )
            : undefined;

          let serverStudent: ApiStudent;
          if (matchingRemote) {
            serverStudent = await api.updateStudent<ApiStudent>(matchingRemote.id, toApiStudentPayload({ ...localStudent, id: matchingRemote.id }));
          } else if (localStudent.id.startsWith('local-')) {
            const created = await api.createStudent<ApiStudent>({
              fullName: localStudent.fullName,
              avatarText: localStudent.avatarText,
              tgUsername: localStudent.telegram?.replace(/^@/, '') ?? null,
              tgChatId: localStudent.telegramChatId ?? null,
              niche: localStudent.niche,
              tariff: localStudent.tariff === 'VIP' ? 'VIP' : 'STANDARD',
              initialIncome: localStudent.initialIncome,
              initialAvgTicket: localStudent.initialAvgTicket,
              startDate: localStudent.startDate ?? new Date().toISOString().slice(0, 10),
            });
            serverStudent = await api.updateStudent<ApiStudent>(created.id, toApiStudentPayload({ ...localStudent, id: created.id }));
          } else {
            serverStudent = await api.updateStudent<ApiStudent>(localStudent.id, toApiStudentPayload(localStudent));
          }

          const serverId = serverStudent.id;
          await api.replaceStudentZoomSessions(serverId, serializeZoomSessions(localStudent.zoomSessions ?? []));
          const serverCalendar = await api.getCalendar<DayInfo[]>(serverId);
          for (const localDay of localStudent.weekDays) {
            const serverDay = serverCalendar.find(day => day.isoDate === localDay.isoDate || (day.day === localDay.day && day.date === localDay.date));
            if (serverDay && (serverDay.status !== localDay.status || serverDay.missedDetails !== localDay.missedDetails)) {
              await api.updateCalendarDay(serverId, {
                date: localDay.isoDate ?? localDay.date,
                day: localDay.day,
                status: localDay.status,
                details: localDay.missedDetails ?? '',
              });
            }
          }

          const savedStudent = withZoomSessionSummary({ ...localStudent, id: serverId }, localStudent.zoomSessions ?? []);
          setStudents(prev => prev.map(student => student.id === localId ? savedStudent : student));
          if (selectedStudent.id === localId) setSelectedStudent(savedStudent);
          completedKeys.push(key);
          continue;
        }

        if (key.startsWith('task-rules:')) {
          const entityId = getEntityKey('task-rules', key);
          const localRule = rules.find(rule => rule.id === entityId);
          const remoteRules = await api.getTaskRules<ApiTaskRule[]>();
          if (localRule) {
            const matchingRemote = localRule.id.startsWith('local-')
              ? remoteRules.find(rule => rule.hashtag.toLowerCase() === localRule.hashtag.toLowerCase())
              : undefined;
            const payload = {
              hashtag: localRule.hashtag,
              title: localRule.title,
              description: localRule.description,
              required: localRule.required,
              example: localRule.example,
              isActive: localRule.enabled !== false,
            };
            const saved = matchingRemote
              ? await api.updateTaskRule<ApiTaskRule>(matchingRemote.id, payload)
              : localRule.id.startsWith('local-')
                ? await api.createTaskRule<ApiTaskRule>(payload)
                : await api.updateTaskRule<ApiTaskRule>(localRule.id, payload);
            setRules(prev => prev.map(rule => rule.id === localRule.id ? mapApiTaskRule(saved) : rule));
          } else if (!entityId.startsWith('local-') && remoteRules.some(rule => rule.id === entityId)) {
            await api.deleteTaskRule(entityId);
          }
          completedKeys.push(key);
          continue;
        }

        if (key.startsWith('curator-scripts:')) {
          const entityId = getEntityKey('curator-scripts', key);
          const localScript = curatorScripts.find(script => script.id === entityId);
          const remoteScripts = await api.getScripts<ApiCuratorScript[]>();
          if (entityId.startsWith('deleted-title:')) {
            const deletedTitle = decodeURIComponent(entityId.slice('deleted-title:'.length));
            const matching = remoteScripts.find(script => script.title === deletedTitle);
            if (matching?.id) await api.deleteScript(matching.id);
          } else if (localScript) {
            const matchingRemote = localScript.id?.startsWith('local-') || !localScript.id
              ? remoteScripts.find(script => script.title === localScript.title)
              : undefined;
            const payload = {
              title: localScript.title,
              description: localScript.description,
              text: localScript.text,
              category: localScript.category ?? 'Кураторские',
            };
            const saved = matchingRemote?.id
              ? await api.updateScript<ApiCuratorScript>(matchingRemote.id, payload)
              : localScript.id?.startsWith('local-') || !localScript.id
                ? await api.createScript<ApiCuratorScript>(payload)
                : await api.updateScript<ApiCuratorScript>(localScript.id, payload);
            setCuratorScripts(prev => prev.map(script => script.id === localScript.id ? saved : script));
          } else if (entityId.startsWith('script-')) {
            await api.deleteScript(entityId);
          }
          completedKeys.push(key);
          continue;
        }

        if (key.startsWith('system-settings')) {
          const saved = await api.updateSettings<ApiSystemSettings>(systemSettings);
          setSystemSettings(mapApiSettings(saved));
          completedKeys.push(key);
          continue;
        }

        if (key.startsWith('reports:')) {
          const reportId = getEntityKey('reports', key);
          const localReport = allReports.find(report => report.id === reportId);
          if (!localReport) {
            completedKeys.push(key);
            continue;
          }
          const remoteReports = (await api.getAllReports<ApiDailyReport[]>()).map(mapApiReport);
          const remoteReport = remoteReports.find(report => report.id === reportId);
          if (remoteReport && localReport.status !== remoteReport.status) {
            if (localReport.status === 'APPROVED') {
              await api.approveReport(reportId, localReport.mentorReply);
            } else if (localReport.status === 'REJECTED') {
              await api.rejectReport(reportId, localReport.mentorReply || 'Отчет отклонен наставником');
            }
          }
          completedKeys.push(key);
        }
      } catch (error) {
        setApiConnectionMessage(error instanceof Error ? `Не удалось отправить часть локальных изменений: ${error.message}` : 'Не удалось отправить часть локальных изменений');
      }
    }

    setPendingSyncDomains(previous => previous.filter(key => !completedKeys.includes(key)));
    return completedKeys.length === pendingSyncDomains.length;
  };

  const handleApiSync = async () => {
    if (!adminToken.trim()) {
      setApiConnection('needs-token');
      setApiConnectionMessage('Для синхронизации введите токен администратора');
      showToast('Локальная копия сохранена; сначала подключите сервер');
      return;
    }

    setIsApiSyncing(true);
    try {
      const localSyncCompleted = await syncPendingChanges();
      const result = await api.syncTelegram<{ message?: string }>();
      setApiRefreshKey(value => value + 1);
      showToast(localSyncCompleted
        ? result.message || 'Синхронизация завершена'
        : 'Часть локальных изменений осталась в очереди повторной отправки');
    } catch (error) {
      const unauthorized = error instanceof Error && 'status' in error && error.status === 401;
      setApiConnection(unauthorized ? 'unauthorized' : 'offline');
      setApiConnectionMessage(unauthorized
        ? 'Токен не принят; локальная копия сохранена'
        : 'Не удалось связаться с сервером; локальная копия сохранена');
      showToast(unauthorized ? 'Токен администратора не принят' : 'Сервер недоступен; данные оставлены локально');
    } finally {
      setIsApiSyncing(false);
    }
  };

  const sendStudentMessage = async (student: StudentDiscipline, text: string) => {
    if (apiConnection === 'connected' && !student.id.startsWith('local-')) {
      try {
        const result = await api.sendStudentMessage<{ delivered: boolean; message: string }>(student.id, text);
        if (result.delivered) {
          showToast(`Telegram: сообщение отправлено ${student.fullName}`);
          return;
        }
        setApiConnectionMessage(result.message);
      } catch {
        setApiConnectionMessage('Сообщение не отправлено; текст сохранен в буфере при возможности');
      }
    }

    try {
      await navigator.clipboard?.writeText(text);
      showToast(`Сообщение не отправлено. Текст скопирован для ${student.fullName}`);
    } catch {
      showToast('Telegram недоступен; скопируйте текст вручную');
    }
  };

  const handleApprove = (reportId: string, name: string) => {
    const reply = replies[reportId]?.trim() ?? '';
    setReports(prev => prev.filter(r => r.id !== reportId));
    setAllReports(prev => prev.map(report => report.id === reportId ? { ...report, status: 'APPROVED', mentorReply: reply || undefined } : report));
    setReplies(prev => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });

    if (apiConnection === 'connected') {
      api.approveReport<{ success: boolean; status: string; tgMessageDelivered?: boolean | null }>(reportId, reply || undefined)
        .then(result => {
          clearLocalChange('reports', reportId);
          if (reply && !result.tgMessageDelivered) {
            setApiConnectionMessage(`Отчет ${name} принят, но Telegram не подтвердил доставку`);
          }
        })
        .catch((_error: unknown) => {
          markLocalChange('reports', reportId);
          setApiConnectionMessage('Одобрение сохранено локально; сервер временно недоступен');
        });
    } else {
      markLocalChange('reports', reportId);
    }

    if (reply) {
      showToast(`Отчёт принят! Ответ отправлен ${name} в Telegram.`);
    } else {
      showToast(`Отчёт ${name} принят без комментария.`);
    }
  };

  const handleReject = async (reportId: string, name: string) => {
    const reason = replies[reportId]?.trim() ?? '';
    if (!reason) {
      showToast('Укажите причину возврата на доработку');
      return;
    }
    let messageDelivered = false;
    if (apiConnection === 'connected') {
      try {
        const result = await api.rejectReport<{ success: boolean; tgMessageDelivered?: boolean | null }>(reportId, reason);
        if (!result.success) throw new Error('Сервер не подтвердил возврат отчета');
        messageDelivered = result.tgMessageDelivered === true;
      } catch {
        markLocalChange('reports', reportId);
        setAllReports(prev => prev.map(report => report.id === reportId ? { ...report, status: 'REJECTED', mentorReply: reason } : report));
        setReports(prev => prev.filter(report => report.id !== reportId));
        setReplies(prev => {
          const next = { ...prev };
          delete next[reportId];
          return next;
        });
        showToast(`Возврат ${name} сохранен локально; сервер не подтвердил отправку`);
        return;
      }
    } else {
      markLocalChange('reports', reportId);
    }
    setReports(prev => prev.filter(report => report.id !== reportId));
    setAllReports(prev => prev.map(report => report.id === reportId ? { ...report, status: 'REJECTED', mentorReply: reason } : report));
    setReplies(prev => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });
    showToast(apiConnection === 'connected'
      ? messageDelivered ? `Отчет возвращен ${name}; сообщение доставлено в Telegram` : `Отчет возвращен ${name}; сообщение не доставлено в Telegram`
      : `Отчет возвращен локально; сообщение ${name} не отправлено`);
  };

  const toggleStudentPause = async (studentId: string) => {
    const student = students.find(item => item.id === studentId);
    if (!student) return;
    const nextState = !student.isOnPause;
    const updated = { ...student, isOnPause: nextState, pauseReason: nextState ? student.pauseReason || 'На паузе' : undefined };
    setStudents(prev => prev.map(item => item.id === studentId ? updated : item));

    if (apiConnection === 'connected' && !studentId.startsWith('local-')) {
      try {
        await api.togglePause(studentId, { isPaused: nextState, pauseReason: updated.pauseReason ?? null });
        clearLocalChange('students', studentId);
        showToast(nextState ? `${student.fullName} поставлена на паузу` : `${student.fullName} возвращена в активный трек`);
        return;
      } catch {
        markLocalChange('students', studentId);
        setApiConnectionMessage('Статус паузы сохранен локально; сервер не принял обновление');
      }
    } else {
      markLocalChange('students', studentId);
    }
    showToast(nextState ? `${student.fullName} поставлена на паузу локально` : `${student.fullName} возвращена локально`);
  };

  const handleCopyCase = () => {
    const s = selectedStudent;
    const g = s.initialIncome <= 0 ? '1.0' : calculateMultiplier(s.currentIncome, s.initialIncome);
    const text =
      `КЕЙС ПОТОКА: ${s.fullName}\n` +
      `Ниша: ${s.niche}\n\n` +
      `Точка А: ${s.initialIncome.toLocaleString('ru-RU')} ₽ -> Точка Б: ${s.currentIncome.toLocaleString('ru-RU')} ₽ (РОСТ x${g}!)\n` +
      `Средний чек: вырос с ${s.initialAvgTicket.toLocaleString('ru-RU')} ₽ до ${s.currentAvgTicket.toLocaleString('ru-RU')} ₽\n` +
      `Проверено: ${s.totalReels} Reels, ${s.totalCarousels} каруселей, ${s.totalStorytells} сторителлов\n` +
      `Исходящий трафик: ${s.totalMailings} рассылок по скрипту\n` +
      `Прирост новых клиентов: +${s.totalNewClients}\n` +
      `Дисциплина сдачи: ${s.disciplineRate}%\n\n` +
      `«Она просто перестала сливать дедлайны и внедрила ежедневный трекинг ключевых действий»`;

    if (typeof navigator !== 'undefined' && navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(text)
        .then(() => showToast('Текст скопирован для сторис'))
        .catch(() => showToast('Текст скопирован'));
    } else {
      showToast('Текст скопирован');
    }
  };

  const q = searchQuery.trim().toLowerCase();
  const filtered = students.filter(s => {
    const matchesSearch =
      s.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.fullName.toLowerCase().includes(q) ||
      s.niche.toLowerCase().includes(q);
    if (!matchesSearch) return false;

    if (studentsFilter === 'perfect') return s.disciplineRate >= 95;
    if (studentsFilter === 'attention') return s.disciplineRate < 75 || s.weekDays.some(d => d.status === 'MISSED');
    if (studentsFilter === 'pause') return s.isOnPause;
    if (studentsFilter === 'zooms') return Boolean(s.zoomSessions?.length);
    return true;
  });

  const tabs: { id: TabType; label: string; icon: typeof Inbox; count?: number }[] = [
    { id: 'inbox', label: 'На проверку', icon: Inbox, count: reports.length },
    { id: 'students', label: 'Ученицы', icon: Users },
    { id: 'analytics', label: 'Аналитика', icon: BarChart3 },
    { id: 'tasks', label: 'Хэштеги', icon: Hash },
  ];

  const periodReports = getReportRange(allReports, analyticsPeriod);
  const currentIncomeTotal = students.reduce((sum, student) => sum + student.currentIncome, 0);
  const initialIncomeTotal = students.reduce((sum, student) => sum + student.initialIncome, 0);
  const periodSalesTotal = periodReports.reduce((sum, report) => sum + report.salesAmount, 0);
  const periodClientsTotal = periodReports.reduce((sum, report) => sum + report.newClientsCount, 0);
  const totalReelsCount = analyticsPeriod === 'all'
    ? students.reduce((sum, student) => sum + student.totalReels, 0)
    : getReportContentCount(periodReports, 'reel');
  const totalCarouselsCount = analyticsPeriod === 'all'
    ? students.reduce((sum, student) => sum + student.totalCarousels, 0)
    : getReportContentCount(periodReports, 'carousel');
  const totalStorytellsCount = analyticsPeriod === 'all'
    ? students.reduce((sum, student) => sum + student.totalStorytells, 0)
    : getReportContentCount(periodReports, 'storytell');
  const totalNewClientsCount = analyticsPeriod === 'all'
    ? students.reduce((sum, student) => sum + student.totalNewClients, 0)
    : periodClientsTotal;
  const totalMailingsCount = analyticsPeriod === 'all'
    ? students.reduce((sum, student) => sum + student.totalMailings, 0)
    : periodReports.reduce((sum, report) => sum + report.mailingsCount, 0);
  const analyticsRevenue = analyticsPeriod === 'all' ? currentIncomeTotal : periodSalesTotal;
  const averageTicket = analyticsPeriod === 'all'
    ? students.length > 0 ? Math.round(students.reduce((sum, student) => sum + student.currentAvgTicket, 0) / students.length) : 0
    : periodClientsTotal > 0 ? Math.round(periodSalesTotal / periodClientsTotal) : 0;
  const activeBurnoutCount = periodReports.filter(report =>
    /выгорани|устал|срыв|нет сил/i.test(`${report.reflectionSummary?.emotions ?? ''} ${report.voiceTranscript ?? ''}`),
  ).length;
  const atRiskStudents = filtered.filter(student =>
    student.disciplineRate < 75 || student.weekDays.some(day => day.status === 'MISSED'),
  );

  const getStudentPeriodStats = (student: StudentDiscipline) => {
    const studentReports = allReports.filter(report => report.studentId === student.id);
    const latestReport = [...studentReports].sort((left, right) => getReportDate(left).localeCompare(getReportDate(right))).at(-1);
    if (analyticsPeriod === 'all') {
      return {
        income: student.currentIncome,
        newClients: student.totalNewClients,
        reels: student.totalReels,
        carousels: student.totalCarousels,
        storytells: student.totalStorytells,
        mailings: student.totalMailings,
        disciplineRate: student.disciplineRate,
        reflectionSentiment: latestReport?.reflectionSummary?.emotions ?? '',
        keyWin: latestReport?.aiSummary ?? '',
      };
    }
    const rangeStudentReports = periodReports.filter(report => report.studentId === student.id);
    const latestPeriodReport = [...rangeStudentReports].sort((left, right) => getReportDate(left).localeCompare(getReportDate(right))).at(-1);
    return {
      income: rangeStudentReports.reduce((sum, report) => sum + report.salesAmount, 0),
      newClients: rangeStudentReports.reduce((sum, report) => sum + report.newClientsCount, 0),
      reels: getReportContentCount(rangeStudentReports, 'reel'),
      carousels: getReportContentCount(rangeStudentReports, 'carousel'),
      storytells: getReportContentCount(rangeStudentReports, 'storytell'),
      mailings: rangeStudentReports.reduce((sum, report) => sum + report.mailingsCount, 0),
      disciplineRate: student.disciplineRate,
      reflectionSentiment: latestPeriodReport?.reflectionSummary?.emotions ?? '',
      keyWin: latestPeriodReport?.aiSummary ?? '',
    };
  };
  const selectedStudentPeriodStats = getStudentPeriodStats(selectedStudent);

  return (
    <div className="app-shell min-h-screen bg-[#fcfdfd] text-slate-800">

      {/* ─── Sidebar + Main Layout ─── */}
      <div className="app-layout flex">

        {/* Sidebar */}
        <aside className="app-sidebar hidden md:flex flex-col w-64 min-h-screen bg-white border-r border-slate-200/60 px-4 py-8 fixed left-0 top-0 z-20">
          <div className="px-3 mb-6">
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">Панель наставника</h1>
            <p className="text-xs text-slate-500 mt-0.5">Рабочий трекер куратора</p>
          </div>

          <nav className="flex-1 space-y-1">
            {tabs.map(t => (
              <button
                key={t.id}
                onClick={() => setActiveTab(t.id)}
                className={`app-nav-link w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm transition-colors cursor-pointer ${
                  activeTab === t.id
                    ? 'is-active bg-neutral-900 text-white font-medium'
                    : 'text-neutral-500 hover:text-neutral-900 hover:bg-slate-50'
                }`}
              >
                <t.icon className="w-4 h-4 shrink-0" strokeWidth={1.8} />
                <span className="font-medium text-sm">{t.label}</span>
                {t.count !== undefined && t.count > 0 && (
                  <span className={`ml-auto text-xs font-bold px-2 py-0.5 rounded-md ${
                    activeTab === t.id ? 'bg-white/20 text-white' : 'bg-indigo-50 text-indigo-700'
                  }`}>
                    {t.count}
                  </span>
                )}
              </button>
            ))}
          </nav>

          {/* Admin Switcher & Session */}
          <div className="px-3 pt-5 border-t border-slate-100 mt-4 space-y-3.5">
            <div className="text-xs text-slate-400 font-medium uppercase tracking-wider">Профиль</div>

            {apiConnection === 'connected' && apiSession ? (
              <div className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-900">
                {apiSession.adminName}
                <span className="ml-2 text-xs font-normal text-slate-500">{apiSession.role === 'MENTOR' ? 'Наставник' : 'Куратор'}</span>
              </div>
            ) : (
            <div className="grid grid-cols-2 gap-1.5 bg-slate-100/80 p-1 rounded-xl text-xs font-medium">
              <button
                onClick={() => {
                  setActiveAdmin('Ксения');
                  showToast('Активный профиль: Ксения (Главный ментор)');
                }}
                className={`py-1.5 rounded-lg transition cursor-pointer text-center ${
                  activeAdmin === 'Ксения'
                    ? 'bg-white text-slate-900 font-bold'
                    : 'text-slate-500 hover:text-slate-900'
                }`}
              >
                Ксения
              </button>
              <button
                onClick={() => {
                  setActiveAdmin('Катя');
                  showToast('Активный профиль: Катя (Куратор / Управление)');
                }}
                className={`py-1.5 rounded-lg transition cursor-pointer text-center ${
                  activeAdmin === 'Катя'
                    ? 'bg-white text-slate-900 font-bold'
                    : 'text-slate-500 hover:text-slate-900'
                }`}
              >
                Катя
              </button>
            </div>
            )}

            {!(apiConnection === 'connected' && apiSession) && (
              <div className="flex items-center gap-3 pt-1">
                <div className="w-9 h-9 rounded-full bg-slate-900 text-white flex items-center justify-center text-xs font-bold">{activeAdmin[0]}</div>
                <div>
                  <div className="text-sm font-semibold text-slate-900">{activeAdmin}</div>
                  <div className="text-xs text-slate-500">Локальный режим</div>
                </div>
              </div>
            )}
          </div>
        </aside>

        {/* Mobile top bar */}
        <div className="md:hidden fixed top-0 left-0 right-0 z-20 bg-white border-b border-slate-200/60 px-4 py-3.5">
          <div className="flex items-center justify-between mb-2.5">
            <div>
              <h1 className="text-base font-bold text-slate-900 tracking-tight">Панель наставника</h1>
              <p className="text-xs text-slate-500">
                {apiSession ? `${apiSession.adminName} · ${apiSession.role === 'MENTOR' ? 'Наставник' : 'Куратор'}` : `${activeAdmin} · Локальный режим`}
              </p>
            </div>
            <button
              onClick={() => void handleApiSync()}
              disabled={isApiSyncing}
              aria-label="Синхронизировать данные"
              className="text-slate-500 hover:text-slate-900 transition cursor-pointer p-1.5"
            >
              <RefreshCw className="w-4 h-4" strokeWidth={1.8} />
            </button>
          </div>
          {apiConnection !== 'connected' || !apiSession ? (
          <div className="grid grid-cols-2 gap-1.5 bg-slate-100 p-1 rounded-xl text-xs font-semibold mb-2.5">
            <button
              onClick={() => {
                setActiveAdmin('Ксения');
                showToast('Активный профиль: Ксения (Главный ментор)');
              }}
              className={`py-1.5 rounded-lg transition cursor-pointer text-center ${
                activeAdmin === 'Ксения' ? 'bg-white text-slate-900 font-bold ' : 'text-slate-500'
              }`}
            >
              Ксения
            </button>
            <button
              onClick={() => {
                setActiveAdmin('Катя');
                showToast('Активный профиль: Катя (Куратор потока)');
              }}
              className={`py-1.5 rounded-lg transition cursor-pointer text-center ${
                activeAdmin === 'Катя' ? 'bg-white text-slate-900 font-bold ' : 'text-slate-500'
              }`}
            >
              Катя
            </button>
          </div>
          ) : null}
          <div className="flex gap-2 overflow-x-auto pb-1">
            {tabs.map(t => (
              <button
                key={t.id}
                onClick={() => setActiveTab(t.id)}
                className={`px-3.5 py-2 rounded-lg text-xs font-semibold transition shrink-0 cursor-pointer ${
                    activeTab === t.id
                      ? 'is-active bg-neutral-900 text-white'
                    : 'text-neutral-500 hover:text-neutral-900 hover:bg-slate-100'
                }`}
              >
                {t.label}
                {t.count !== undefined && t.count > 0 && (
                  <span className="ml-1.5 text-xs font-bold opacity-80">{t.count}</span>
                )}
              </button>
            ))}
          </div>
        </div>

        {/* Main content */}
        <main className="app-main flex-1 md:ml-64 pt-28 md:pt-0">
          <div className="app-content max-w-5xl mx-auto px-6 sm:px-10 py-10">

            {/* ════════════ TAB: INBOX ════════════ */}
                {activeTab === 'inbox' && (
              <div>
                <div className="mb-8 flex items-center justify-between">
                  <div>
                    <h2 className="text-2xl font-bold tracking-tight text-slate-900">На проверку</h2>
                    <p id="inbox-subtitle" className="text-sm sm:text-base text-slate-500 mt-1">
                      {reports.length > 0
                        ? `${reports.length} ${reports.length === 1 ? 'черновик ожидает' : 'черновика ожидают'} проверки`
                        : 'Все отчёты проверены'
                      }
                    </p>
                  </div>
                </div>

                {reports.length === 0 ? (
                  <div className="text-center py-20 bg-white rounded-2xl border border-slate-200/60 p-8">
                    <CheckCheck className="w-8 h-8 text-slate-400 mx-auto mb-3" strokeWidth={1.5} />
                    <p className="text-lg font-bold text-slate-800">Все черновики проверены</p>
                    <p className="text-sm text-slate-500 mt-1">Всё проверено. Ученицы получили обратную связь в чатах.</p>
                    <button
                      onClick={() => setReports(initialReports)}
                      className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-indigo-600 hover:text-indigo-800 transition cursor-pointer"
                    >
                      <RotateCcw className="w-4 h-4" />
                      Загрузить демо-отчёты
                    </button>
                  </div>
                ) : (
                  <div className="space-y-5">
                    {reports.map((r) => (
                      <div key={r.id} className="bg-white rounded-2xl border border-slate-200/60 overflow-hidden">
                        {/* Header */}
                        <div className="px-6 py-4.5 flex items-center justify-between border-b border-slate-100">
                          <div className="flex items-center gap-3.5">
                            <div className="w-10 h-10 rounded-full bg-slate-100 text-slate-700 flex items-center justify-center text-xs font-bold shrink-0">
                              {r.avatarText}
                            </div>
                            <div>
                              <div className="text-base sm:text-lg font-bold text-slate-900">{r.studentName}</div>
                              <div className="flex items-center gap-2.5 mt-0.5">
                                <span className="text-xs sm:text-sm text-slate-500">@{r.tgUsername}</span>
                                <span className={`text-xs font-medium px-2 py-0.5 rounded-md border ${
                                  r.isOnTime
                                    ? 'bg-emerald-50 text-emerald-700 border-emerald-100'
                                    : 'bg-amber-50 text-amber-700 border-amber-100'
                                }`}>
                                  {r.isOnTime ? 'в срок' : 'опоздание'}
                                </span>
                              </div>
                            </div>
                          </div>

                          <a
                            href={`https://t.me/${r.tgUsername}`}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1.5 text-xs sm:text-sm font-medium text-slate-600 hover:text-indigo-600 bg-slate-50 hover:bg-slate-100 border border-slate-200/60 px-3.5 py-1.5 rounded-xl transition"
                          >
                            <span>В Telegram</span>
                            <ExternalLink className="w-3.5 h-3.5" strokeWidth={1.8} />
                          </a>
                        </div>

                        {/* Content */}
                        <div className="px-6 py-5 space-y-4.5">
                          {/* Content Drafts List */}
                          {r.contentDrafts && r.contentDrafts.length > 0 && (
                            <div className="space-y-2">
                              <div className="text-xs font-medium text-slate-400 uppercase tracking-wider">
                                Сдано на проверку:
                              </div>
                              {r.contentDrafts.map(draft => {
                                const badge = getContentTypeBadge(draft.type);
                                return (
                                  <div
                                    key={draft.id}
                                    className="flex items-center justify-between py-2.5 px-3.5 rounded-xl bg-slate-50 border border-slate-100 text-sm"
                                  >
                                    <div className="flex items-center gap-2.5 truncate mr-2">
                                      <span className={`text-xs font-semibold px-2.5 py-1 rounded-md border shrink-0 inline-flex items-center gap-1.5 ${badge.bg}`}>
                                        <badge.icon className="w-3.5 h-3.5 shrink-0" strokeWidth={1.8} />
                                        <span>{badge.label}</span>
                                      </span>
                                      <span className="text-slate-800 font-medium truncate">
                                        {draft.label}
                                      </span>
                                    </div>

                                    {draft.isDuplicate ? (
                                      <span className="flex items-center gap-1.5 text-rose-600 text-xs font-semibold shrink-0 bg-rose-50 border border-rose-100 px-2.5 py-1 rounded-md">
                                        <AlertTriangle className="w-3.5 h-3.5" strokeWidth={2} />
                                        <span>дубликат ({draft.duplicateDate})</span>
                                      </span>
                                    ) : (
                                      <span className="flex items-center gap-1.5 text-emerald-600 text-xs font-semibold shrink-0 bg-emerald-50 border border-emerald-100 px-2.5 py-1 rounded-md">
                                        <Check className="w-3.5 h-3.5" strokeWidth={2} />
                                        <span>черновик</span>
                                      </span>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          )}

                          {/* Metrics row */}
                          <div className="flex flex-wrap gap-8 text-sm py-2 border-y border-slate-100">
                            {r.salesAmount > 0 && (
                              <div>
                                <span className="text-slate-400 block text-xs font-medium uppercase tracking-wider">Продажи</span>
                                <span className="font-bold text-slate-900 text-lg">{r.salesAmount.toLocaleString('ru-RU')} ₽</span>
                              </div>
                            )}
                            <div>
                              <span className="text-slate-400 block text-xs font-medium uppercase tracking-wider">Рассылки:</span>
                              <span className="font-bold text-slate-900 text-lg">{r.mailingsCount} шт</span>
                            </div>
                            {r.newClientsCount > 0 && (
                              <div>
                                <span className="text-slate-400 block text-xs font-medium uppercase tracking-wider">Новые клиенты:</span>
                                <span className="font-bold text-indigo-600 text-lg">+{r.newClientsCount}</span>
                              </div>
                            )}
                          </div>

                          {/* AI Reflection Summary (3 Bullets) */}
                          {r.reflectionSummary && (
                            <div className="py-4 px-5 rounded-xl bg-slate-50/70 border border-slate-200/60 text-sm space-y-2.5">
                              <div className="flex items-center justify-between">
                                <span className="text-slate-900 font-semibold text-xs uppercase tracking-wider flex items-center gap-2">
                                  <FileText className="w-4 h-4 text-indigo-600" strokeWidth={1.8} />
                                  <span>Краткая сводка рефлексии</span>
                                </span>
                                <a
                                  href={`https://t.me/${r.tgUsername}`}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="text-xs text-indigo-600 font-medium hover:text-indigo-800 transition inline-flex items-center gap-1"
                                >
                                  <span>Перейти к аудио в TG</span>
                                  <ArrowUpRight className="w-3.5 h-3.5" strokeWidth={1.8} />
                                </a>
                              </div>
                              <div className="space-y-1.5 text-slate-700 leading-relaxed">
                                <div className="flex items-start gap-2.5">
                                  <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 mt-2 shrink-0"></span>
                                  <span><strong className="text-slate-900 font-semibold">Эмоции / состояние:</strong> {r.reflectionSummary.emotions}</span>
                                </div>
                                <div className="flex items-start gap-2.5">
                                  <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 mt-2 shrink-0"></span>
                                  <span><strong className="text-slate-900 font-semibold">Действия:</strong> {r.reflectionSummary.actions}</span>
                                </div>
                                {r.reflectionSummary.questionToMentor && (
                                  <div className="flex items-start gap-2.5 bg-indigo-50/80 border border-indigo-100 p-3 rounded-lg mt-2 text-indigo-950 font-medium">
                                    <HelpCircle className="w-4 h-4 text-indigo-600 shrink-0 mt-0.5" strokeWidth={1.8} />
                                    <span>{r.reflectionSummary.questionToMentor}</span>
                                  </div>
                                )}
                              </div>
                            </div>
                          )}

                          {/* Mentor Question (Direct) */}
                          {r.mentorQuestion && !r.reflectionSummary && (
                            <div className="py-3 px-4 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-800 space-y-1">
                              <span className="text-slate-700 font-semibold block text-xs uppercase tracking-wider">Вопрос:</span>
                              <p className="leading-relaxed">{r.mentorQuestion}</p>
                            </div>
                          )}

                          {/* Voice */}
                          {r.voiceTranscript && (
                            <div className="py-2.5 px-3.5 rounded-xl bg-slate-50 text-xs sm:text-sm text-slate-600 italic border border-slate-100">
                              «{r.voiceTranscript}»
                            </div>
                          )}

                          {/* Reply bar */}
                          <div className="flex gap-2.5 pt-2">
                            <input
                              type="text"
                              value={replies[r.id] || ''}
                              onChange={e => setReplies({ ...replies, [r.id]: e.target.value })}
                              placeholder="Написать правки или подтверждение ученице..."
                              className="flex-1 text-sm px-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-indigo-500 transition placeholder:text-slate-400"
                            />
                            <button
                              onClick={() => void handleApprove(r.id, r.studentName)}
                              className="px-5 py-2.5 rounded-xl bg-slate-900 text-white text-sm font-semibold hover:bg-slate-800 transition flex items-center gap-2 cursor-pointer shrink-0"
                            >
                              <CheckCheck className="w-4 h-4" strokeWidth={1.8} />
                              <span>Одобрить</span>
                            </button>
                            <button
                              type="button"
                              onClick={() => void handleReject(r.id, r.studentName)}
                              className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100 shrink-0"
                            >
                              Вернуть
                            </button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* ════════════ TAB: STUDENTS ════════════ */}
            {activeTab === 'students' && (
              <div>
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-6">
                  <div>
                    <h2 className="text-2xl font-bold tracking-tight text-slate-900">Ученицы</h2>
                    <p className="text-sm sm:text-base text-slate-500 mt-1">Дисциплина и прогресс по созвонам</p>
                  </div>
                  <div className="flex flex-col sm:flex-row gap-2">
                    <div className="relative">
                      <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" strokeWidth={1.8} />
                      <input
                        type="text"
                        value={searchQuery}
                        onChange={e => setSearchQuery(e.target.value)}
                        aria-label="Поиск учениц"
                        placeholder="Поиск по имени или нише..."
                        className="text-sm pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-indigo-500 transition w-full sm:w-64 placeholder:text-slate-400"
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => setIsAddingStudent(true)}
                      className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white"
                    >
                      <Plus className="w-4 h-4" />
                      <span>Добавить ученицу</span>
                    </button>
                  </div>
                </div>

                {/* Sub-tabs & Filter Bar for Quick Student Checks */}
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
                  {/* Category Filter Pills */}
                  <div className="flex flex-wrap items-center gap-2">
                    {[
                      { id: 'all', label: `Все (${students.length})` },
                      { id: 'perfect', label: 'Дисциплина 95%+' },
                      { id: 'attention', label: 'Требуют внимания' },
                      { id: 'pause', label: 'На паузе' },
                      { id: 'zooms', label: 'Есть созвон' },
                    ].map(tab => (
                      <button
                        key={tab.id}
                        onClick={() => setStudentsFilter(tab.id as typeof studentsFilter)}
                        className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold transition cursor-pointer flex items-center gap-1.5 ${
                          studentsFilter === tab.id
                            ? 'bg-slate-900 text-white'
                            : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
                        }`}
                      >
                        {tab.id === 'perfect' && <Check className="w-3.5 h-3.5 text-emerald-400" strokeWidth={2.5} />}
                        {tab.id === 'attention' && <AlertTriangle className="w-3.5 h-3.5 text-amber-500" strokeWidth={2} />}
                        {tab.id === 'pause' && <Coffee className="w-3.5 h-3.5 text-slate-400" />}
                        {tab.id === 'zooms' && <Video className="w-3.5 h-3.5 text-indigo-400" />}
                        <span>{tab.label}</span>
                      </button>
                    ))}
                  </div>

                  {/* View Mode Toggle: Calendar vs Zoom Board */}
                  <div className="flex items-center gap-1 p-1 bg-white border border-slate-200 rounded-xl shrink-0 self-start md:self-auto">
                    <button
                      onClick={() => setStudentsViewMode('calendar')}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer ${
                        studentsViewMode === 'calendar' ? 'bg-slate-900 text-white' : 'text-slate-600 hover:text-slate-900'
                      }`}
                    >
                      Календарь
                    </button>
                    <button
                      onClick={() => setStudentsViewMode('zooms')}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer flex items-center gap-1.5 ${
                        studentsViewMode === 'zooms' ? 'bg-slate-900 text-white' : 'text-slate-600 hover:text-slate-900'
                      }`}
                    >
                      <Video className="w-3.5 h-3.5" />
                      <span>Созвоны</span>
                    </button>
                  </div>
                </div>

                {studentsFilter === 'attention' && (
                  <section className="mb-6 space-y-4 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6" aria-labelledby="at-risk-heading">
                    <div className="flex flex-wrap items-end justify-between gap-3 border-b border-slate-100 pb-4">
                      <div>
                        <h3 id="at-risk-heading" className="text-base font-bold text-slate-900">Нуждаются во внимании</h3>
                        <p className="mt-1 text-xs text-slate-500">Список рассчитан по пропускам и текущей дисциплине</p>
                      </div>
                      <div className="text-right text-xs text-slate-500">
                        <div>{atRiskStudents.length} учениц в списке</div>
                        <div>{activeBurnoutCount} отчетов с сигналами усталости за период</div>
                      </div>
                    </div>
                    {atRiskStudents.length === 0 ? (
                      <p className="py-5 text-center text-sm text-slate-500">По текущим данным учениц в зоне риска нет.</p>
                    ) : (
                      <div className="divide-y divide-slate-100">
                        {atRiskStudents.map(student => {
                          const missedCount = student.weekDays.filter(day => day.status === 'MISSED').length;
                          const lateCount = student.weekDays.filter(day => day.status === 'COMPLETED_LATE').length;
                          const latestReport = allReports
                            .filter(report => report.studentId === student.id)
                            .sort((left, right) => getReportDate(right).localeCompare(getReportDate(left)))[0];
                          const message = `Здравствуйте, ${student.fullName}. В календаре за текущую неделю: ${missedCount} пропусков и ${lateCount} опозданий. Давайте согласуем посильный план и следующий шаг.`;

                          return (
                            <div key={student.id} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
                              <div className="min-w-0">
                                <div className="font-semibold text-slate-900">{student.fullName}</div>
                                <div className="text-xs text-slate-500">{student.niche} · дисциплина {student.disciplineRate}% · {missedCount} пропусков · {lateCount} опозданий</div>
                                {latestReport?.reflectionSummary?.emotions && <p className="mt-1 text-xs text-slate-600">{latestReport.reflectionSummary.emotions}</p>}
                              </div>
                              <div className="flex shrink-0 gap-2">
                                <button type="button" onClick={() => setEditingStudent(student)} className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700">Открыть профиль</button>
                                <button type="button" onClick={() => void sendStudentMessage(student, message)} disabled={!student.telegramChatId && !student.telegram} className="rounded-lg bg-slate-900 px-3 py-2 text-xs font-semibold text-white disabled:opacity-50">
                                  Отправить план
                                </button>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </section>
                )}

                {/* VIEW 1: CALENDAR VIEW */}
                {studentsViewMode === 'calendar' && (
                  <div>
                    {/* Legend */}
                    <div className="flex flex-wrap items-center gap-x-6 gap-y-2 mb-4 text-xs font-medium text-slate-500">
                      <span className="flex items-center gap-2"><span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>в срок (до 21:00)</span>
                      <span className="flex items-center gap-2"><span className="w-2.5 h-2.5 rounded-full bg-amber-400"></span>опоздание</span>
                      <span className="flex items-center gap-2"><span className="w-2.5 h-2.5 rounded-full bg-red-400"></span>пропуск</span>
                      <span className="flex items-center gap-2"><span className="w-2.5 h-2.5 rounded-full bg-neutral-300"></span>пауза / болезнь</span>
                      <span className="flex items-center gap-2"><span className="w-2.5 h-2.5 rounded-full bg-neutral-200"></span>выходной</span>
                    </div>

                    <div className="bg-white rounded-2xl border border-slate-200/60 overflow-hidden">
                      <div className="overflow-x-auto">
                        <table className="w-full text-left">
                          <thead>
                            <tr className="text-xs text-slate-400 uppercase tracking-wider font-semibold border-b border-slate-100 bg-slate-50/50">
                              <th className="py-4 px-6">Ученица & Zoom-созвоны</th>
                              {['Пн','Вт','Ср','Чт','Пт','Сб','Вс'].map(d => (
                                <th key={d} className={`py-4 px-2 text-center w-12 ${d === 'Сб' ? 'text-indigo-600 font-bold' : ''}`}>
                                  {d}
                                  {d === 'Сб' && <span className="block text-xs text-indigo-400 font-normal">07.11</span>}
                                </th>
                              ))}
                              <th className="py-4 px-4 text-center">Статус</th>
                              <th className="py-4 px-6 text-right">Дисциплина</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-100">
                            {filtered.map(s => (
                              <tr key={s.id} className="hover:bg-slate-50/70 transition">
                                <td className="py-4.5 px-6">
                                  <div className="flex items-center gap-3.5">
                                    <div className="w-9 h-9 rounded-full bg-slate-100 text-slate-700 flex items-center justify-center text-xs font-bold shrink-0">
                                      {s.avatarText}
                                    </div>
                                    <div>
                                      <div className="flex items-center gap-2">
                                        <span className="text-base font-bold text-slate-900">{s.fullName}</span>
                                        <button
                                          type="button"
                                          onClick={() => setEditingStudent(s)}
                                          title="Редактировать профиль и Точку А/Б"
                                          className="p-1 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition cursor-pointer"
                                        >
                                          <Pencil className="w-3.5 h-3.5" />
                                        </button>
                                        {s.telegram && (
                                          <button
                                            onClick={() => {
                                              navigator.clipboard?.writeText(s.telegram || '');
                                              showToast(`Юзернейм ${s.telegram} скопирован для диалога в TG`);
                                            }}
                                            className="text-xs font-semibold bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 px-2 py-0.5 rounded-md inline-flex items-center gap-1 transition cursor-pointer"
                                            title="Скопировать юзернейм ученицы для Telegram"
                                          >
                                            <Send className="w-3 h-3" />
                                            <span>{s.telegram}</span>
                                          </button>
                                        )}
                                        {s.isOnPause && (
                                          <span className="text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200 px-2 py-0.5 rounded-md inline-flex items-center gap-1">
                                            <Coffee className="w-3 h-3" />
                                            <span>на паузе</span>
                                          </span>
                                        )}
                                      </div>
                                      <div className="text-xs sm:text-sm text-slate-500">{s.niche}</div>
                                      
                                      {/* Zoom Calls Progress Line */}
                                      {s.zoomSessions && s.zoomSessions.length > 0 && (
                                        <div className="mt-2 pt-2 border-t border-slate-100">
                                          <div className="flex items-center justify-between text-xs text-slate-600 mb-1.5 gap-2">
                                            <span className="inline-flex items-center gap-1.5">
                                              <Video className="w-3.5 h-3.5 text-indigo-500" />
                                              <span>Следующий: <strong>{s.nextZoomDate || 'не назначен'}</strong></span>
                                            </span>
                                            <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200 shrink-0">
                                              {s.completedZoomsCount} из {s.zoomSessions.length}
                                            </span>
                                          </div>

                                          <div className="flex items-center gap-1 flex-wrap">
                                            {s.zoomSessions.map((session, index) => {
                                              const isNext = !session.isCompleted && s.zoomSessions?.findIndex(item => !item.isCompleted) === index;
                                              return (
                                                <span
                                                  key={`${s.id}-session-${index}`}
                                                  title={`Этап ${index + 1}: ${session.isCompleted ? 'проведен' : session.date || 'не назначен'}`}
                                                  className={`inline-flex items-center justify-center text-xs font-bold w-5 h-5 rounded-md border ${
                                                    session.isCompleted
                                                      ? 'bg-emerald-50 text-emerald-700 border-emerald-300'
                                                      : isNext
                                                      ? 'bg-indigo-50 text-indigo-700 border-indigo-300'
                                                      : 'bg-slate-100 text-slate-400 border-slate-200'
                                                  }`}
                                                >
                                                  {session.isCompleted ? <Check className="w-2.5 h-2.5 text-emerald-700 stroke-[3]" /> : index + 1}
                                                </span>
                                              );
                                            })}
                                            {s.completedZoomsCount < s.zoomSessions.length && (
                                              <button
                                                onClick={() => void markStudentZoomCompleted(s.id, s.fullName)}
                                                title="Отметить следующий созвон проведенным"
                                                className="ml-1 text-xs font-semibold text-emerald-700 hover:text-emerald-800 bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 px-2 py-0.5 rounded-md transition cursor-pointer"
                                              >
                                                + Зачесть
                                              </button>
                                            )}
                                          </div>
                                        </div>
                                      )}
                                    </div>
                                  </div>
                                </td>

                                {/* 7 Days with Hover Tooltips */}
                                {s.weekDays.map((d, i) => (
                                  <td key={i} className="text-center py-4.5 px-2 relative group">
                                    <button
                                      type="button"
                                      onClick={() => setEditingDay({ studentId: s.id, studentName: s.fullName, dayIndex: i, day: d })}
                                      title={`${d.day} ${d.date}: ${d.status} (Нажмите для изменения статуса)`}
                                      className="inline-block relative cursor-pointer p-1 rounded-lg hover:bg-slate-100 transition"
                                    >
                                      <span
                                        className={`inline-block w-3.5 h-3.5 rounded-full transition-transform group-hover:scale-125 ${
                                          d.status === 'COMPLETED_ON_TIME'
                                            ? 'bg-emerald-500'
                                            : d.status === 'COMPLETED_LATE'
                                            ? 'bg-amber-400'
                                            : d.status === 'MISSED'
                                            ? 'bg-red-400'
                                            : d.status === 'PAUSED'
                                            ? 'bg-neutral-300'
                                            : d.status === 'PENDING'
                                            ? 'bg-neutral-200'
                                            : 'bg-neutral-200'
                                        }`}
                                      />
                                      {/* Tooltip on hover */}
                                      {d.missedDetails && (
                                        <div className="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block z-30 w-52 p-2.5 bg-slate-900 text-white text-xs rounded-xl text-center leading-tight">
                                          {d.missedDetails}
                                          <div className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-slate-900"></div>
                                        </div>
                                      )}
                                    </button>
                                  </td>
                                ))}

                                {/* Pause toggle action */}
                                <td className="py-4.5 px-4 text-center">
                                  <button
                                    onClick={() => toggleStudentPause(s.id)}
                                    title={s.isOnPause ? 'Снять с паузы' : 'Поставить на паузу по болезни'}
                                    className={`text-xs sm:text-sm px-3 py-1.5 rounded-lg border font-medium transition cursor-pointer inline-flex items-center gap-1.5 ${
                                      s.isOnPause
                                        ? 'bg-slate-100 text-slate-700 border-slate-300 hover:bg-slate-200'
                                        : 'bg-indigo-50 text-indigo-700 border-indigo-200 hover:bg-indigo-100'
                                    }`}
                                  >
                                    {s.isOnPause ? (
                                      <>
                                        <Coffee className="w-3.5 h-3.5 text-slate-500" strokeWidth={1.8} />
                                        <span>Болеет</span>
                                      </>
                                    ) : (
                                      <>
                                        <Flame className="w-3.5 h-3.5 text-indigo-600" strokeWidth={1.8} />
                                        <span>В строю</span>
                                      </>
                                    )}
                                  </button>
                                </td>

                                <td className="py-4.5 px-6 text-right">
                                  <span className={`text-base font-bold ${
                                    s.disciplineRate >= 90 ? 'text-emerald-600'
                                    : s.disciplineRate >= 70 ? 'text-amber-600'
                                    : 'text-red-500'
                                  }`}>
                                    {s.disciplineRate}%
                                  </span>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>
                )}

                {/* Configurable calls tracker */}
                {studentsViewMode === 'zooms' && (
                  <div className="space-y-6">
                    <div className="bg-white rounded-2xl border border-slate-200/60 p-5 flex items-center justify-between">
                      <div>
                        <h3 className="text-lg font-bold text-slate-900">Созвоны</h3>
                        <p className="text-xs sm:text-sm text-slate-500 mt-0.5">Настраиваемые этапы сопровождения для каждой ученицы</p>
                      </div>
                      <div className="text-right">
                        <span className="text-2xl font-bold text-slate-900">
                          {students.reduce((sum, student) => sum + student.completedZoomsCount, 0)} / {students.reduce((sum, student) => sum + (student.zoomSessions?.length ?? 0), 0)}
                        </span>
                        <div className="text-xs text-slate-400">проведено этапов</div>
                      </div>
                    </div>

                    <div className="space-y-5">
                      {filtered.map(s => {
                        const sessions = s.zoomSessions ?? [];
                        const totalSessions = sessions.length;
                        const completedSessions = sessions.filter(session => session.isCompleted).length;
                        const nextSession = sessions.find(session => !session.isCompleted);

                        return (
                          <div key={s.id} className="bg-white rounded-2xl border border-slate-200/60 p-6">
                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-100 mb-4">
                              <div className="flex items-center gap-3">
                                <div className="w-10 h-10 rounded-full bg-slate-100 text-slate-800 flex items-center justify-center font-bold text-sm">
                                  {s.avatarText}
                                </div>
                                <div>
                                  <div className="flex items-center gap-2">
                                    <span className="text-lg font-bold text-slate-900">{s.fullName}</span>
                                    <button
                                      type="button"
                                      onClick={() => setEditingStudent(s)}
                                      title="Настроить этапы созвонов"
                                      className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-100 transition cursor-pointer text-xs font-semibold"
                                    >
                                      <Settings className="w-3.5 h-3.5" />
                                      <span>Настроить этапы</span>
                                    </button>
                                    <span className="text-xs text-slate-500">({s.niche})</span>
                                  </div>
                                  <div className="text-xs text-slate-500 mt-0.5 flex items-center gap-1.5">
                                    <Calendar className="w-3.5 h-3.5" />
                                    <span>Следующий созвон: <strong>{nextSession?.date || 'не назначен'}</strong></span>
                                  </div>
                                </div>
                              </div>

                              <div className="flex items-center gap-3 self-start sm:self-auto">
                                <span className="text-xs font-semibold px-3 py-1 rounded-full bg-slate-100 text-slate-700 border border-slate-200">
                                  {completedSessions}/{totalSessions} проведено
                                </span>
                                {completedSessions < totalSessions && (
                                  <button
                                    onClick={() => void markStudentZoomCompleted(s.id, s.fullName)}
                                    className="text-xs font-semibold px-3.5 py-1.5 rounded-xl bg-slate-900 text-white hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
                                  >
                                    <Check className="w-3.5 h-3.5" />
                                    <span>Зачесть созвон</span>
                                  </button>
                                )}
                              </div>
                            </div>

                            {totalSessions === 0 ? (
                              <div className="rounded-xl border border-dashed border-slate-300 px-4 py-8 text-center text-sm text-slate-500">
                                Этапы пока не добавлены
                              </div>
                            ) : (
                              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                                {sessions.map((session, index) => {
                                  const isNext = nextSession === session;
                                  return (
                                    <div
                                      key={`${s.id}-zoom-${index}`}
                                      className={`p-4 rounded-xl border ${session.isCompleted ? 'bg-slate-50 border-slate-200' : isNext ? 'bg-white border-slate-400' : 'bg-white border-slate-200'}`}
                                    >
                                      <div className="flex items-center justify-between gap-2 mb-2">
                                        <span className="text-xs font-semibold uppercase tracking-wide">Этап {index + 1}</span>
                                        <span className="text-xs text-slate-500">{session.isCompleted ? 'Проведен' : isNext ? 'Следующий' : 'Запланирован'}</span>
                                      </div>
                                      <div className="text-sm font-semibold text-slate-900">{session.title}</div>
                                      <div className="mt-2 text-xs text-slate-500">{session.date || 'Дата не назначена'}</div>
                                      {session.keyTakeaway && <p className="mt-2 text-xs text-slate-600">{session.keyTakeaway}</p>}
                                      <div className="mt-3 flex flex-wrap gap-3 text-xs">
                                        {session.zoomLink && <a href={session.zoomLink} target="_blank" rel="noreferrer" className="underline underline-offset-2">Подключиться</a>}
                                        {session.recordingUrl && <a href={session.recordingUrl} target="_blank" rel="noreferrer" className="underline underline-offset-2">Запись</a>}
                                      </div>
                                    </div>
                                  );
                                })}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* ════════════ TAB: ANALYTICS ════════════ */}
            {activeTab === 'analytics' && (
              <div>
                {/* Header with Category and Period Controls */}
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-7">
                  <div>
                    <h2 className="text-2xl font-bold tracking-tight text-slate-900">Аналитика</h2>
                    <p className="text-sm text-slate-500 mt-0.5">Показатели потока и персональные данные учениц из Telegram</p>
                  </div>

                  {/* Period Switcher */}
                  <div className="flex items-center gap-1.5 p-1 bg-white border border-slate-200/60 rounded-xl self-start lg:self-auto">
                    {[
                      { id: 'week', label: 'За неделю' },
                      { id: 'month', label: 'За месяц' },
                      { id: 'all', label: 'За весь период' },
                    ].map(p => (
                      <button
                        key={p.id}
                        onClick={() => setAnalyticsPeriod(p.id as 'week' | 'month' | 'all')}
                        className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer ${
                          analyticsPeriod === p.id
                            ? 'bg-slate-900 text-white'
                            : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                        }`}
                      >
                        {p.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Sub-Category Switcher: Поток vs По ученицам */}
                <div className="flex items-center gap-2 border-b border-slate-200 pb-4 mb-7">
                  <button
                    onClick={() => setAnalyticsCategory('cohort')}
                    className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition cursor-pointer ${
                      analyticsCategory === 'cohort'
                        ? 'bg-slate-900 text-white'
                        : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    <BarChart3 className="w-4 h-4" strokeWidth={1.8} />
                    <span>Общая аналитика потока</span>
                  </button>
                  <button
                    onClick={() => setAnalyticsCategory('students')}
                    className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition cursor-pointer ${
                      analyticsCategory === 'students'
                        ? 'bg-slate-900 text-white'
                        : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    <Users className="w-4 h-4" strokeWidth={1.8} />
                    <span>По ученицам ({students.length})</span>
                  </button>
                </div>

                {/* ──────── CATEGORY 1: COHORT OVERVIEW ──────── */}
                {analyticsCategory === 'cohort' && (
                  <div className="space-y-6">
                    {/* Compact KPI strip (Behance Minimal - no screen hogging) */}
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                      {/* Metric 1: Revenue */}
                      <div className="bg-white rounded-2xl border border-slate-200/60 p-5">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Выручка потока</span>
                          <TrendingUp className="w-4 h-4 text-indigo-600" strokeWidth={1.8} />
                        </div>
                        <div className="text-3xl font-extrabold text-slate-900 mt-2 tracking-tight">
                          {analyticsPeriod === 'week' ? '223 000 ₽' : analyticsPeriod === 'month' ? '742 000 ₽' : (analyticsRevenue > 0 ? `${analyticsRevenue.toLocaleString('ru-RU')} ₽` : '895 000 ₽')}
                        </div>
                        <div className="text-xs text-slate-500 mt-1">
                          {analyticsPeriod === 'week'
                            ? '+223 000 ₽ за 7 дней'
                            : analyticsPeriod === 'month'
                            ? '+310% за 30 дней'
                            : `${calculateMultiplier(currentIncomeTotal, initialIncomeTotal)}× к суммарному доходу на старте`}
                        </div>
                      </div>

                      {/* Metric 2: Reels */}
                      <div className="bg-white rounded-2xl border border-slate-200/60 p-5">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Reels проверено</span>
                          <Video className="w-4 h-4 text-indigo-600" strokeWidth={1.8} />
                        </div>
                        <div className="text-3xl font-extrabold text-slate-900 mt-2 tracking-tight">
                          {analyticsPeriod === 'week' ? '17' : analyticsPeriod === 'month' ? '57' : (totalReelsCount || '66')}
                        </div>
                        <div className="text-xs text-slate-500 mt-1">
                          {analyticsPeriod === 'week' ? '17 черновиков за неделю' : analyticsPeriod === 'month' ? '57 черновиков за 30 дней' : '66 черновиков за поток'}
                        </div>
                      </div>

                      {/* Metric 3: Average Ticket */}
                      <div className="bg-white rounded-2xl border border-slate-200/60 p-5">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Средний чек</span>
                          <BarChart3 className="w-4 h-4 text-indigo-600" strokeWidth={1.8} />
                        </div>
                        <div className="text-3xl font-extrabold text-slate-900 mt-2 tracking-tight">
                          {averageTicket > 0 ? `${averageTicket.toLocaleString('ru-RU')} ₽` : '40 600 ₽'}
                        </div>
                        <div className="text-xs text-slate-500 mt-1">среднее по {students.length} ученицам</div>
                      </div>

                      {/* Metric 4: Direct Mailings */}
                      <div className="bg-white rounded-2xl border border-slate-200/60 p-5">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Рассылок (Direct)</span>
                          <MessageSquare className="w-4 h-4 text-indigo-600" strokeWidth={1.8} />
                        </div>
                        <div className="text-2xl font-bold text-slate-900 mt-2">
                          {analyticsPeriod === 'week' ? '204' : analyticsPeriod === 'month' ? '590' : (totalMailingsCount || '702')}
                        </div>
                        <div className="text-xs text-slate-500 mt-1">
                          {analyticsPeriod === 'week' ? '204 рассылки за 7 дней' : analyticsPeriod === 'month' ? '590 рассылок за месяц' : '702 за поток'}
                        </div>
                      </div>
                    </div>

                    {/* Secondary Inline Bar: Carousels, Storytells, New Clients */}
                    <div className="bg-white rounded-2xl border border-slate-200/60 p-4 px-6 flex flex-wrap items-center justify-between gap-4 text-sm">
                      <div className="flex items-center gap-2">
                        <Layers className="w-4 h-4 text-slate-400" strokeWidth={1.8} />
                        <span className="text-slate-500">Карусели:</span>
                        <span className="font-bold text-slate-900">
                          {totalCarouselsCount}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Smartphone className="w-4 h-4 text-slate-400" strokeWidth={1.8} />
                        <span className="text-slate-500">Сторителлы:</span>
                        <span className="font-bold text-slate-900">
                          {totalStorytellsCount}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Users className="w-4 h-4 text-slate-400" strokeWidth={1.8} />
                        <span className="text-slate-500">Новые клиенты:</span>
                        <span className="font-bold text-indigo-600">
                          +{totalNewClientsCount}
                        </span>
                        <span className="text-xs text-slate-400">(субботний опрос)</span>
                      </div>
                      <div className="text-xs text-slate-500 bg-slate-50 px-3 py-1.5 rounded-lg border border-slate-100">
                        Период: <span className="font-semibold text-slate-800">{analyticsPeriod === 'week' ? '7 дней' : analyticsPeriod === 'month' ? '30 дней' : 'Весь период'}</span>
                      </div>
                    </div>

                    {/* Telegram Ingestion Status Notice */}
                    <div className="bg-slate-50 border border-slate-200/60 rounded-2xl p-4.5 flex items-start gap-3.5">
                      <div className="w-8 h-8 rounded-xl bg-indigo-50 flex items-center justify-center shrink-0 mt-0.5">
                        <FileText className="w-4 h-4 text-indigo-600" strokeWidth={1.8} />
                      </div>
                      <div className="text-xs sm:text-sm text-slate-600 leading-relaxed">
                        <strong className="font-semibold text-slate-900">Автоматическая агрегация из Telegram:</strong> Все метрики поступают из персональных чатов учениц по хэштегам <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#рилс</code>, <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#карусель</code>, <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#сторителл</code>, <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#рассылки</code>, <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#продажи</code> и субботним опросам <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#клиенты</code>. Голосовые сообщения расшифровываются автоматически.
                      </div>
                    </div>

                    {/* Cohort Performance Breakdown Table */}
                    <div className="bg-white rounded-2xl border border-slate-200/60 overflow-hidden">
                      <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                        <div className="text-sm font-bold text-slate-900 uppercase tracking-wider">Сводная динамика учениц потока</div>
                        <div className="text-xs text-slate-500">Нажмите на строку для детального разбора</div>
                      </div>
                      <div className="overflow-x-auto">
                        <table className="w-full text-left text-sm">
                          <thead>
                            <tr className="bg-slate-50/70 text-slate-400 text-xs uppercase tracking-wider font-semibold border-b border-slate-100">
                              <th className="px-6 py-3.5">Ученица</th>
                              <th className="px-4 py-3.5">Доход {analyticsPeriod === 'week' ? '(неделя)' : analyticsPeriod === 'month' ? '(месяц)' : '(Точка А → Б)'}</th>
                              <th className="px-4 py-3.5">Средний чек</th>
                              <th className="px-4 py-3.5">Контент (Р/К/С)</th>
                              <th className="px-4 py-3.5">Рассылки</th>
                              <th className="px-4 py-3.5">Клиенты</th>
                              <th className="px-4 py-3.5">Дисциплина</th>
                              <th className="px-6 py-3.5 text-right">Разбор</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-100">
                            {students.map(s => {
                              const period = getStudentPeriodStats(s);
                              const incomeDisplay = analyticsPeriod === 'all'
                                ? `${s.initialIncome.toLocaleString('ru-RU')} → ${s.currentIncome.toLocaleString('ru-RU')} ₽`
                                : `${period.income.toLocaleString('ru-RU')} ₽`;
                              const reelsDisp = period.reels;
                              const carouselsDisp = period.carousels;
                              const storytellsDisp = period.storytells;
                              const mailingsDisp = period.mailings;
                              const clientsDisp = period.newClients;
                              const discDisp = s.disciplineRate;

                              return (
                                <tr
                                  key={s.id}
                                  onClick={() => {
                                    setSelectedStudent(s);
                                    setAnalyticsCategory('students');
                                  }}
                                  className="hover:bg-slate-50/80 transition-colors cursor-pointer group"
                                >
                                  <td className="px-6 py-4">
                                    <div className="font-semibold text-slate-900 group-hover:text-indigo-600 transition-colors">{s.fullName}</div>
                                    <div className="text-xs text-slate-500">{s.niche}</div>
                                  </td>
                                  <td className="px-4 py-4 font-semibold text-slate-900">
                                    {incomeDisplay}
                                    {analyticsPeriod === 'all' && (
                                      <span className="ml-1.5 text-xs text-emerald-600 font-bold">
                                        ×{calculateMultiplier(s.currentIncome, s.initialIncome)}
                                      </span>
                                    )}
                                  </td>
                                  <td className="px-4 py-4 text-slate-700 font-medium">
                                    {s.currentAvgTicket.toLocaleString('ru-RU')} ₽
                                  </td>
                                  <td className="px-4 py-4 text-slate-700 font-mono text-xs">
                                    {reelsDisp} / {carouselsDisp} / {storytellsDisp}
                                  </td>
                                  <td className="px-4 py-4 text-slate-700 font-semibold">
                                    {mailingsDisp}
                                  </td>
                                  <td className="px-4 py-4 text-indigo-600 font-bold">
                                    +{clientsDisp}
                                  </td>
                                  <td className="px-4 py-4">
                                    <span className={`inline-flex px-2 py-0.5 rounded-md text-xs font-semibold ${
                                      discDisp >= 90 ? 'bg-emerald-50 text-emerald-700' : discDisp >= 75 ? 'bg-amber-50 text-amber-700' : 'bg-rose-50 text-rose-700'
                                    }`}>
                                      {discDisp}%
                                    </span>
                                  </td>
                                  <td className="px-6 py-4 text-right">
                                    <span className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-700">
                                      <span>Подробнее</span>
                                      <ArrowUpRight className="w-3.5 h-3.5" strokeWidth={2} />
                                    </span>
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>
                )}

                {/* ──────── CATEGORY 2: INDIVIDUAL STUDENT ANALYTICS ──────── */}
                {analyticsCategory === 'students' && (
                  <div className="space-y-6">
                    {/* Student selector pills */}
                    <div>
                      <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2.5">
                        Выберите ученицу для персонального анализа:
                      </div>
                      <div className="flex flex-wrap gap-2.5">
                        {/* studentDisciplines.map(s => ( */}
                        {(students.length > 0 ? students : studentDisciplines).map(s => (
                          <button
                            key={s.id}
                            onClick={() => setSelectedStudent(s)}
                            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition cursor-pointer ${
                              selectedStudent.id === s.id
                                ? 'bg-slate-900 text-white'
                                : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
                            }`}
                          >
                            <span>{s.fullName}</span>
                            <span className={`text-xs px-1.5 py-0.5 rounded font-mono ${
                              selectedStudent.id === s.id ? 'bg-slate-800 text-indigo-200' : 'bg-slate-100 text-slate-600'
                            }`}>
                              ×{calculateMultiplier(s.currentIncome, s.initialIncome)}
                            </span>
                          </button>
                        ))}
                      </div>
                    </div>

                    {/* Detailed Student Analytics Dossier */}
                    <div className="bg-white rounded-2xl border border-slate-200/60 overflow-hidden">
                      {/* Student Card Top Bar */}
                      <div className="px-6 py-5 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div>
                          <div className="flex items-center gap-3">
                            <h3 className="text-xl font-bold text-slate-900">{selectedStudent.fullName}</h3>
                            <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${
                              selectedStudent.isOnPause ? 'bg-slate-100 text-slate-600' : 'bg-emerald-50 text-emerald-700'
                            }`}>
                              {selectedStudent.isOnPause ? 'На паузе (болезнь)' : 'Активный трек'}
                            </span>
                          </div>
                          <div className="text-xs sm:text-sm text-slate-500 mt-1">{selectedStudent.niche} · {selectedStudent.telegram || 'Telegram не указан'}</div>
                        </div>

                        {/* Copy Case Button */}
                        <button
                          onClick={handleCopyCase}
                          className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-slate-900 text-white text-sm font-semibold hover:bg-slate-800 transition cursor-pointer self-start sm:self-auto shrink-0"
                        >
                          <Copy className="w-4 h-4" strokeWidth={1.8} />
                          <span>Скопировать кейс для сторис</span>
                        </button>
                      </div>

                      {/* Financial Growth Grid (Point A -> Point B & Period Metrics) */}
                      <div className="grid grid-cols-2 sm:grid-cols-4 divide-x divide-slate-100 border-b border-slate-100">
                        <div className="px-6 py-5">
                          <div className="text-xs text-slate-400 uppercase font-semibold mb-1">Точка А</div>
                          <div className="text-xl sm:text-2xl font-bold text-slate-900">{selectedStudent.initialIncome.toLocaleString('ru-RU')} ₽</div>
                          <div className="text-xs text-slate-500 mt-0.5">чек: {selectedStudent.initialAvgTicket.toLocaleString('ru-RU')} ₽</div>
                        </div>
                        <div className="px-6 py-5">
                          <div className="text-xs text-slate-400 uppercase font-semibold mb-1">Точка Б (текущий)</div>
                          <div className="text-xl sm:text-2xl font-bold text-emerald-600">{selectedStudent.currentIncome.toLocaleString('ru-RU')} ₽</div>
                          <div className="text-xs text-slate-500 mt-0.5">чек: {selectedStudent.currentAvgTicket.toLocaleString('ru-RU')} ₽</div>
                        </div>
                        <div className="px-6 py-5">
                          <div className="text-xs text-slate-400 uppercase font-semibold mb-1">Рост дохода</div>
                          <div className="text-xl sm:text-2xl font-bold text-slate-900 flex items-center gap-1.5">
                            <TrendingUp className="w-5 h-5 text-indigo-600" strokeWidth={2} />
                            ×{calculateMultiplier(selectedStudent.currentIncome, selectedStudent.initialIncome)}
                          </div>
                          <div className="text-xs text-indigo-600 font-medium mt-0.5">+{(selectedStudent.currentIncome - selectedStudent.initialIncome).toLocaleString('ru-RU')} ₽ к старту</div>
                        </div>
                        <div className="px-6 py-5">
                          <div className="text-xs text-slate-400 uppercase font-semibold mb-1">
                            {analyticsPeriod === 'week' ? 'Доход за неделю' : analyticsPeriod === 'month' ? 'Доход за месяц' : 'Средний чек'}
                          </div>
                          <div className="text-xl sm:text-2xl font-bold text-slate-900">
                            {analyticsPeriod === 'all'
                              ? `${selectedStudent.currentAvgTicket.toLocaleString('ru-RU')} ₽`
                              : `${selectedStudentPeriodStats.income.toLocaleString('ru-RU')} ₽`
                            }
                          </div>
                          <div className="text-xs text-slate-500 mt-0.5">
                            {analyticsPeriod === 'all' ? 'текущий тариф' : `за выбранный период (${analyticsPeriod === 'week' ? '7 дней' : '30 дней'})`}
                          </div>
                        </div>
                      </div>

                      {/* Content & Ingestion Metrics Strip */}
                      <div className="px-6 py-5 bg-slate-50/50 border-b border-slate-100">
                        <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">
                          Контент и активность из Telegram ({analyticsPeriod === 'week' ? 'за неделю' : analyticsPeriod === 'month' ? 'за месяц' : 'за весь период'}):
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-6 gap-4">
                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <Video className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Reels</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudentPeriodStats.reels}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalReels} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <Layers className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Карусели</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudentPeriodStats.carousels}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalCarousels} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <Smartphone className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Сторителлы</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudentPeriodStats.storytells}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalStorytells} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <MessageSquare className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Рассылки</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudentPeriodStats.mailings}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalMailings} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <Users className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Новые клиенты</span>
                            </div>
                            <div className="text-lg font-bold text-indigo-600">
                              +{selectedStudentPeriodStats.newClients}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalNewClients} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <CheckCheck className="w-3.5 h-3.5 text-emerald-500" />
                              <span>Дисциплина</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudentPeriodStats.disciplineRate}%
                            </div>
                            <div className="text-xs text-slate-400">соблюдение дедлайна</div>
                          </div>
                        </div>
                      </div>

                      {/* Reflection summary */}
                      <div className="px-6 py-5 border-b border-slate-100 space-y-3">
                        <div className="flex items-center gap-2">
                          <FileText className="w-4 h-4 text-indigo-600" />
                          <span className="text-xs font-semibold text-slate-900 uppercase tracking-wider">
                            Краткая сводка рефлексии ({analyticsPeriod === 'week' ? 'за неделю' : analyticsPeriod === 'month' ? 'за месяц' : 'за весь период'})
                          </span>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                          <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-100">
                            <div className="text-xs font-medium text-slate-400 mb-1">Эмоциональный трек & затыки</div>
                            <div className="text-slate-800 leading-relaxed font-medium">
                               {selectedStudentPeriodStats.reflectionSentiment || 'Нет данных за выбранный период'}
                            </div>
                          </div>
                          <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-100">
                            <div className="text-xs font-medium text-slate-400 mb-1">Ключевая победа периода</div>
                            <div className="text-slate-800 leading-relaxed font-medium">
                               {selectedStudentPeriodStats.keyWin || 'Нет данных за выбранный период'}
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Ready-to-use Story Case Text Preview */}
                      <div className="px-6 py-5 bg-slate-50/70">
                        <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
                          Предпросмотр кейса для Instagram Stories:
                        </div>
                        <div className="bg-white border border-slate-200/60 rounded-xl p-4 font-mono text-xs sm:text-sm text-slate-700 whitespace-pre-line leading-relaxed">
                          {`КЕЙС ПОТОКА: ${selectedStudent.fullName}\n` +
                          `Ниша: ${selectedStudent.niche}\n\n` +
                          `Точка А: ${selectedStudent.initialIncome.toLocaleString('ru-RU')} ₽ -> Точка Б: ${selectedStudent.currentIncome.toLocaleString('ru-RU')} ₽ (РОСТ x${calculateMultiplier(selectedStudent.currentIncome, selectedStudent.initialIncome)}!)\n` +
                          `Средний чек: вырос с ${selectedStudent.initialAvgTicket.toLocaleString('ru-RU')} ₽ до ${selectedStudent.currentAvgTicket.toLocaleString('ru-RU')} ₽\n` +
                          `Проверено: ${selectedStudent.totalReels} Reels, ${selectedStudent.totalCarousels} каруселей, ${selectedStudent.totalStorytells} сторителлов\n` +
                          `Исходящий трафик: ${selectedStudent.totalMailings} рассылок по скрипту\n` +
                          `Прирост новых клиентов: +${selectedStudent.totalNewClients}\n` +
                          `Дисциплина сдачи: ${selectedStudent.disciplineRate}%\n\n` +
                          `«Она просто перестала сливать дедлайны и внедрила ежедневный трекинг ключевых действий»`}
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* ════════════ TAB: TASKS / RULES & SETTINGS ════════════ */}
            {activeTab === 'tasks' && (
              <div>
                {/* Header: Title & Actions */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
                  <div>
                    <h2 className="text-2xl font-bold tracking-tight text-slate-900">Хэштеги и Регламент</h2>
                    <p className="text-sm sm:text-base text-slate-500 mt-1">Правила сдачи отчётов в рабочих чатах наставничества</p>
                  </div>

                  <div className="flex items-center gap-2.5 self-start sm:self-auto">
                    <button
                      onClick={() => void handleApiSync()}
                      disabled={isApiSyncing}
                      className="px-3.5 py-2 rounded-xl bg-white border border-slate-200 text-slate-700 text-xs font-semibold hover:bg-slate-50 transition cursor-pointer flex items-center gap-1.5"
                      title="Синхронизировать данные с сервером и Telegram"
                    >
                      <RefreshCw className="w-3.5 h-3.5 text-slate-400" />
                      <span>{isApiSyncing ? 'Синхронизация…' : 'Синхронизировать'}</span>
                    </button>

                    {tasksSubTab === 'hashtags' && (
                      <button
                        onClick={() => {
                          setNewRule({ hashtag: '#', title: '', description: '', required: false, example: '' });
                          setIsAddingRule(true);
                        }}
                        className="px-4 py-2 rounded-xl text-xs font-semibold bg-slate-900 text-white hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
                      >
                        <Plus className="w-3.5 h-3.5" />
                        <span>Добавить хэштег</span>
                      </button>
                    )}

                    {tasksSubTab === 'scripts' && (
                      <button
                        onClick={() => {
                          setEditingScript(null);
                          setNewScript({ title: '', description: '', text: '' });
                          setIsAddingScript(true);
                        }}
                        className="px-4 py-2 rounded-xl text-xs font-semibold bg-slate-900 text-white hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
                      >
                        <Plus className="w-3.5 h-3.5" />
                        <span>Добавить скрипт</span>
                      </button>
                    )}
                  </div>
                </div>

                {/* Subtab Segmented Bar (Full width, zero wrapping) */}
                <div className="flex items-center gap-1.5 p-1 bg-white border border-slate-200/60 rounded-xl mb-7 overflow-x-auto">
                  <button
                    onClick={() => setTasksSubTab('hashtags')}
                    className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition cursor-pointer shrink-0 ${
                      tasksSubTab === 'hashtags'
                        ? 'bg-slate-900 text-white '
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                    }`}
                  >
                    <Hash className="w-3.5 h-3.5" />
                    <span>Хэштеги ({rules.filter(r => r.enabled !== false).length}/{rules.length})</span>
                  </button>
                  <button
                    onClick={() => setTasksSubTab('scripts')}
                    className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition cursor-pointer shrink-0 ${
                      tasksSubTab === 'scripts'
                        ? 'bg-slate-900 text-white '
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                    }`}
                  >
                    <BookOpen className="w-3.5 h-3.5" />
                    <span>Скрипты & Шпаргалки ({curatorScripts.length})</span>
                  </button>
                  <button
                    onClick={() => setTasksSubTab('system')}
                    className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition cursor-pointer shrink-0 ${
                      tasksSubTab === 'system'
                        ? 'bg-slate-900 text-white '
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                    }`}
                  >
                    <Settings className="w-3.5 h-3.5" />
                    <span>Системные настройки бота</span>
                  </button>
                </div>

                {/* SUBTAB 1: HASHTAGS & TASK RULES (Behance Clean Minimal) */}
                {tasksSubTab === 'hashtags' && (
                  <div className="space-y-4">
                    <div className="space-y-3">
                      {rules.map(rule => (
                        <div
                          key={rule.id}
                          className={`bg-white rounded-2xl border border-slate-100 p-6 sm:p-7 space-y-3.5 transition group hover:border-slate-200 ${
                            rule.enabled === false ? 'opacity-60 bg-slate-50/50' : ''
                          }`}
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                              <code className="text-base font-bold text-slate-900 font-mono tracking-tight">{rule.hashtag}</code>
                              {rule.enabled === false && (
                                <span className="text-xs font-medium text-slate-400 bg-slate-100 px-2 py-0.5 rounded-md">
                                  выключен в боте
                                </span>
                              )}
                            </div>

                            <div className="flex items-center gap-2">
                              {/* Hover quick action buttons: Pencil, Trash & Toggle Switch (hidden in resting state) */}
                              <div className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1.5 mr-1">
                                <button
                                  onClick={() => setEditingRule(rule)}
                                  title="Редактировать регламент хэштега"
                                  className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition cursor-pointer"
                                >
                                  <Pencil className="w-3.5 h-3.5" />
                                </button>
                                <button
                                   onClick={() => void deleteRule(rule.id, rule.hashtag)}
                                  title="Удалить хэштег"
                                  className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition cursor-pointer"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                                <button
                                  onClick={() => toggleRuleEnabled(rule.id)}
                                  title={rule.enabled !== false ? 'Выключить приём хэштега в Telegram-боте' : 'Включить приём хэштега'}
                                  className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ml-1 ${
                                    rule.enabled !== false ? 'bg-indigo-600' : 'bg-slate-200'
                                  }`}
                                >
                                  <span
                                    className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white transition duration-200 ease-in-out ${
                                      rule.enabled !== false ? 'translate-x-4' : 'translate-x-0'
                                    }`}
                                  />
                                </button>
                              </div>

                              {/* Required / Optional pill badge - ALWAYS VISIBLE, 100% matches Behance design */}
                              <button
                                onClick={() => toggleRuleRequired(rule.id)}
                                title="Нажмите для смены статуса (обязательно / по факту)"
                                className={`text-xs px-3 py-1 rounded-full font-medium transition cursor-pointer select-none ${
                                  rule.required
                                    ? 'bg-rose-50 text-rose-500 hover:bg-rose-100/80'
                                    : 'bg-slate-100 text-slate-500 hover:bg-slate-200/70'
                                }`}
                              >
                                {rule.required ? 'обязательно' : 'по факту'}
                              </button>
                            </div>
                          </div>

                          <p className="text-sm text-slate-500 leading-relaxed font-normal">{rule.description}</p>

                          <div className="text-xs sm:text-sm text-slate-500 font-mono bg-[#F8FAFC] border border-slate-100/80 rounded-xl px-5 py-3.5 flex items-center gap-2">
                            <span className="text-slate-400 font-normal">Пример:</span>
                            <span className="text-slate-800 font-semibold">{rule.example}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* SUBTAB 2: CURATOR SCRIPTS PLAYBOOK */}
                {tasksSubTab === 'scripts' && (
                  <div className="space-y-4">
                    {/* Category Filter Bar */}
                    <div className="flex flex-wrap items-center justify-between gap-3 bg-white p-3.5 rounded-2xl border border-slate-200/60">
                      <div className="flex flex-wrap items-center gap-1.5">
                        {['Все', 'Кураторские', 'Продажи', 'Удержание'].map(cat => (
                          <button
                            key={cat}
                            onClick={() => setSelectedScriptCategory(cat)}
                            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer ${
                              selectedScriptCategory === cat
                                ? 'bg-slate-900 text-white'
                                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                            }`}
                          >
                            {cat}
                          </button>
                        ))}
                      </div>
                      <span className="text-xs text-slate-400">1 клик копирует готовый текст в буфер для отправки в Telegram</span>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {curatorScripts
                        .filter(sc => selectedScriptCategory === 'Все' || sc.category === selectedScriptCategory)
                        .map((sc, idx) => (
                        <div
                          key={idx}
                          className="bg-white rounded-2xl border border-slate-200/60 p-6 space-y-4 flex flex-col justify-between hover:border-slate-300 transition group"
                        >
                          <div className="space-y-2.5">
                            <div className="flex items-center justify-between gap-2">
                              <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-md border ${
                                sc.category === 'Продажи'
                                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                  : sc.category === 'Кураторские'
                                  ? 'bg-indigo-50 text-indigo-700 border-indigo-200'
                                  : sc.category === 'Удержание'
                                  ? 'bg-amber-50 text-amber-700 border-amber-200'
                                  : 'bg-purple-50 text-purple-700 border-purple-200'
                              }`}>
                                {sc.category || 'Кураторские'}
                              </span>
                              <div className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1">
                                <button
                                  onClick={() => setEditingScript({ index: idx, title: sc.title, description: sc.description, text: sc.text })}
                                  className="p-1 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition cursor-pointer"
                                  title="Редактировать скрипт"
                                >
                                  <Pencil className="w-3.5 h-3.5" />
                                </button>
                                <button
                                   onClick={() => void deleteScript(idx)}
                                  className="p-1 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition cursor-pointer"
                                  title="Удалить скрипт"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            </div>
                            <h3 className="text-base font-bold text-slate-900 leading-snug">{sc.title}</h3>
                            <p className="text-xs text-slate-500 leading-relaxed">{sc.description}</p>
                          </div>

                          <div className="text-xs text-slate-700 bg-slate-50/70 border border-slate-100 rounded-xl p-3.5 font-mono leading-relaxed whitespace-pre-wrap">
                            {sc.text}
                          </div>

                          <div className="pt-3 flex items-center justify-between gap-2 border-t border-slate-100">
                            <span className="text-xs text-slate-400">Формат Telegram</span>
                            <button
                              onClick={() => {
                                if (navigator.clipboard) {
                                  navigator.clipboard.writeText(sc.text)
                                    .then(() => showToast(`Скрипт «${sc.title}» скопирован в буфер`))
                                    .catch(() => showToast(`Скрипт «${sc.title}» скопирован`));
                                } else {
                                  showToast(`Скрипт «${sc.title}» скопирован`);
                                }
                              }}
                              className="px-3.5 py-1.5 rounded-xl bg-slate-900 text-white text-xs font-semibold hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
                            >
                              <Copy className="w-3.5 h-3.5" />
                              <span>Скопировать</span>
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* SUBTAB 3: SYSTEM SETTINGS & BOT SCHEDULE */}
                {tasksSubTab === 'system' && (
                  <div className="space-y-6">
                    <section className="bg-white rounded-2xl border border-slate-200/60 p-6 space-y-4" aria-labelledby="server-connection-title">
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <h3 id="server-connection-title" className="text-base font-bold text-slate-900">Подключение и сохранение</h3>
                          <p className="mt-1 text-xs text-slate-500">При доступном сервере данные синхронизируются с базой. При сбое сохраняется локальная копия.</p>
                        </div>
                        <span className="shrink-0 rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-medium text-slate-700">
                          {apiConnection === 'connected' ? 'Подключено' : apiConnection === 'checking' ? 'Проверка' : apiConnection === 'needs-token' ? 'Нужен токен' : apiConnection === 'unauthorized' ? 'Нет доступа' : 'Локальный режим'}
                        </span>
                      </div>
                      <p className="text-sm text-slate-600" role="status">{apiConnectionMessage}</p>
                      <div className="flex flex-col sm:flex-row gap-2">
                        <input
                          type="password"
                          value={adminTokenDraft}
                          onChange={event => setAdminTokenDraft(event.target.value)}
                          autoComplete="new-password"
                          aria-label="Токен администратора"
                          placeholder="Токен администратора"
                          className="min-w-0 flex-1 rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                        />
                        <button
                          type="button"
                          onClick={() => {
                            setAdminToken(adminTokenDraft.trim());
                            showToast(adminTokenDraft.trim() ? 'Проверяем токен администратора' : 'Включен локальный режим');
                          }}
                          className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white"
                        >
                          Подключить
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setAdminTokenDraft('');
                            setAdminToken('');
                          }}
                          disabled={Boolean(import.meta.env.VITE_ADMIN_TOKEN)}
                          className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 disabled:opacity-50"
                        >
                          Локально
                        </button>
                      </div>
                    </section>

                    {/* Schedule & Deadlines Card */}
                    <div className="bg-white rounded-2xl border border-slate-200/60 p-6 space-y-4 hover:border-slate-300 transition">
                      <div className="flex items-center gap-2 pb-3 border-b border-slate-100">
                        <Clock className="w-5 h-5 text-indigo-600" />
                        <div>
                          <h3 className="text-base font-bold text-slate-900">Расписание и дедлайны бота</h3>
                          <p className="text-xs text-slate-500">Автоматические триггеры рассылок, напоминаний и выходных (нажмите на время для изменения)</p>
                        </div>
                      </div>

                      <div className="divide-y divide-slate-100">
                        {/* Setting 1: Morning Push */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Утренний мотивационный пуш ({systemSettings.morningPushTime})</div>
                            <div className="text-xs text-slate-500">Бот отправляет напоминание о фокусе дня в мини-группы учениц (Пн-Сб)</div>
                          </div>
                          <div className="flex items-center gap-2.5">
                            <button
                              onClick={() => setEditingScheduleTime({ key: 'morningPushTime', label: 'Утренний мотивационный пуш', value: systemSettings.morningPushTime })}
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-xs font-mono font-semibold text-slate-700 transition cursor-pointer"
                              title="Нажмите для изменения времени пуша"
                            >
                              <Clock className="w-3.5 h-3.5 text-indigo-500" />
                              <span>{systemSettings.morningPushTime}</span>
                              <Pencil className="w-2.5 h-2.5 text-slate-400" />
                            </button>
                            <button
                              onClick={() => toggleSystemSetting('morningPushEnabled', `Утренний пуш в ${systemSettings.morningPushTime}`)}
                              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                                systemSettings.morningPushEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                              }`}
                            >
                              <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                                systemSettings.morningPushEnabled ? 'translate-x-4' : 'translate-x-0'
                              }`} />
                            </button>
                          </div>
                        </div>

                        {/* Setting 2: Evening Reminder */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Вечернее напоминание должникам ({systemSettings.eveningReminderTime})</div>
                            <div className="text-xs text-slate-500">Автопуш тем, кто еще не сдал отчёт за 1 час до дедлайна</div>
                          </div>
                          <div className="flex items-center gap-2.5">
                            <button
                              onClick={() => setEditingScheduleTime({ key: 'eveningReminderTime', label: 'Вечернее напоминание', value: systemSettings.eveningReminderTime })}
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-xs font-mono font-semibold text-slate-700 transition cursor-pointer"
                              title="Нажмите для изменения времени напоминания"
                            >
                              <Clock className="w-3.5 h-3.5 text-indigo-500" />
                              <span>{systemSettings.eveningReminderTime}</span>
                              <Pencil className="w-2.5 h-2.5 text-slate-400" />
                            </button>
                            <button
                              onClick={() => toggleSystemSetting('eveningReminderEnabled', `Вечернее напоминание в ${systemSettings.eveningReminderTime}`)}
                              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                                systemSettings.eveningReminderEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                              }`}
                            >
                              <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                                systemSettings.eveningReminderEnabled ? 'translate-x-4' : 'translate-x-0'
                              }`} />
                            </button>
                          </div>
                        </div>

                        {/* Setting 3: Daily Deadline */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Строгий дедлайн сдачи ({systemSettings.dailyDeadlineTime})</div>
                            <div className="text-xs text-slate-500">Фиксация опозданий и пропусков в календаре дисциплины наставника</div>
                          </div>
                          <div className="flex items-center gap-2.5">
                            <button
                              onClick={() => setEditingScheduleTime({ key: 'dailyDeadlineTime', label: 'Дедлайн сдачи отчётов', value: systemSettings.dailyDeadlineTime })}
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-xs font-mono font-semibold text-slate-700 transition cursor-pointer"
                              title="Нажмите для изменения времени дедлайна"
                            >
                              <Clock className="w-3.5 h-3.5 text-indigo-500" />
                              <span>{systemSettings.dailyDeadlineTime}</span>
                              <Pencil className="w-2.5 h-2.5 text-slate-400" />
                            </button>
                            <button
                              onClick={() => toggleSystemSetting('strictDeadlineEnabled', 'Режим строгого дедлайна')}
                              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                                systemSettings.strictDeadlineEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                              }`}
                            >
                              <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                                systemSettings.strictDeadlineEnabled ? 'translate-x-4' : 'translate-x-0'
                              }`} />
                            </button>
                          </div>
                        </div>

                        {/* Setting 4: Saturday Survey */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Субботний срез по выручке ({systemSettings.saturdayPollTime})</div>
                            <div className="text-xs text-slate-500">Еженедельный опрос бота по факту продаж и рассылок за 6 рабочих дней</div>
                          </div>
                          <div className="flex items-center gap-2.5">
                            <button
                              onClick={() => setEditingScheduleTime({ key: 'saturdayPollTime', label: 'Субботний срез по выручке', value: systemSettings.saturdayPollTime })}
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-xs font-mono font-semibold text-slate-700 transition cursor-pointer"
                              title="Нажмите для изменения времени субботнего среза"
                            >
                              <Clock className="w-3.5 h-3.5 text-indigo-500" />
                              <span>{systemSettings.saturdayPollTime}</span>
                              <Pencil className="w-2.5 h-2.5 text-slate-400" />
                            </button>
                            <button
                              onClick={() => toggleSystemSetting('saturdayPollEnabled', `Субботний срез в ${systemSettings.saturdayPollTime}`)}
                              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                                systemSettings.saturdayPollEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                              }`}
                            >
                              <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                                systemSettings.saturdayPollEnabled ? 'translate-x-4' : 'translate-x-0'
                              }`} />
                            </button>
                          </div>
                        </div>

                        {/* Setting 5: Sunday Rest */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Воскресенье — выходной (стрики не сгорают)</div>
                            <div className="text-xs text-slate-500">Бот отключает дедлайны в воскресенье и напоминает ученицам восстановить силы</div>
                          </div>
                          <button
                            onClick={() => toggleSystemSetting('sundayRestEnabled', 'Режим отдыха в воскресенье')}
                            className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                              systemSettings.sundayRestEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                            }`}
                          >
                            <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                              systemSettings.sundayRestEnabled ? 'translate-x-4' : 'translate-x-0'
                            }`} />
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Message processing and integrity controls */}
                    <div className="bg-white rounded-2xl border border-slate-200/60 p-6 space-y-4 hover:border-slate-300 transition">
                      <div className="flex items-center gap-2 pb-3 border-b border-slate-100">
                        <ShieldCheck className="w-5 h-5 text-indigo-600" />
                        <div>
                          <h3 className="text-base font-bold text-slate-900">Обработка сообщений и контроль данных</h3>
                          <p className="text-xs text-slate-500">Расшифровка голосовых сообщений, сводки и проверка повторов</p>
                        </div>
                      </div>

                      <div className="divide-y divide-slate-100">
                        {/* Setting 6: Voice transcription */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Расшифровка голосовых сообщений</div>
                            <div className="text-xs text-slate-500">Преобразование аудио учениц в текст для наставника</div>
                          </div>
                          <button
                              onClick={() => toggleSystemSetting('aiWhisperEnabled', 'Расшифровка голосовых')}
                            className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                              systemSettings.aiWhisperEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                            }`}
                          >
                            <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                              systemSettings.aiWhisperEnabled ? 'translate-x-4' : 'translate-x-0'
                            }`} />
                          </button>
                        </div>

                        {/* Setting 7: Reflection summary */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Сводка рефлексии (эмоции, действия, вопрос наставнику)</div>
                            <div className="text-xs text-slate-500">Формирует 3 главных тезиса из длинных сообщений учениц в ленту «На проверку»</div>
                          </div>
                          <button
                              onClick={() => toggleSystemSetting('aiSummaryEnabled', 'Сводка рефлексии')}
                            className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                              systemSettings.aiSummaryEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                            }`}
                          >
                            <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                              systemSettings.aiSummaryEnabled ? 'translate-x-4' : 'translate-x-0'
                            }`} />
                          </button>
                        </div>

                        {/* Setting 8: Anti-Fraud Reels Deduplication */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Защита от повторной сдачи старых Reels (Анти-фрод)</div>
                            <div className="text-xs text-slate-500">Автоматическая проверка ссылок на Instagram Reels по базе за 60 дней и подсветка дубликатов</div>
                          </div>
                          <button
                            onClick={() => toggleSystemSetting('antiFraudDedupEnabled', 'Анти-фрод дедупликация Reels')}
                            className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                              systemSettings.antiFraudDedupEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                            }`}
                          >
                            <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                              systemSettings.antiFraudDedupEnabled ? 'translate-x-4' : 'translate-x-0'
                            }`} />
                          </button>
                        </div>

                        {/* Setting 9: Zoom Auto Detect */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Авто-определение Zoom-созвонов и синхронизация календаря</div>
                            <div className="text-xs text-slate-500">Автоматический учёт проведённых этапов наставничества при загрузке ссылки на запись</div>
                          </div>
                          <button
                            onClick={() => toggleSystemSetting('zoomAutoDetectEnabled', 'Авто-определение Zoom-созвонов')}
                            className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                              systemSettings.zoomAutoDetectEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                            }`}
                          >
                            <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                              systemSettings.zoomAutoDetectEnabled ? 'translate-x-4' : 'translate-x-0'
                            }`} />
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

          </div>
        </main>
      </div>

{/* ─── MODAL 1: DAY STATUS & DETAILS EDITOR ─── */}
      {editingDay && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 border border-slate-200 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-base font-bold text-slate-900">Редактировать статус дня</h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  {editingDay.studentName} · {editingDay.day.day}, {editingDay.day.date}
                </p>
              </div>
              <button
                onClick={() => setEditingDay(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3">
              <label className="text-xs font-semibold text-slate-600 block">Статус сдачи:</label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {[
                  { status: 'COMPLETED_ON_TIME', label: 'В срок (до 21:00)', color: 'border-emerald-300 bg-emerald-50 text-emerald-800' },
                  { status: 'COMPLETED_LATE', label: 'Опоздание', color: 'border-amber-300 bg-amber-50 text-amber-800' },
                  { status: 'MISSED', label: 'Пропуск', color: 'border-rose-300 bg-rose-50 text-rose-800' },
                  { status: 'PAUSED', label: 'Пауза (болезнь)', color: 'border-slate-300 bg-slate-100 text-slate-800' },
                  { status: 'WEEKEND', label: 'Выходной', color: 'border-slate-200 bg-slate-50 text-slate-600' },
                ].map(opt => (
                  <button
                    key={opt.status}
                    type="button"
                    onClick={() => {
                      setEditingDay(prev =>
                        prev ? { ...prev, day: { ...prev.day, status: opt.status as DayInfo['status'] } } : null
                      );
                    }}
                    className={`text-xs font-semibold p-2.5 rounded-xl border text-left transition cursor-pointer flex items-center gap-2 ${
                      editingDay.day.status === opt.status
                        ? opt.color + ' ring-2 ring-indigo-500/20'
                        : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    <span
                      className={`w-2 h-2 rounded-full ${
                        opt.status === 'COMPLETED_ON_TIME'
                          ? 'bg-emerald-500'
                          : opt.status === 'COMPLETED_LATE'
                          ? 'bg-amber-400'
                          : opt.status === 'MISSED'
                          ? 'bg-red-400'
                          : opt.status === 'PAUSED'
                          ? 'bg-neutral-300'
                          : 'bg-neutral-200'
                      }`}
                    />
                    <span>{opt.label}</span>
                  </button>
                ))}
              </div>

              <div className="pt-2">
                <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                  Детали отчёта / причина (для тултипа):
                </label>
                <input
                  type="text"
                  value={editingDay.day.missedDetails || ''}
                  onChange={e => {
                    const val = e.target.value;
                    setEditingDay(prev =>
                      prev ? { ...prev, day: { ...prev.day, missedDetails: val } } : null
                    );
                  }}
                  placeholder="Например: Reels #14, карусель, 25 рассылок, +2 клиента"
                  className="w-full text-xs sm:text-sm px-3.5 py-2.5 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setEditingDay(null)}
                className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 transition cursor-pointer"
              >
                Отмена
              </button>
              <button
                type="button"
                onClick={() => {
                  saveDayEdit(editingDay.studentId, editingDay.dayIndex, editingDay.day);
                  setEditingDay(null);
                }}
                className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
              >
                <Save className="w-3.5 h-3.5" />
                <span>Сохранить статус</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {isAddingStudent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-xl space-y-5 rounded-2xl border border-slate-200 bg-white p-6">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-base font-bold text-slate-900">Новая ученица</h3>
                <p className="mt-1 text-xs text-slate-500">Профиль сразу сохранится в браузере, а при подключенном сервере — в базе.</p>
              </div>
              <button type="button" onClick={() => setIsAddingStudent(false)} aria-label="Закрыть" className="rounded-lg p-1 text-slate-500 hover:bg-slate-100">
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="text-xs font-semibold text-slate-600">
                Имя и фамилия
                <input value={newStudent.fullName} onChange={event => setNewStudent(prev => ({ ...prev, fullName: event.target.value }))} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal" />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Ниша
                <input value={newStudent.niche} onChange={event => setNewStudent(prev => ({ ...prev, niche: event.target.value }))} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal" />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Telegram username
                <input value={newStudent.telegram} onChange={event => setNewStudent(prev => ({ ...prev, telegram: event.target.value }))} placeholder="@username" className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal" />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Telegram chat ID
                <input type="number" value={newStudent.telegramChatId} onChange={event => setNewStudent(prev => ({ ...prev, telegramChatId: event.target.value }))} placeholder="Для отправки сообщений" className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal" />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Доход на старте, ₽
                <input type="number" min={0} value={newStudent.initialIncome} onChange={event => setNewStudent(prev => ({ ...prev, initialIncome: Number(event.target.value) || 0 }))} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal" />
              </label>
              <label className="text-xs font-semibold text-slate-600">
                Средний чек на старте, ₽
                <input type="number" min={0} value={newStudent.initialAvgTicket} onChange={event => setNewStudent(prev => ({ ...prev, initialAvgTicket: Number(event.target.value) || 0 }))} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal" />
              </label>
              <label className="text-xs font-semibold text-slate-600 sm:col-span-2">
                Тариф
                <select value={newStudent.tariff} onChange={event => setNewStudent(prev => ({ ...prev, tariff: event.target.value === 'VIP' ? 'VIP' : 'Стандарт' }))} className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-normal">
                  <option value="Стандарт">Стандарт</option>
                  <option value="VIP">VIP</option>
                </select>
              </label>
            </div>
            <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
              <button type="button" onClick={() => setIsAddingStudent(false)} className="rounded-xl px-4 py-2 text-sm font-semibold text-slate-600">Отмена</button>
              <button type="button" onClick={() => void addStudent()} className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white">
                <Save className="h-4 w-4" />
                <span>Создать профиль</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ─── MODAL 2: STUDENT PROFILE & FINANCIALS EDITOR ─── */}
      {editingStudent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 border border-slate-200 space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-base font-bold text-slate-900">Профиль и созвоны</h3>
                <p className="text-xs text-slate-500 mt-0.5">{editingStudent.fullName}</p>
              </div>
              <button
                onClick={() => setEditingStudent(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3.5">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Имя и фамилия:</label>
                  <input
                    type="text"
                    value={editingStudent.fullName}
                    onChange={e => setEditingStudent({ ...editingStudent, fullName: e.target.value })}
                    className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Ниша эксперта:</label>
                  <input
                    type="text"
                    value={editingStudent.niche}
                    onChange={e => setEditingStudent({ ...editingStudent, niche: e.target.value })}
                    className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Telegram username:</label>
                  <input
                    type="text"
                    value={editingStudent.telegram || ''}
                    onChange={event => setEditingStudent({ ...editingStudent, telegram: event.target.value || undefined })}
                    placeholder="@username"
                    className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Telegram chat ID:</label>
                  <input
                    type="number"
                    value={editingStudent.telegramChatId ?? ''}
                    onChange={event => setEditingStudent({ ...editingStudent, telegramChatId: event.target.value ? Number(event.target.value) : undefined })}
                    className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Тариф:</label>
                  <select
                    value={editingStudent.tariff ?? 'Стандарт'}
                    onChange={event => setEditingStudent({ ...editingStudent, tariff: event.target.value === 'VIP' ? 'VIP' : 'Стандарт' })}
                    className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                  >
                    <option value="Стандарт">Стандарт</option>
                    <option value="VIP">VIP</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Дата старта:</label>
                  <input
                    type="date"
                    value={editingStudent.startDate ?? ''}
                    onChange={event => setEditingStudent({ ...editingStudent, startDate: event.target.value })}
                    className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Причина паузы:</label>
                  <input
                    type="text"
                    value={editingStudent.pauseReason ?? ''}
                    onChange={event => setEditingStudent({ ...editingStudent, pauseReason: event.target.value || undefined })}
                    className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                  />
                </div>
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input type="checkbox" checked={editingStudent.isOnPause} onChange={event => setEditingStudent({ ...editingStudent, isOnPause: event.target.checked })} />
                  На паузе
                </label>
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input type="checkbox" checked={editingStudent.isActive ?? true} onChange={event => setEditingStudent({ ...editingStudent, isActive: event.target.checked })} />
                  Активный профиль
                </label>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Точка А (старт, ₽):</label>
                  <input
                    type="number"
                    value={editingStudent.initialIncome}
                    onChange={e => setEditingStudent({ ...editingStudent, initialIncome: Number(e.target.value) || 0 })}
                    className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Точка Б (факт, ₽):</label>
                  <input
                    type="number"
                    value={editingStudent.currentIncome}
                    onChange={e => setEditingStudent({ ...editingStudent, currentIncome: Number(e.target.value) || 0 })}
                    className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500 font-bold text-emerald-600"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Средний чек на старте (₽):</label>
                  <input
                    type="number"
                    value={editingStudent.initialAvgTicket}
                    onChange={e => setEditingStudent({ ...editingStudent, initialAvgTicket: Number(e.target.value) || 0 })}
                    className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Текущий средний чек (₽):</label>
                  <input
                    type="number"
                    value={editingStudent.currentAvgTicket}
                    onChange={e => setEditingStudent({ ...editingStudent, currentAvgTicket: Number(e.target.value) || 0 })}
                    className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500 font-bold"
                  />
                </div>
              </div>

              <section className="space-y-3 border-t border-slate-100 pt-4" aria-labelledby="zoom-session-settings-title">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h4 id="zoom-session-settings-title" className="text-sm font-semibold text-slate-900">Этапы созвонов</h4>
                    <p className="text-xs text-slate-500 mt-0.5">Количество этапов не ограничено</p>
                  </div>
                  <button
                    type="button"
                    onClick={addEditingZoomSession}
                    className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 text-xs font-semibold text-slate-800 hover:bg-slate-100"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Добавить этап</span>
                  </button>
                </div>

                {(editingStudent.zoomSessions ?? []).length === 0 ? (
                  <p className="rounded-xl border border-dashed border-slate-300 px-4 py-6 text-center text-xs text-slate-500">
                    Этапов пока нет. Добавьте первый этап сопровождения.
                  </p>
                ) : (
                  <div className="space-y-3">
                    {(editingStudent.zoomSessions ?? []).map((session, index) => (
                      <div key={`${editingStudent.id}-editing-session-${index}`} className="space-y-3 rounded-xl border border-slate-200 p-4">
                        <div className="flex items-center justify-between gap-3">
                          <span className="text-xs font-semibold uppercase tracking-wide">Этап {index + 1}</span>
                          <button
                            type="button"
                            onClick={() => removeEditingZoomSession(index)}
                            aria-label={`Удалить этап ${index + 1}`}
                            title="Удалить этап"
                            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                          <div className="sm:col-span-2">
                            <label className="mb-1 block text-xs font-semibold text-slate-600">Название этапа</label>
                            <input
                              type="text"
                              value={session.title}
                              onChange={e => updateEditingZoomSession(index, { title: e.target.value })}
                              placeholder="Например: Разбор стратегии"
                              className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                            />
                          </div>
                          <div>
                            <label className="mb-1 block text-xs font-semibold text-slate-600">Дата и время</label>
                            <input
                              type="text"
                              value={session.date}
                              onChange={e => updateEditingZoomSession(index, { date: e.target.value })}
                              placeholder="Например: 12.11 в 19:00"
                              className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                            />
                          </div>
                          <div>
                            <label className="mb-1 block text-xs font-semibold text-slate-600">Статус</label>
                            <select
                              value={session.isCompleted ? 'completed' : 'planned'}
                              onChange={e => updateEditingZoomSession(index, { isCompleted: e.target.value === 'completed' })}
                              className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                            >
                              <option value="planned">Запланирован</option>
                              <option value="completed">Проведен</option>
                            </select>
                          </div>
                          <div>
                            <label className="mb-1 block text-xs font-semibold text-slate-600">Ссылка на созвон</label>
                            <input
                              type="url"
                              value={session.zoomLink ?? ''}
                              onChange={e => updateEditingZoomSession(index, { zoomLink: e.target.value || undefined })}
                              placeholder="https://…"
                              className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                            />
                          </div>
                          <div>
                            <label className="mb-1 block text-xs font-semibold text-slate-600">Ссылка на запись</label>
                            <input
                              type="url"
                              value={session.recordingUrl ?? ''}
                              onChange={e => updateEditingZoomSession(index, { recordingUrl: e.target.value || undefined })}
                              placeholder="https://…"
                              className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                            />
                          </div>
                          <div className="sm:col-span-2">
                            <label className="mb-1 block text-xs font-semibold text-slate-600">Итог и заметки</label>
                            <textarea
                              rows={2}
                              value={session.keyTakeaway ?? ''}
                              onChange={e => updateEditingZoomSession(index, { keyTakeaway: e.target.value || undefined })}
                              placeholder="Ключевые договоренности и итоги этапа"
                              className="w-full rounded-xl border border-slate-200 px-3.5 py-2 text-sm"
                            />
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              <div>
                <label className="text-xs font-semibold text-slate-600 block mb-1">Финансовая цель потока (₽):</label>
                <input
                  type="number"
                  value={editingStudent.portalData?.targetIncome || 100000}
                  onChange={e => {
                    const val = Number(e.target.value) || 100000;
                    setEditingStudent({
                      ...editingStudent,
                      portalData: editingStudent.portalData
                        ? { ...editingStudent.portalData, targetIncome: val }
                        : {
                            targetIncome: val,
                            currentStreakDays: 2,
                            dailyChecklist: { contentSubmitted: false, mailingsDone: 8, mailingsTarget: 15, reflectionDone: false },
                            todayDeadline: '21:00',
                            scripts: [],
                            zoomSessions: [],
                          },
                    });
                  }}
                  className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setEditingStudent(null)}
                className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 transition cursor-pointer"
              >
                Отмена
              </button>
              <button
                type="button"
                onClick={() => {
                  void saveStudentProfile(editingStudent);
                  setEditingStudent(null);
                }}
                className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
              >
                <Save className="w-3.5 h-3.5" />
                <span>Сохранить профиль</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ─── MODAL 3: SCRIPT EDITOR / CREATOR ─── */}
      {(editingScript || isAddingScript) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 border border-slate-200 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  {isAddingScript ? 'Добавить новый скрипт в Direct' : 'Редактировать скрипт'}
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">База готовых скриптов ученицы</p>
              </div>
              <button
                onClick={() => {
                  setEditingScript(null);
                  setIsAddingScript(false);
                }}
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
                onClick={() => {
                  setEditingScript(null);
                  setIsAddingScript(false);
                }}
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
                    void addScript(newScript);
                    setNewScript({ title: '', description: '', text: '' });
                    setIsAddingScript(false);
                  } else if (editingScript) {
                    void saveScript(editingScript.index, {
                      title: editingScript.title,
                      description: editingScript.description,
                      text: editingScript.text,
                    });
                    setEditingScript(null);
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
      )}

      {/* ─── MODAL 4: HASHTAG RULE EDITOR / CREATOR ─── */}
      {(editingRule || isAddingRule) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 border border-slate-200 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  {isAddingRule ? 'Новый хэштег / задание в боте' : `Редактирование: ${editingRule?.hashtag}`}
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">Настройка регламента и парсинга Telegram</p>
              </div>
              <button
                onClick={() => {
                  setEditingRule(null);
                  setIsAddingRule(false);
                }}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Хэштег (с символом #):</label>
                  <input
                    type="text"
                    value={isAddingRule ? newRule.hashtag : editingRule?.hashtag || ''}
                    onChange={e => {
                      const val = e.target.value.startsWith('#') ? e.target.value : `#${e.target.value}`;
                      if (isAddingRule) setNewRule(prev => ({ ...prev, hashtag: val }));
                      else if (editingRule) setEditingRule({ ...editingRule, hashtag: val });
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
                  onClick={() => {
                    if (isAddingRule) setNewRule(prev => ({ ...prev, required: !prev.required }));
                    else if (editingRule) setEditingRule({ ...editingRule, required: !editingRule.required });
                  }}
                  className={`text-xs font-semibold px-3 py-1 rounded-lg border transition cursor-pointer ${
                    (isAddingRule ? newRule.required : editingRule?.required)
                      ? 'bg-rose-50 text-rose-700 border-rose-200'
                      : 'bg-slate-100 text-slate-600 border-slate-200'
                  }`}
                >
                  {(isAddingRule ? newRule.required : editingRule?.required) ? 'обязательно' : 'по факту'}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
              <button
                type="button"
                onClick={() => {
                  setEditingRule(null);
                  setIsAddingRule(false);
                }}
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
                    void addRule(newRule);
                    setNewRule({ hashtag: '#', title: '', description: '', required: false, example: '' });
                    setIsAddingRule(false);
                  } else if (editingRule) {
                    void saveRule(editingRule);
                    setEditingRule(null);
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
      )}

      {/* ─── MODAL 5: SCHEDULE TIME EDITOR ─── */}
      {editingScheduleTime && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl max-w-sm w-full p-6 border border-slate-200 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-base font-bold text-slate-900">Изменить время расписания</h3>
                <p className="text-xs text-slate-500 mt-0.5">{editingScheduleTime.label}</p>
              </div>
              <button
                onClick={() => setEditingScheduleTime(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1">Время (ЧЧ:ММ):</label>
              <input
                type="text"
                value={editingScheduleTime.value}
                onChange={e => setEditingScheduleTime({ ...editingScheduleTime, value: e.target.value })}
                placeholder="09:00"
                className="w-full text-sm px-3.5 py-2.5 rounded-xl border border-slate-200 font-mono text-center font-bold"
              />
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setEditingScheduleTime(null)}
                className="px-4 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 transition cursor-pointer"
              >
                Отмена
              </button>
              <button
                type="button"
                onClick={() => {
                  updateScheduleTime(editingScheduleTime.key, editingScheduleTime.value, editingScheduleTime.label);
                  setEditingScheduleTime(null);
                }}
                className="px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-xl hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
              >
                <Save className="w-3.5 h-3.5" />
                <span>Сохранить</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toast */}
      {toastMessage && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 bg-slate-900 text-white px-5 py-3 rounded-xl text-sm font-semibold">
          {toastMessage}
        </div>
      )}
    </div>
  );
}
