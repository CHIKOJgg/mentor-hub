export type ContentType = 'reel' | 'carousel' | 'storytell';

export interface ContentDraft {
  id: string;
  type: ContentType;
  label: string;
  isDuplicate: boolean;
  duplicateDate?: string;
  url?: string;
}

// Backward compatibility alias if needed
export interface ReelSubmission {
  id: string;
  url: string;
  shortcode: string;
  isValid: boolean;
  isDuplicate: boolean;
  duplicateDate?: string;
}

export interface ReflectionSummary {
  emotions: string;
  actions: string;
  questionToMentor?: string;
}

export interface DailyReport {
  id: string;
  studentId: string;
  studentName: string;
  avatarText: string;
  tgUsername: string;
  tariff?: string;
  submittedAt: string;
  reportDate?: string;
  isOnTime: boolean;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  mentorReply?: string;
  contentDrafts: ContentDraft[];
  reels?: ReelSubmission[];
  salesAmount: number;
  mailingsCount: number;
  newClientsCount: number;
  mentorQuestion?: string;
  voiceTranscript?: string;
  reflectionSummary?: ReflectionSummary;
  aiSummary: string;
}

export type DayStatus =
  | 'COMPLETED_ON_TIME'
  | 'COMPLETED_LATE'
  | 'MISSED'
  | 'PENDING'
  | 'WEEKEND'
  | 'PAUSED';

export interface DayInfo {
  day: string;
  date: string;
  status: DayStatus;
  missedDetails?: string;
  isoDate?: string;
}

export interface StudentPeriodMetrics {
  income: number;
  newClients: number;
  reels: number;
  carousels: number;
  storytells: number;
  mailings: number;
  disciplineRate: number;
  reflectionSentiment: string;
  keyWin: string;
}

export interface ZoomSessionRecord {
  callNumber: number;
  title: string;
  date: string;
  scheduledAt?: string;
  isCompleted: boolean;
  zoomLink?: string;
  recordingUrl?: string;
  keyTakeaway?: string;
  linkSentAt?: string; // ISO-время отправки ссылки в рабочий чат Telegram
  evidenceUrl?: string; // Ссылка на сообщение/скриншот, подтверждающий консультацию
  sourceChatId?: number;
  sourceMessageId?: number;
}

export interface StudentPortalData {
  targetIncome: number; // e.g. 100 000 ₽
  currentStreakDays: number;
  dailyChecklist: {
    contentSubmitted: boolean;
    mailingsDone: number;
    mailingsTarget: number;
    reflectionDone: boolean;
  };
  todayDeadline: string;
  scripts: {
    title: string;
    description: string;
    text: string;
  }[];
  zoomSessions: ZoomSessionRecord[];
}

export interface StudentDiscipline {
  id: string;
  fullName: string;
  avatarText: string;
  telegram?: string;
  niche: string;
  telegramChatId?: number;
  tariff?: string;
  startDate?: string;
  isActive?: boolean;
  initialIncome: number; // Point A
  currentIncome: number; // Point B
  initialAvgTicket: number;
  currentAvgTicket: number;
  totalReels: number;
  totalCarousels: number;
  totalStorytells: number;
  totalMailings: number;
  totalNewClients: number;
  disciplineRate: number; // e.g. 95%
  weekDays: DayInfo[];
  isOnPause: boolean;
  pauseReason?: string;
  nextZoomDate?: string;
  completedZoomsCount: number;
  zoomSessions?: ZoomSessionRecord[];
  contractZoomQuota?: number; // Базовый объем по договору (по умолчанию 8)
  periodStats?: {
    week: StudentPeriodMetrics;
    month: StudentPeriodMetrics;
    all: StudentPeriodMetrics;
  };
  portalData?: StudentPortalData;
}

export interface TaskRule {
  id: string;
  hashtag: string;
  title: string;
  description: string;
  required: boolean;
  example: string;
  enabled?: boolean;
}

export interface SystemSettings {
  morningPushEnabled: boolean;
  morningPushTime: string;
  eveningReminderEnabled: boolean;
  eveningReminderTime: string;
  dailyDeadlineTime: string;
  strictDeadlineEnabled: boolean;
  saturdayPollEnabled: boolean;
  saturdayPollTime: string;
  sundayRestEnabled: boolean;
  aiWhisperEnabled: boolean;
  aiSummaryEnabled: boolean;
  antiFraudDedupEnabled: boolean;
  zoomAutoDetectEnabled: boolean;
}

export type TabType = 'inbox' | 'students' | 'analytics' | 'promotion' | 'settings' | 'tasks';

export interface CheatsheetItem {
  id: string;
  title: string;
  category: 'reels' | 'direct' | 'profile' | 'rules';
  description: string;
  content: string;
}

export const BEAUTY_NICHES = [
  'Бьюти-мастер (Маникюр / Брови)',
  'Стилист-имиджмейкер',
  'Визажист-стилист',
  'Косметолог-эстетист',
  'Наставник бьюти-сферы',
] as const;

export type BeautyNiche = (typeof BEAUTY_NICHES)[number];
