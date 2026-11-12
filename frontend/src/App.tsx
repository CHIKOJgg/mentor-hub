import { useState, useRef, useEffect, useMemo } from 'react';
import { BEAUTY_NICHES, type DailyReport, type StudentDiscipline, type TabType, type ContentType, type TaskRule, type SystemSettings, type DayInfo, type ZoomSessionRecord, type CheatsheetItem } from './types';
import type { ApiAdminSession, ApiCuratorScript, ApiDailyReport, ApiStudent, ApiSystemSettings, ApiTaskRule, ApiZoomSession } from './api/contracts';
import { api } from './api/client';
import { readPersisted, writePersisted } from './persistence';
import { ConfirmDialog } from './components/common/ConfirmDialog';
import { DayStatusModal } from './components/modals/DayStatusModal';
import { AddStudentModal } from './components/modals/AddStudentModal';
import { StudentSettingsModal } from './components/modals/StudentSettingsModal';
import { EditStudentModal } from './components/modals/EditStudentModal';
import { ScriptModal } from './components/modals/ScriptModal';
import { RuleModal } from './components/modals/RuleModal';
import { ScheduleTimeModal } from './components/modals/ScheduleTimeModal';
import { StudentDossierModal } from './components/modals/StudentDossierModal';
import { ZoomModal, type ZoomFormState } from './components/modals/ZoomModal';
import { CheatsheetModal } from './components/modals/CheatsheetModal';
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
  Settings,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  CheckCircle2,
  History,
  Sparkles,
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

const getIsoTimestamp = (): string => {
  return new Date().toISOString();
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
  'Стратегия, аудит воронки и распаковка Точки А',
  'Позиционирование и упаковка премиум-предложения',
  'Контентная воронка Reels и каруселей без выгорания',
  'Скрипты квалификации и дожима лидов в Direct',
  'Продажи на высокий чек и закрытие возражений',
  'Оцифровка базы клиентов и повторные продажи',
  'Масштабирование трафика и делегирование рутины',
  'Итоговая защита Точки Б и стратегия масштабирования',
];

const ensureContractZoomSessions = (student: StudentDiscipline, sessions: ZoomSessionRecord[]): ZoomSessionRecord[] => {
  const quota = Math.max(8, student.contractZoomQuota ?? 8);
  const byNumber = new Map<number, ZoomSessionRecord>();
  sessions.forEach((session, index) => {
    const callNumber = session.callNumber || index + 1;
    if (!byNumber.has(callNumber)) byNumber.set(callNumber, { ...session, callNumber });
  });
  for (let callNumber = 1; callNumber <= quota; callNumber += 1) {
    if (!byNumber.has(callNumber)) {
      byNumber.set(callNumber, {
        callNumber,
        title: defaultZoomSessionTitles[callNumber - 1] ?? `Этап сопровождения ${callNumber}`,
        date: '',
        isCompleted: false,
      });
    }
  }
  return [...byNumber.values()].sort((left, right) => left.callNumber - right.callNumber);
};

const getStudentZoomSessions = (student: StudentDiscipline): ZoomSessionRecord[] => {
  const configuredSessions = student.zoomSessions ?? student.portalData?.zoomSessions;
  if (configuredSessions) {
    return ensureContractZoomSessions(student, configuredSessions);
  }

  const sessionCount = Math.max(
    student.contractZoomQuota || 8,
    defaultZoomSessionTitles.length,
    student.completedZoomsCount + (student.nextZoomDate ? 1 : 0),
  );

  return ensureContractZoomSessions(student, Array.from({ length: sessionCount }, (_, index) => ({
    callNumber: index + 1,
    title: defaultZoomSessionTitles[index] ?? `Этап сопровождения ${index + 1}`,
    date: index === student.completedZoomsCount ? student.nextZoomDate ?? '' : '',
    isCompleted: index < student.completedZoomsCount,
  })));
};

const withZoomSessionSummary = (
  student: StudentDiscipline,
  sessions: ZoomSessionRecord[],
): StudentDiscipline => {
  const zoomSessions = ensureContractZoomSessions(student, sessions);
  const nextSession = zoomSessions.find(session => !session.isCompleted);
  const nextZoomDate = nextSession?.date.trim() || undefined;

  return {
    ...student,
    contractZoomQuota: student.contractZoomQuota ?? 8,
    zoomSessions,
    completedZoomsCount: zoomSessions.filter(session => session.isCompleted).length,
    nextZoomDate,
  };
};

const newStudentTemplate = {
  fullName: '',
  niche: BEAUTY_NICHES[0] as string,
  telegram: '',
  telegramChatId: '',
  initialIncome: 0,
  initialAvgTicket: 0,
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
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const parsed = new Date(`${raw}T12:00:00+03:00`);
    return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
  }
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

const getZoomSessionDate = (session: ZoomSessionRecord): Date | null => {
  const rawValue = session.scheduledAt || session.date;
  if (!rawValue.trim()) return null;
  const value = parseZoomDate(rawValue);
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

const getMinskDateKey = (date: Date): string => {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: 'Europe/Minsk',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  const year = parts.find(part => part.type === 'year')?.value ?? '';
  const month = parts.find(part => part.type === 'month')?.value ?? '';
  const day = parts.find(part => part.type === 'day')?.value ?? '';
  return `${year}-${month}-${day}`;
};

const getZoomSessionTimeLabel = (session: ZoomSessionRecord): string => {
  const explicitTime = session.date.match(/(?:в\s*)(\d{1,2}:\d{2})\s*$/i)?.[1];
  if (explicitTime) return explicitTime;
  if (session.scheduledAt) {
    return new Date(session.scheduledAt).toLocaleTimeString('ru-RU', {
      timeZone: 'Europe/Minsk',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
  return 'Время не указано';
};

const parseLinkSentAt = (value: string | undefined, sessionDate: string): string | null => {
  const raw = value?.trim();
  if (!raw) return null;
  const parsed = new Date(raw);
  if (!Number.isNaN(parsed.getTime()) && /[Tz]/i.test(raw)) return parsed.toISOString();
  if (/^\d{1,2}:\d{2}$/.test(raw)) {
    if (/^\d{4}-\d{2}-\d{2}$/.test(sessionDate)) {
      const dateWithTime = new Date(`${sessionDate}T${raw}:00+03:00`);
      return Number.isNaN(dateWithTime.getTime()) ? null : dateWithTime.toISOString();
    }
    return parseZoomDate(`${sessionDate} в ${raw}`);
  }
  return parseZoomDate(raw.replace(/\s+(\d{1,2}:\d{2})$/, ' в $1'));
};

const mapApiZoomSession = (session: ApiZoomSession, index: number): ZoomSessionRecord => ({
  callNumber: session.callNumber ?? index + 1,
  title: session.title,
  date: session.scheduledAt ? new Date(session.scheduledAt).toLocaleString('ru-RU', { timeZone: 'Europe/Minsk', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).replace(',', ' в') : '',
  scheduledAt: session.scheduledAt,
  isCompleted: session.isCompleted,
  zoomLink: session.zoomUrl,
  recordingUrl: session.recordingUrl,
  keyTakeaway: session.meetingNotes,
  linkSentAt: session.linkSentAt,
  evidenceUrl: session.evidenceUrl,
  sourceChatId: session.sourceChatId,
  sourceMessageId: session.sourceMessageId,
});

const serializeZoomSessions = (sessions: ZoomSessionRecord[]) => sessions.map((session, index) => ({
  callNumber: session.callNumber || index + 1,
  title: session.title,
  zoomUrl: session.zoomLink ?? null,
  recordingUrl: session.recordingUrl ?? null,
  scheduledAt: parseZoomDate(session.scheduledAt || session.date),
  linkSentAt: parseLinkSentAt(session.linkSentAt, session.date),
  evidenceUrl: session.evidenceUrl ?? null,
  isCompleted: session.isCompleted,
  meetingNotes: session.keyTakeaway ?? null,
}));

type LocalDataDomain = 'reports' | 'students' | 'task-rules' | 'system-settings' | 'curator-scripts';

const emptyStudent: StudentDiscipline = {
  id: '',
  fullName: '',
  avatarText: '',
  niche: '',
  initialIncome: 0,
  currentIncome: 0,
  initialAvgTicket: 0,
  currentAvgTicket: 0,
  totalReels: 0,
  totalCarousels: 0,
  totalStorytells: 0,
  totalMailings: 0,
  totalNewClients: 0,
  disciplineRate: 0,
  weekDays: [],
  isOnPause: false,
  completedZoomsCount: 0,
  zoomSessions: [],
};

const defaultSystemSettings: SystemSettings = {
  morningPushEnabled: true,
  morningPushTime: '09:00',
  eveningReminderEnabled: true,
  eveningReminderTime: '20:00',
  dailyDeadlineTime: '21:00',
  strictDeadlineEnabled: true,
  saturdayPollEnabled: true,
  saturdayPollTime: '18:00',
  sundayRestEnabled: true,
  aiWhisperEnabled: true,
  aiSummaryEnabled: true,
  antiFraudDedupEnabled: true,
  zoomAutoDetectEnabled: true,
};

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
    contractZoomQuota: student.contractZoomQuota ?? 8,
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

const getUniqueDrafts = (drafts?: DailyReport['contentDrafts']) => {
  if (!drafts || drafts.length === 0) return [];
  return drafts.filter((draft, idx, self) =>
    idx === self.findIndex(d => {
      if (d.url && draft.url && (d.url === draft.url || d.url.includes(draft.url) || draft.url.includes(d.url))) return true;
      if (draft.label && d.url && draft.label.includes(d.url)) return true;
      if (d.label && draft.url && d.label.includes(draft.url)) return true;
      return d.label === draft.label && d.type === draft.type;
    })
  );
};

export default function App() {
  const [activeTab, setActiveTab] = useState<TabType>(() => {
    const saved = readPersisted<TabType>('active-tab', 'inbox');
    return saved === 'tasks' ? 'promotion' : saved;
  });
  const [inboxView, setInboxView] = useState<'pending' | 'approved'>('pending');
  const [reports, setReports] = useState<DailyReport[]>(() => readPersisted('reports', []));
  const [allReports, setAllReports] = useState<DailyReport[]>(() => readPersisted('all-reports', []));
  const [students, setStudents] = useState<StudentDiscipline[]>(() =>
    readPersisted<StudentDiscipline[]>('students', [])
      .map(student => withZoomSessionSummary(student, getStudentZoomSessions(student))),
  );
  const [replies, setReplies] = useState<Record<string, string>>(() => readPersisted('replies', {}));
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [selectedStudentId, setSelectedStudentId] = useState<string>(() =>
    readPersisted('selected-student-id', ''),
  );
  const selectedStudent = useMemo(
    () => students.find(student => student.id === selectedStudentId) ?? students[0] ?? emptyStudent,
    [students, selectedStudentId],
  );
  const setSelectedStudent = (student: StudentDiscipline) => {
    setSelectedStudentId(student.id);
  };
  const [searchQuery, setSearchQuery] = useState('');
  const [analyticsCategory, setAnalyticsCategory] = useState<'cohort' | 'students'>('cohort');
  const [analyticsPeriod, setAnalyticsPeriod] = useState<'week' | 'month' | 'all'>('all');
  const [rules, setRules] = useState<TaskRule[]>(() => readPersisted('task-rules', []));
  const [systemSettings, setSystemSettings] = useState<SystemSettings>(() => readPersisted('system-settings', defaultSystemSettings));
  const [promotionSubTab, setPromotionSubTab] = useState<'scripts' | 'cheatsheets' | 'hashtags'>('scripts');
  const [studentsFilter, setStudentsFilter] = useState<'all' | 'perfect' | 'attention' | 'pause' | 'zooms'>('all');
  const [studentsViewMode, setStudentsViewMode] = useState<'calendar' | 'zooms'>('calendar');
  const [zoomCalendarWeekOffset, setZoomCalendarWeekOffset] = useState(0);
  const [zoomCalendarReferenceDate] = useState(() => {
    const date = new Date();
    date.setHours(12, 0, 0, 0);
    return date;
  });
  const [selectedScriptCategory, setSelectedScriptCategory] = useState<string>('Все');
  const [curatorScripts, setCuratorScripts] = useState<ApiCuratorScript[]>(() => readPersisted('curator-scripts', []));
  const [cheatsheets, setCheatsheets] = useState<CheatsheetItem[]>(() => readPersisted('cheatsheets', []));
  const [selectedCheatsheetCategory, setSelectedCheatsheetCategory] = useState<string>('Все');
  const [isAddingCheatsheet, setIsAddingCheatsheet] = useState(false);
  const [editingCheatsheet, setEditingCheatsheet] = useState<CheatsheetItem | null>(null);
  const [newCheatsheet, setNewCheatsheet] = useState<{ title: string; category: CheatsheetItem['category']; description: string; content: string }>({
    title: '',
    category: 'reels',
    description: '',
    content: '',
  });

  const [inspectingStudent, setInspectingStudent] = useState<StudentDiscipline | null>(null);
  const [inspectingTab, setInspectingTab] = useState<'profile' | 'reports' | 'zooms'>('profile');
  const [managingZoomStudent, setManagingZoomStudent] = useState<StudentDiscipline | null>(null);
  const [isAddingZoom, setIsAddingZoom] = useState(false);
  const [zoomForm, setZoomForm] = useState<ZoomFormState>({
    title: 'Разбор оффера и чека',
    scheduledAt: '',
    zoomUrl: '',
    meetingNotes: '',
    linkSentAt: '',
    evidenceUrl: '',
  });

  const [accessKeyDraft, setAccessKeyDraft] = useState('');
  const [isLoginSubmitting, setIsLoginSubmitting] = useState(false);
  const [apiSession, setApiSession] = useState<ApiAdminSession | null>(null);
  const [apiConnection, setApiConnection] = useState<'checking' | 'connected' | 'offline' | 'unauthorized' | 'needs-token'>('checking');
  const [apiConnectionMessage, setApiConnectionMessage] = useState('Проверяем подключение');
  const [isApiSyncing, setIsApiSyncing] = useState(false);
  const [apiRefreshKey, setApiRefreshKey] = useState(0);
  const [pendingSyncDomains, setPendingSyncDomains] = useState<string[]>(() => readPersisted('pending-sync-domains', []));
  const [isAddingStudent, setIsAddingStudent] = useState(false);
  const [isManagingStudents, setIsManagingStudents] = useState(false);
  const [newStudent, setNewStudent] = useState<{ fullName: string; niche: string; telegram: string; telegramChatId: string; initialIncome: number; initialAvgTicket: number; tariff?: string }>(newStudentTemplate);

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
  const [rejectErrorReportId, setRejectErrorReportId] = useState<string | null>(null);
  const [confirmModal, setConfirmModal] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    isDanger?: boolean;
    onConfirm: () => void | Promise<void>;
  } | null>(null);

  const requestConfirm = (options: {
    title: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    isDanger?: boolean;
    onConfirm: () => void | Promise<void>;
  }) => {
    setConfirmModal({
      isOpen: true,
      title: options.title,
      message: options.message,
      confirmLabel: options.confirmLabel || 'Подтвердить',
      cancelLabel: options.cancelLabel || 'Отмена',
      isDanger: options.isDanger !== false,
      onConfirm: options.onConfirm,
    });
  };

  useEffect(() => {
    writePersisted('active-tab', activeTab);
    writePersisted('reports', reports);
    writePersisted('all-reports', allReports);
    writePersisted('students', students);
    writePersisted('replies', replies);
    writePersisted('selected-student-id', selectedStudentId);
    writePersisted('selected-student', selectedStudent);
    writePersisted('task-rules', rules);
    writePersisted('system-settings', systemSettings);
    writePersisted('curator-scripts', curatorScripts);
    writePersisted('cheatsheets', cheatsheets);
    writePersisted('pending-sync-domains', pendingSyncDomains);
  }, [activeTab, reports, allReports, students, replies, selectedStudentId, selectedStudent, rules, systemSettings, curatorScripts, cheatsheets, pendingSyncDomains]);

  useEffect(() => {
    let isCurrent = true;

    const loadRemoteData = async () => {
      setApiConnection('checking');
      setApiConnectionMessage('Проверяем подключение');

      let currentSession: ApiAdminSession;
      try {
        currentSession = await api.getCurrentSession<ApiAdminSession>();
      } catch (error) {
        if (isCurrent) {
          const unauthorized = error instanceof Error && 'status' in error && error.status === 401;
          setApiSession(null);
          setApiConnection(unauthorized ? 'needs-token' : 'offline');
          setApiConnectionMessage(unauthorized ? 'Введите ключ доступа администратора.' : 'Не удалось подключиться. Проверьте соединение.');
        }
        return;
      }
      if (!isCurrent) return;
      if (!currentSession.authenticated) {
        setApiSession(null);
        setApiConnection('needs-token');
        setApiConnectionMessage('Введите ключ доступа администратора.');
        return;
      }
      setApiSession(currentSession);

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
        setApiSession(null);
        setApiConnection('needs-token');
        setApiConnectionMessage('Нет доступа к серверу. Обратитесь к администратору.');
      } else if (loadedCount === results.length) {
        setApiConnection('connected');
        setApiConnectionMessage('Данные синхронизированы.');
      } else if (loadedCount > 0) {
        setApiConnection('connected');
        setApiConnectionMessage('Подключено частично. Некоторые данные временно недоступны.');
      } else {
        setApiConnection('offline');
        setApiConnectionMessage('Не удалось загрузить данные. Проверьте соединение и повторите попытку.');
      }
    };

    void loadRemoteData();
    return () => {
      isCurrent = false;
    };
  }, [apiRefreshKey, pendingSyncDomains]);

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
        setApiConnectionMessage('Изменение дня ожидает синхронизации.');
      }
    } else {
      markLocalChange('students', studentId);
    }
    showToast(`Статус за ${updatedDay.date} обновлен; ожидает синхронизации`);
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
        setApiConnectionMessage('Профиль ожидает синхронизации.');
      }
    } else {
      markLocalChange('students', localKey);
    }
    showToast(`Профиль ${normalized.fullName} обновлен; ожидает синхронизации`);
  };

  const addStudent = async () => {
    const fullName = newStudent.fullName.trim();
    const niche = newStudent.niche.trim();
    if (!fullName || !niche) {
      showToast('Укажите имя учениц…43969 tokens truncated…                    target="_blank"
                      rel="noopener noreferrer"
                      className="px-3.5 py-1.5 rounded-xl bg-indigo-50 text-indigo-700 hover:bg-indigo-100 text-xs font-semibold transition cursor-pointer flex items-center gap-1.5"
                    >
                      <span>Открыть бота</span>
                      <ExternalLink className="w-3.5 h-3.5" />
                    </a>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
                      <div className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="w-5 h-5 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-[11px] font-bold">1</span>
                        <span>Создайте группу</span>
                      </div>
                      <p className="text-xs text-slate-500">Создайте мини-чат в Telegram с Ксенией, Катей и новой ученицей</p>
                    </div>
                    <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
                      <div className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="w-5 h-5 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-[11px] font-bold">2</span>
                        <span>Добавьте бота</span>
                      </div>
                      <p className="text-xs text-slate-500">Добавьте бота администратором с правом чтения сообщений</p>
                    </div>
                    <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
                      <div className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="w-5 h-5 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-[11px] font-bold">3</span>
                        <span>Отправьте отчёт</span>
                      </div>
                      <p className="text-xs text-slate-500">Бот сам определит группу и автоматически привяжет её к дашборду наставника</p>
                    </div>
                  </div>
                </div>

                {/* Message processing and integrity controls */}
                <div className="bg-white rounded-2xl border border-slate-200/60 p-6 space-y-4 hover:border-slate-300 transition">
                  <div className="flex items-center gap-2 pb-3 border-b border-slate-100">
                    <ShieldCheck className="w-5 h-5 text-indigo-600" />
                    <div>
                      <h3 className="text-base font-bold text-slate-900">Обработка сообщений и контроль данных</h3>
                    </div>
                  </div>

                  <div className="divide-y divide-slate-100">
                    {/* Setting 6: Voice transcription */}
                    <div className="py-3.5 flex items-center justify-between">
                      <div>
                        <div className="text-sm font-semibold text-slate-900">Расшифровка голосовых сообщений</div>
                        <div className="text-xs text-slate-500">Автоматический перевод аудиоотчётов в текст</div>
                      </div>
                      <button
                        onClick={() => toggleSystemSetting('aiWhisperEnabled', 'Расшифровка голосовых')}
                        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                          systemSettings.aiWhisperEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                        }`}
                      >
                        <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white transition duration-200 ease-in-out ${
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
                        <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white transition duration-200 ease-in-out ${
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
                        <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white transition duration-200 ease-in-out ${
                          systemSettings.antiFraudDedupEnabled ? 'translate-x-4' : 'translate-x-0'
                        }`} />
                      </button>
                    </div>

                    {/* Setting 9: Video Meeting Link Auto Detect */}
                    <div className="py-3.5 flex items-center justify-between">
                      <div>
                        <div className="text-sm font-semibold text-slate-900">Сохранять ссылки на Zoom, Meet и Телемост из Telegram</div>
                        <div className="text-xs text-slate-500">Записывает ссылку, время отправки и сообщение-источник; приглашение не засчитывается как проведённый созвон</div>
                      </div>
                      <button
                        onClick={() => toggleSystemSetting('zoomAutoDetectEnabled', 'Авто-определение Zoom-созвонов')}
                        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                          systemSettings.zoomAutoDetectEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                        }`}
                      >
                        <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white transition duration-200 ease-in-out ${
                          systemSettings.zoomAutoDetectEnabled ? 'translate-x-4' : 'translate-x-0'
                        }`} />
                      </button>
                    </div>
                  </div>
                </div>

                {/* Data export */}
                <div className="bg-white rounded-2xl border border-slate-200/60 p-6 flex flex-col sm:flex-row items-center justify-between gap-4">
                  <div>
                    <h3 className="text-base font-bold text-slate-900">Экспорт данных потока</h3>
                    <p className="text-xs text-slate-500 mt-0.5">Полная резервная копия учениц, отчётов и созвонов</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={exportFullBackup}
                      className="px-4 py-2 rounded-xl bg-slate-900 text-white text-xs font-semibold hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>Скачать данные</span>
                    </button>
                  </div>
                </div>
              </div>
            )}

          </div>
        </main>
      </div>

      {/* ─── MODAL 1: DAY STATUS & DETAILS EDITOR ─── */}
      <DayStatusModal
        editingDay={editingDay}
        onClose={() => setEditingDay(null)}
        onSave={saveDayEdit}
        onUpdateDay={updatedDay => {
          setEditingDay(prev => prev ? { ...prev, day: updatedDay } : null);
        }}
      />

      {/* ─── MODAL 2: ADD STUDENT MODAL ─── */}
      <AddStudentModal
        isOpen={isAddingStudent}
        newStudent={newStudent}
        onClose={() => {
          setIsAddingStudent(false);
          setNewStudent(newStudentTemplate);
        }}
        onUpdate={updater => setNewStudent(updater)}
        onSubmit={addStudent}
      />

      {/* ─── MODAL 2A: STUDENT SETTINGS & ROSTER MANAGEMENT ─── */}
      <StudentSettingsModal
        isOpen={isManagingStudents}
        onClose={() => setIsManagingStudents(false)}
        students={students}
        onEditStudent={s => setEditingStudent(s)}
        onAddStudent={() => setIsAddingStudent(true)}
      />

      {/* ─── MODAL 2B: STUDENT PROFILE & FINANCIALS EDITOR ─── */}
      <EditStudentModal
        student={editingStudent}
        setEditingStudent={setEditingStudent}
        onClose={() => setEditingStudent(null)}
        onSave={saveStudentProfile}
        addEditingZoomSession={addEditingZoomSession}
        removeEditingZoomSession={removeEditingZoomSession}
        updateEditingZoomSession={updateEditingZoomSession}
        onDelete={deleteStudent}
      />

      {/* ─── MODAL 3: SCRIPT EDITOR / CREATOR ─── */}
      <ScriptModal
        isOpen={Boolean(editingScript || isAddingScript)}
        isAddingScript={isAddingScript}
        editingScript={editingScript}
        newScript={newScript}
        setNewScript={setNewScript}
        setEditingScript={setEditingScript}
        onClose={() => {
          setEditingScript(null);
          setIsAddingScript(false);
          setNewScript({ title: '', description: '', text: '' });
        }}
        onAdd={addScript}
        onSave={saveScript}
        showToast={showToast}
      />

      {/* ─── MODAL 4: HASHTAG RULE EDITOR / CREATOR ─── */}
      <RuleModal
        isOpen={Boolean(editingRule || isAddingRule)}
        isAddingRule={isAddingRule}
        editingRule={editingRule}
        newRule={newRule}
        setNewRule={setNewRule}
        setEditingRule={setEditingRule}
        onClose={() => {
          setEditingRule(null);
          setIsAddingRule(false);
          setNewRule({ hashtag: '#', title: '', description: '', required: false, example: '' });
        }}
        onAdd={addRule}
        onSave={saveRule}
        showToast={showToast}
      />

      {/* ─── MODAL 5: SCHEDULE TIME EDITOR ─── */}
      <ScheduleTimeModal
        editingScheduleTime={editingScheduleTime}
        setEditingScheduleTime={setEditingScheduleTime}
        onClose={() => setEditingScheduleTime(null)}
        onSave={(key, value, label) => updateScheduleTime(key, value, label)}
      />

      {/* ─── MODAL 6: STUDENT DOSSIER (PROFILE, HISTORY, ZOOMS) ─── */}
      <StudentDossierModal
        student={inspectingStudent}
        inspectingTab={inspectingTab}
        setInspectingTab={setInspectingTab}
        allReports={allReports}
        onClose={() => setInspectingStudent(null)}
        onSendMessage={sendStudentMessage}
        onManageZooms={student => {
          setManagingZoomStudent(student);
          setInspectingStudent(null);
        }}
        onToggleZoomCompleted={toggleZoomSessionCompleted}
      />

      {/* ─── MODAL 7: ZOOM MANAGEMENT MODAL ─── */}
      <ZoomModal
        student={managingZoomStudent}
        isAddingZoom={isAddingZoom}
        setIsAddingZoom={setIsAddingZoom}
        zoomForm={zoomForm}
        setZoomForm={setZoomForm}
        onToggleCompleted={toggleZoomSessionCompleted}
        onAddZoomSession={addZoomSessionToStudent}
        onClose={() => {
          setManagingZoomStudent(null);
          setIsAddingZoom(false);
          setZoomForm({ title: 'Разбор оффера и чека', scheduledAt: '', zoomUrl: '', meetingNotes: '', linkSentAt: '', evidenceUrl: '' });
        }}
      />

      {/* ─── MODAL 8: CHEATSHEET EDITOR / CREATOR ─── */}
      <CheatsheetModal
        isOpen={Boolean(isAddingCheatsheet || editingCheatsheet)}
        isAddingCheatsheet={isAddingCheatsheet}
        editingCheatsheet={editingCheatsheet}
        newCheatsheet={newCheatsheet}
        setEditingCheatsheet={setEditingCheatsheet}
        setNewCheatsheet={setNewCheatsheet}
        onClose={() => {
          setIsAddingCheatsheet(false);
          setEditingCheatsheet(null);
          setNewCheatsheet({ title: '', category: 'reels', description: '', content: '' });
        }}
        onSave={saveCheatsheet}
        onAdd={addCheatsheet}
      />

      {/* ─── CONFIRMATION DIALOG MODAL ─── */}
      {confirmModal && (
        <ConfirmDialog
          isOpen={confirmModal.isOpen}
          title={confirmModal.title}
          message={confirmModal.message}
          confirmLabel={confirmModal.confirmLabel}
          cancelLabel={confirmModal.cancelLabel}
          isDanger={confirmModal.isDanger}
          onConfirm={confirmModal.onConfirm}
          onClose={() => setConfirmModal(null)}
        />
      )}

{/* Toast */}
      {toastMessage && (
        <div className="app-toast fixed left-1/2 -translate-x-1/2 z-50 bg-slate-900 text-white px-5 py-3 rounded-xl text-sm font-semibold" role="status" aria-live="polite">
          {toastMessage}
        </div>
      )}
    </div>
  );
}
