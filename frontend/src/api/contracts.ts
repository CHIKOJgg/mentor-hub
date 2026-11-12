import type { DayInfo } from '../types';

export interface ApiContentDraft {
  id: string;
  type: string;
  label: string;
  isDuplicate: boolean;
  duplicateDate?: string;
  url?: string;
}

export interface ApiReel {
  id: string;
  url: string;
  shortcode: string;
  isValid: boolean;
  isDuplicate: boolean;
  duplicateDate?: string;
}

export interface ApiDailyReport {
  id: string;
  studentId: string;
  studentName: string;
  avatarText: string;
  tgUsername: string;
  tariff: string;
  submittedAt: string;
  reportDate: string;
  isOnTime: boolean;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  mentorReply?: string;
  contentDrafts: ApiContentDraft[];
  reels: ApiReel[];
  salesAmount: number | string;
  mailingsCount: number;
  newClientsCount: number;
  mentorQuestion?: string;
  voiceTranscript?: string;
  reflectionSummary?: { emotions: string; actions: string; questionToMentor?: string };
  aiSummary: string;
}

export interface ApiStudent {
  id: string;
  fullName: string;
  avatarText: string;
  tgUsername?: string;
  tgChatId?: number;
  niche: string;
  tariff?: string;
  startDate?: string;
  initialIncome: number | string;
  currentIncome: number | string;
  initialAvgTicket: number | string;
  currentAvgTicket: number | string;
  totalReels: number;
  totalCarousels: number;
  totalStorytells: number;
  totalMailings: number;
  totalNewClients: number;
  disciplineRate: number;
  isOnPause: boolean;
  pauseReason?: string;
  nextZoomDate?: string;
  completedZoomsCount: number;
  contractZoomQuota?: number;
  weekDays: DayInfo[];
  zoomSessions?: ApiZoomSession[];
}

export interface ApiZoomSession {
  id: number;
  studentId: number;
  studentName: string;
  title: string;
  callNumber: number;
  zoomUrl?: string;
  recordingUrl?: string;
  scheduledAt?: string;
  linkSentAt?: string;
  evidenceUrl?: string;
  sourceChatId?: number;
  sourceMessageId?: number;
  completedAt?: string;
  isCompleted: boolean;
  meetingNotes?: string;
}

export interface ApiTaskRule {
  id: string;
  hashtag: string;
  title: string;
  description: string;
  required: boolean;
  example: string;
  isActive?: boolean;
}

export interface ApiSystemSettings {
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

export interface ApiCuratorScript {
  id?: string;
  title: string;
  description: string;
  text: string;
  category?: string;
}

export interface ApiAdminSession {
  adminName: string;
  role: string;
  authenticated: boolean;
}
