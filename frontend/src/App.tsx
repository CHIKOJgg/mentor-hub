import { useState, useRef } from 'react';
import { initialReports, studentDisciplines, taskRules, defaultSystemSettings } from './mockData';
import type { DailyReport, StudentDiscipline, TabType, ContentType, TaskRule, SystemSettings, DayInfo } from './types';
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
  PartyPopper,
  RotateCcw,
  Sparkles,
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
  Zap,
  Pencil,
  Plus,
  Trash2,
  X,
  Save,
  Settings,
  Calendar,
} from 'lucide-react';

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

export default function App() {
  const [activeTab, setActiveTab] = useState<'inbox' | 'students' | 'analytics' | 'tasks'>('inbox');
  const [reports, setReports] = useState<DailyReport[]>(initialReports);
  const [students, setStudents] = useState<StudentDiscipline[]>(studentDisciplines);
  const [replies, setReplies] = useState<Record<string, string>>({});
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [selectedStudent, setSelectedStudent] = useState<StudentDiscipline>(studentDisciplines[0]);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeAdmin, setActiveAdmin] = useState<'Ксения' | 'Катя'>('Ксения');
  const [analyticsCategory, setAnalyticsCategory] = useState<'cohort' | 'students'>('cohort');
  const [analyticsPeriod, setAnalyticsPeriod] = useState<'week' | 'month' | 'all'>('all');
  const [rules, setRules] = useState<TaskRule[]>(taskRules);
  const [systemSettings, setSystemSettings] = useState<SystemSettings>(defaultSystemSettings);
  const [tasksSubTab, setTasksSubTab] = useState<'hashtags' | 'scripts' | 'system'>('hashtags');
  const [studentsFilter, setStudentsFilter] = useState<'all' | 'perfect' | 'attention' | 'pause' | 'zooms'>('all');
  const [studentsViewMode, setStudentsViewMode] = useState<'calendar' | 'zooms'>('calendar');
  const [rehabOffered, setRehabOffered] = useState<boolean>(false);
  const [selectedScriptCategory, setSelectedScriptCategory] = useState<string>('Все');
  const [curatorScripts, setCuratorScripts] = useState<{ title: string; description: string; text: string; category?: string }[]>([
    {
      title: 'Скрипт Direct: Первое касание (шаблон для учениц)',
      description: 'Холодное касание экспертам и блогерам без давления и навязывания',
      category: 'Продажи',
      text: '«Привет, [Имя]! Увидела твой сторителл про запуск — очень откликается глубина. Заметила, что многие сильные эксперты на масштабе ловят выгорание и синдром самозванца. Я клинический психолог, помогаю экспертам пробить плато дохода без надрыва. Хочу подарить 15-минутный экспресс-разбор: где сейчас утекает энергия. Актуально на этой неделе?»',
    },
    {
      title: 'Офер пакета консультаций за 15 000 ₽ (шаблон)',
      description: 'Формула закрытия после бесплатного экспресс-разбора на высокий чек',
      category: 'Продажи',
      text: '«Мы вскрыли корень: страх отказа и обесценивание своего опыта. Чтобы убрать это системно за 3 недели и зафиксировать чек, предлагаю пакет из 4 сессий с моим личным сопровождением в Telegram между встречами. Стоимость — 15 000 ₽. Стартуем в четверг?»',
    },
    {
      title: 'Напоминание о дедлайне в личку (от куратора Кати)',
      description: 'Мягкий пуш ученицам за 1 час до дедлайна с фокусом на результат',
      category: 'Кураторские',
      text: '«Привет! Напоминаю, что сегодня ждём твой черновик контента и количество касаний до 21:00. Ксения сейчас на связи и разбирает отчёты в первую очередь. Если есть затык — отправляй голосовое с #вопрос, разберём!»',
    },
    {
      title: 'Поддержка при спаде энергии и амнистия (от наставника Ксении)',
      description: 'Личное сообщение ученице в риске слёта для возврата в стрик',
      category: 'Удержание',
      text: '«Привет, дорогая! Вижу, что накопилась усталость от совмещения. Давай снизим темп: на сегодня план-минимум — всего 15 рассылок и короткий Reels без сложного монтажа. Твоя Точка Б ближе, чем кажется, давай не сливать стрик!»',
    },
  ]);

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

  const saveDayEdit = (studentId: string, dayIndex: number, updatedDay: DayInfo) => {
    setStudents(prev =>
      prev.map(s => {
        if (s.id === studentId) {
          const nextWeekDays = [...s.weekDays];
          nextWeekDays[dayIndex] = updatedDay;
          const activeDays = nextWeekDays.filter(d => d.status !== 'WEEKEND' && d.status !== 'PAUSED');
          const completedCount = activeDays.filter(d => d.status === 'COMPLETED_ON_TIME' || d.status === 'COMPLETED_LATE').length;
          const newRate = activeDays.length > 0 ? Math.round((completedCount / activeDays.length) * 100) : 100;
          return {
            ...s,
            weekDays: nextWeekDays,
            disciplineRate: newRate,
          };
        }
        return s;
      })
    );
    showToast(`Статус за ${updatedDay.date} обновлен`);
  };

  const saveStudentProfile = (updated: StudentDiscipline) => {
    setStudents(prev => prev.map(s => (s.id === updated.id ? updated : s)));
    if (selectedStudent.id === updated.id) {
      setSelectedStudent(updated);
    }
    showToast(`Профиль ${updated.fullName} успешно сохранен`);
  };

  const saveScript = (index: number, scriptData: { title: string; description: string; text: string }) => {
    setCuratorScripts(prev => {
      const next = [...prev];
      next[index] = { ...next[index], ...scriptData };
      return next;
    });
    showToast(`Скрипт «${scriptData.title}» сохранён`);
  };

  const addScript = (scriptData: { title: string; description: string; text: string }) => {
    setCuratorScripts(prev => [...prev, scriptData]);
    showToast(`Добавлен новый скрипт: ${scriptData.title}`);
  };

  const deleteScript = (index: number) => {
    setCuratorScripts(prev => prev.filter((_, i) => i !== index));
    showToast('Скрипт удален');
  };

  const saveRule = (updatedRule: TaskRule) => {
    setRules(prev => prev.map(r => (r.id === updatedRule.id ? updatedRule : r)));
    showToast(`Правило ${updatedRule.hashtag} сохранено`);
  };

  const addRule = (ruleData: { hashtag: string; title: string; description: string; required: boolean; example: string }) => {
    setRules(prev => {
      const id = `rule-${prev.length + 1}`;
      return [...prev, { ...ruleData, id, enabled: true }];
    });
    showToast(`Хэштег ${ruleData.hashtag} добавлен в систему`);
  };

  const deleteRule = (ruleId: string, tag: string) => {
    setRules(prev => prev.filter(r => r.id !== ruleId));
    showToast(`Хэштег ${tag} удален`);
  };

  const updateScheduleTime = (key: keyof SystemSettings, val: string, label: string) => {
    setSystemSettings(prev => ({ ...prev, [key]: val }));
    showToast(`${label} изменено на ${val}`);
  };

  const toggleRuleEnabled = (ruleId: string) => {
    setRules(prev =>
      prev.map(r => {
        if (r.id === ruleId) {
          const next = r.enabled !== false ? false : true;
          showToast(next ? `Хэштег ${r.hashtag} включен в боте` : `Хэштег ${r.hashtag} отключен`);
          return { ...r, enabled: next };
        }
        return r;
      })
    );
  };

  const toggleRuleRequired = (ruleId: string) => {
    setRules(prev =>
      prev.map(r => {
        if (r.id === ruleId) {
          const next = !r.required;
          showToast(`Статус ${r.hashtag} изменен на: ${next ? 'обязательно' : 'по факту'}`);
          return { ...r, required: next };
        }
        return r;
      })
    );
  };

  const toggleSystemSetting = (key: keyof SystemSettings, label: string) => {
    setSystemSettings(prev => {
      const nextVal = !prev[key];
      showToast(`${label}: ${nextVal ? 'включено' : 'отключено'}`);
      return { ...prev, [key]: nextVal };
    });
  };

  const markStudentZoomCompleted = (studentId: string, studentName: string) => {
    setStudents(prev =>
      prev.map(s => {
        if (s.id === studentId) {
          const nextCount = Math.min(8, s.completedZoomsCount + 1);
          showToast(`Созвон ${nextCount}/8 с ${studentName} зачтен`);
          return { ...s, completedZoomsCount: nextCount };
        }
        return s;
      })
    );
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

  const handleApprove = (reportId: string, name: string) => {
    setReports(prev => prev.filter(r => r.id !== reportId));
    const reply = replies[reportId];
    showToast(reply?.trim() ? `Ответ отправлен ${name}` : `Отчёт ${name} принят и одобрен к публикации`);
    setReplies(prev => {
      const next = { ...prev };
      delete next[reportId];
      return next;
    });
  };

  const toggleStudentPause = (studentId: string) => {
    setStudents(prev =>
      prev.map(s => {
        if (s.id === studentId) {
          const nextState = !s.isOnPause;
          showToast(
            nextState
              ? `${s.fullName} поставлена на паузу (стрик сохранен)`
              : `${s.fullName} возвращена в активный трек`
          );
          return {
            ...s,
            isOnPause: nextState,
            pauseReason: nextState ? 'На паузе (болезнь/отпуск)' : undefined,
          };
        }
        return s;
      })
    );
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
    if (studentsFilter === 'zooms') return Boolean(s.nextZoomDate);
    return true;
  });

  const tabs: { id: TabType; label: string; icon: typeof Inbox; count?: number }[] = [
    { id: 'inbox', label: 'На проверку', icon: Inbox, count: reports.length },
    { id: 'students', label: 'Ученицы', icon: Users },
    { id: 'analytics', label: 'Аналитика', icon: BarChart3 },
    { id: 'tasks', label: 'Хэштеги', icon: Hash },
  ];

  // Aggregate metrics
  const totalReelsCount = students.reduce((sum, s) => sum + s.totalReels, 0);
  const totalCarouselsCount = students.reduce((sum, s) => sum + s.totalCarousels, 0);
  const totalStorytellsCount = students.reduce((sum, s) => sum + s.totalStorytells, 0);
  const totalNewClientsCount = students.reduce((sum, s) => sum + s.totalNewClients, 0);
  const totalMailingsCount = students.reduce((sum, s) => sum + s.totalMailings, 0);

  return (
    <div className="min-h-screen bg-[#fcfdfd] text-slate-800" style={{ fontFamily: "'Inter', system-ui, -apple-system, sans-serif" }}>

      {/* ─── Sidebar + Main Layout ─── */}
      <div className="flex">

        {/* Sidebar */}
        <aside className="hidden md:flex flex-col w-64 min-h-screen bg-white border-r border-slate-200/60 px-4 py-8 fixed left-0 top-0 z-20">
          <div className="px-3 mb-6">
            <div className="text-xs font-medium tracking-wider text-slate-400 uppercase font-mono mb-1">Mentor Hub</div>
            <h1 className="text-xl font-bold text-slate-900 tracking-tight">Панель наставника</h1>
            <p className="text-xs text-slate-500 mt-0.5">Рабочий трекер куратора</p>
          </div>

          {/* Active Telegram Bot Indicator */}
          <div className="px-3 mb-5">
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                </span>
                <div>
                  <div className="text-xs font-bold text-slate-900">Telegram-бот</div>
                  <div className="text-xs text-slate-400 font-mono">@nastav_beauty_bot</div>
                </div>
              </div>
              <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-md">
                онлайн
              </span>
            </div>
          </div>

          <nav className="flex-1 space-y-1">
            {tabs.map(t => (
              <button
                key={t.id}
                onClick={() => setActiveTab(t.id)}
                className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm transition-colors cursor-pointer ${
                  activeTab === t.id
                    ? 'bg-neutral-900 text-white font-medium'
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

          {/* Quick Curator Tools for Ksenia & Katya */}
          <div className="mt-5 pt-4 border-t border-slate-100 px-1 space-y-1">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400 px-2.5 mb-2">
              Инструменты куратора:
            </div>
            <button
              onClick={() => {
                setActiveTab('students');
                setStudentsViewMode('zooms');
                showToast('Открыт трекер Zoom-созвонов (8 этапов)');
              }}
              className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-50 transition cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <Video className="w-4 h-4 text-indigo-500" />
                <span>Zoom-созвоны (8 этапов)</span>
              </div>
              <span className="text-xs font-mono font-bold text-indigo-600 bg-indigo-50 px-1.5 py-0.5 rounded">8</span>
            </button>
            <button
              onClick={() => {
                setActiveTab('students');
                setStudentsFilter('attention');
                setStudentsViewMode('calendar');
                showToast('Открыт Радар удержания: ученицы в зоне риска');
              }}
              className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-50 transition cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <AlertTriangle className="w-4 h-4 text-rose-500" />
                <span>Радар удержания (риски)</span>
              </div>
              <span className="text-xs font-mono font-bold text-rose-600 bg-rose-50 px-1.5 py-0.5 rounded">1</span>
            </button>
            <button
              onClick={() => {
                setActiveTab('tasks');
                setTasksSubTab('scripts');
                showToast('Открыта база шпаргалок и скриптов куратора');
              }}
              className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-50 transition cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <MessageSquare className="w-4 h-4 text-emerald-500" />
                <span>Шпаргалки и скрипты</span>
              </div>
              <span className="text-xs font-mono font-bold text-slate-600 bg-slate-100 px-1.5 py-0.5 rounded">{curatorScripts.length}</span>
            </button>
          </div>

          {/* Admin Switcher & Session */}
          <div className="px-3 pt-5 border-t border-slate-100 mt-4 space-y-3.5">
            <div className="flex items-center justify-between text-xs text-slate-400 font-medium uppercase tracking-wider">
              <span>Доступ админа:</span>
              <span className="text-emerald-600 font-medium normal-case flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>онлайн
              </span>
            </div>

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

            <div className="flex items-center gap-3 pt-1">
              <div className="w-9 h-9 rounded-full bg-slate-900 text-white flex items-center justify-center text-xs font-bold">
                {activeAdmin[0]}
              </div>
              <div>
                <div className="text-sm font-semibold text-slate-900">{activeAdmin}</div>
                <div className="text-xs text-slate-500">
                  {activeAdmin === 'Ксения' ? 'Главный наставник' : 'Куратор потока'}
                </div>
              </div>
            </div>
          </div>
        </aside>

        {/* Mobile top bar */}
        <div className="md:hidden fixed top-0 left-0 right-0 z-20 bg-white border-b border-slate-200/60 px-4 py-3.5">
          <div className="flex items-center justify-between mb-2.5">
            <div>
              <div className="text-xs font-medium tracking-wider text-slate-400 uppercase font-mono">Mentor Hub</div>
              <h1 className="text-base font-bold text-slate-900 tracking-tight">Панель наставника</h1>
              <p className="text-xs text-slate-500">{activeAdmin} · Поток Ноябрь</p>
            </div>
            <button
              onClick={() => showToast('Синхронизировано')}
              className="text-slate-500 hover:text-slate-900 transition cursor-pointer p-1.5"
            >
              <RefreshCw className="w-4 h-4" strokeWidth={1.8} />
            </button>
          </div>
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
          <div className="flex gap-2 overflow-x-auto pb-1">
            {tabs.map(t => (
              <button
                key={t.id}
                onClick={() => setActiveTab(t.id)}
                className={`px-3.5 py-2 rounded-lg text-xs font-semibold transition shrink-0 cursor-pointer ${
                  activeTab === t.id
                    ? 'bg-neutral-900 text-white'
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
        <main className="flex-1 md:ml-64 pt-28 md:pt-0">
          <div className="max-w-5xl mx-auto px-6 sm:px-10 py-10">

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
                  <div className="hidden sm:flex items-center gap-2 text-xs font-medium text-slate-600 bg-white border border-slate-200/60 px-3.5 py-2 rounded-xl">
                    <Sparkles className="w-3.5 h-3.5 text-indigo-600" />
                    <span>AI-выжимка рефлексий активна</span>
                  </div>
                </div>

                {reports.length === 0 ? (
                  <div className="text-center py-20 bg-white rounded-2xl border border-slate-200/60 p-8">
                    <PartyPopper className="w-10 h-10 text-slate-400 mx-auto mb-3" strokeWidth={1.5} />
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
                                  <Sparkles className="w-4 h-4 text-indigo-600" strokeWidth={1.8} />
                                  <span>Выжимка рефлексии (AI)</span>
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
                              onClick={() => handleApprove(r.id, r.studentName)}
                              className="px-5 py-2.5 rounded-xl bg-slate-900 text-white text-sm font-semibold hover:bg-slate-800 transition flex items-center gap-2 cursor-pointer shrink-0"
                            >
                              <CheckCheck className="w-4 h-4" strokeWidth={1.8} />
                              <span>Одобрить</span>
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
                    <h2 className="text-2xl font-bold tracking-tight text-slate-900">Ученицы & Календарь</h2>
                    <p className="text-sm sm:text-base text-slate-500 mt-1">Календарь дисциплины, трекер Zoom-созвонов и статус прохождения</p>
                  </div>
                  <div className="relative">
                    <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" strokeWidth={1.8} />
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={e => setSearchQuery(e.target.value)}
                      placeholder="Поиск по имени или нише..."
                      className="text-sm pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:outline-none focus:border-indigo-500 transition w-full sm:w-64 placeholder:text-slate-400"
                    />
                  </div>
                </div>

                {/* Sub-tabs & Filter Bar for Quick Student Checks */}
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
                  {/* Category Filter Pills */}
                  <div className="flex flex-wrap items-center gap-2">
                    {[
                      { id: 'all', label: `Все ученицы (${students.length})` },
                      { id: 'perfect', label: '100% дисциплина (зелено)' },
                      { id: 'attention', label: 'Требуют внимания / риск' },
                      { id: 'pause', label: 'На паузе (болезнь)' },
                      { id: 'zooms', label: `Zoom-созвоны (${students.length})` },
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
                      <span>Созвоны (8 этапов)</span>
                    </button>
                  </div>
                </div>

                {/* ─── RETENTION RADAR 360° (ДЛЯ КСЕНИИ: РАДАР УДЕРЖАНИЯ) ─── */}
                {(studentsFilter === 'all' || studentsFilter === 'attention') && (
                  <div className="bg-white rounded-2xl border border-rose-200/80 p-5 sm:p-6 mb-6">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-rose-100">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center font-bold text-sm shrink-0">
                          <AlertTriangle className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-bold text-slate-900 uppercase tracking-wider">Радар удержания (Retention Radar 360°)</span>
                            <span className="text-xs font-semibold px-2 py-0.5 rounded-md bg-rose-50 text-rose-700 border border-rose-200">
                              Риск слёта: 78% (Высокий)
                            </span>
                          </div>
                          <div className="text-xs text-slate-500 mt-0.5">
                            Ученица: <strong>Аня Петрова</strong> · Ниша: Психолог для экспертов · Сигналы: 2 пропуска, сдача глубокой ночью (23:15)
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 self-start sm:self-auto shrink-0">
                        <button
                          onClick={() => {
                            setRehabOffered(true);
                            showToast('Офер амнистии стрика отправлен Ане в Telegram: 1 Reels + 15 рассылок вернут дисциплину до 92%');
                          }}
                          disabled={rehabOffered}
                          className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold transition cursor-pointer flex items-center gap-1.5 ${
                            rehabOffered
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                              : 'bg-indigo-600 hover:bg-indigo-700 text-white'
                          }`}
                        >
                          <Zap className="w-3.5 h-3.5" />
                          <span>{rehabOffered ? 'Амнистия предложена' : 'Предложить амнистию стрика'}</span>
                        </button>
                        <button
                          onClick={() => {
                            navigator.clipboard?.writeText('@anya_psy_coach');
                            showToast('Юзернейм @anya_psy_coach скопирован для диалога в TG');
                          }}
                          className="px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-slate-900 text-white hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
                        >
                          <Send className="w-3.5 h-3.5 text-indigo-400" />
                          <span>Написать в TG</span>
                        </button>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mt-4">
                      {/* Left: AI Diagnosis */}
                      <div className="p-4 rounded-xl bg-slate-50 border border-slate-100">
                        <div className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                          <Sparkles className="w-3.5 h-3.5 text-indigo-600" />
                          <span>AI-диагноз психологического барьера (Whisper)</span>
                        </div>
                        <p className="text-xs sm:text-sm text-slate-700 leading-relaxed font-medium">
                          Аня на грани выгорания из-за попытки совместить полный рабочий день и 25 рассылок. Синдром самозванца при чеке 15 000 ₽. Сдаёт отчёты глубокой ночью (23:15). Высокий риск схода.
                        </p>
                      </div>

                      {/* Right: Mentor Action Items */}
                      <div className="p-4 rounded-xl bg-slate-50 border border-slate-100">
                        <div className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                          <Clock className="w-3.5 h-3.5 text-indigo-600" />
                          <span>План действий Ксении к созвону 12.11 (19:00)</span>
                        </div>
                        <ul className="text-xs text-slate-700 space-y-1">
                          <li className="flex items-start gap-1.5">
                            <Check className="w-3.5 h-3.5 text-indigo-600 shrink-0 mt-0.5" />
                            <span>Сократить дневную норму рассылок с 25 до 15 целевых контактов</span>
                          </li>
                          <li className="flex items-start gap-1.5">
                            <Check className="w-3.5 h-3.5 text-indigo-600 shrink-0 mt-0.5" />
                            <span>Дать готовый конструктор сценариев Reels на 20 минут съемки</span>
                          </li>
                          <li className="flex items-start gap-1.5">
                            <Check className="w-3.5 h-3.5 text-indigo-600 shrink-0 mt-0.5" />
                            <span>Отработать на созвоне 12.11 страх отказа: продажа за 15 000 ₽ уже состоялась!</span>
                          </li>
                          <li className="flex items-start gap-1.5">
                            <Check className="w-3.5 h-3.5 text-indigo-600 shrink-0 mt-0.5" />
                            <span>Предложить субботнюю амнистию стрика (Reels + 15 рассылок)</span>
                          </li>
                        </ul>
                      </div>
                    </div>
                  </div>
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
                                      {s.nextZoomDate && (
                                        <div className="mt-2 pt-2 border-t border-slate-100">
                                          <div className="flex items-center justify-between text-xs text-slate-600 mb-1.5 gap-2">
                                            <span className="inline-flex items-center gap-1.5">
                                              <Video className="w-3.5 h-3.5 text-indigo-500" />
                                              <span>Созвон: <strong>{s.nextZoomDate}</strong></span>
                                            </span>
                                            <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200 shrink-0">
                                              {s.completedZoomsCount} проведено из 8
                                            </span>
                                          </div>

                                          {/* 8 Call slots - All green when done */}
                                          <div className="flex items-center gap-1 flex-wrap">
                                            {[1, 2, 3, 4, 5, 6, 7, 8].map(num => {
                                              const isDone = num <= s.completedZoomsCount;
                                              const isNext = num === s.completedZoomsCount + 1;
                                              return (
                                                <span
                                                  key={num}
                                                  title={`Созвон ${num}: ${isDone ? 'Проведён (зелено)' : isNext ? s.nextZoomDate || 'Запланирован' : 'В программе'}`}
                                                  className={`inline-flex items-center justify-center text-xs font-bold w-5 h-5 rounded-md border ${
                                                    isDone
                                                      ? 'bg-emerald-50 text-emerald-700 border-emerald-300'
                                                      : isNext
                                                      ? 'bg-indigo-50 text-indigo-700 border-indigo-300'
                                                      : 'bg-slate-100 text-slate-400 border-slate-200'
                                                  }`}
                                                >
                                                  {isDone ? <Check className="w-2.5 h-2.5 text-emerald-700 stroke-[3]" /> : num}
                                                </span>
                                              );
                                            })}
                                            <button
                                              onClick={() => markStudentZoomCompleted(s.id, s.fullName)}
                                              title="Отметить следующий созвон проведенным"
                                              className="ml-1 text-xs font-semibold text-emerald-700 hover:text-emerald-800 bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 px-2 py-0.5 rounded-md transition cursor-pointer"
                                            >
                                              + Зачесть
                                            </button>
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

                {/* VIEW 2: DEDICATED ZOOM CALLS TRACKER BOARD (8 STAGES) */}
                {studentsViewMode === 'zooms' && (
                  <div className="space-y-6">
                    <div className="bg-white rounded-2xl border border-slate-200/60 p-5 flex items-center justify-between">
                      <div>
                        <h3 className="text-lg font-bold text-slate-900">Мастер-трекер Zoom-созвонов потока</h3>
                        <p className="text-xs sm:text-sm text-slate-500 mt-0.5">8 обязательных модульных созвонов для каждой ученицы (проведенные отмечены зелёным)</p>
                      </div>
                      <div className="text-right">
                        <span className="text-2xl font-bold text-emerald-600">
                          {students.reduce((sum, s) => sum + s.completedZoomsCount, 0)} / 40
                        </span>
                        <div className="text-xs text-slate-400">созвонов проведено</div>
                      </div>
                    </div>

                    <div className="space-y-5">
                      {filtered.map(s => {
                        const zoomTopics = [
                          'Стратегия и декомпозиция цели (Точка А -> Точка Б)',
                          'Распаковка позиционирования и премиум-оферты',
                          'Воронка экспертных Reels и хуки первых 3 секунд',
                          'Скрипты квалификации и продаж в Direct без отказов',
                          'Сбор продающих каруселей и упаковка кейсов',
                          'Дожим входящих лидов и отработка возражений по чеку',
                          'Делегирование, ассистент и масштабирование',
                          'Финальная аттестация, презентация кейса и выпуск',
                        ];

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
                                      title="Редактировать созвоны и профиль"
                                      className="p-1 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition cursor-pointer"
                                    >
                                      <Pencil className="w-3.5 h-3.5" />
                                    </button>
                                    <span className="text-xs text-slate-500">({s.niche})</span>
                                  </div>
                                  <div className="text-xs text-slate-500 mt-0.5 flex items-center gap-1.5">
                                    <Calendar className="w-3.5 h-3.5 text-indigo-500" />
                                    <span>Следующий созвон: <strong className="text-indigo-600">{s.nextZoomDate || 'по согласованию'}</strong></span>
                                  </div>
                                </div>
                              </div>

                              <div className="flex items-center gap-3 self-start sm:self-auto">
                                <span className="text-xs font-semibold px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                                  {s.completedZoomsCount}/8 проведено ({Math.round((s.completedZoomsCount / 8) * 100)}%)
                                </span>
                                <button
                                  onClick={() => markStudentZoomCompleted(s.id, s.fullName)}
                                  className="text-xs font-semibold px-3.5 py-1.5 rounded-xl bg-slate-900 text-white hover:bg-slate-800 transition cursor-pointer flex items-center gap-1.5"
                                >
                                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                                  <span>+ Зачесть созвон</span>
                                </button>
                              </div>
                            </div>

                            {/* 8 Zoom Call Modules Grid */}
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                              {zoomTopics.map((topic, idx) => {
                                const callNum = idx + 1;
                                const isDone = callNum <= s.completedZoomsCount;
                                const isNext = callNum === s.completedZoomsCount + 1;

                                return (
                                  <div
                                    key={callNum}
                                    className={`p-3.5 rounded-xl border transition ${
                                      isDone
                                        ? 'bg-emerald-50/60 border-emerald-200 text-slate-800'
                                        : isNext
                                        ? 'bg-indigo-50/50 border-indigo-200 text-slate-800'
                                        : 'bg-slate-50 border-slate-200/60 text-slate-400'
                                    }`}
                                  >
                                    <div className="flex items-center justify-between mb-1.5">
                                      <span className="text-xs font-bold uppercase tracking-wider">
                                        Созвон #{callNum}
                                      </span>
                                      {isDone ? (
                                        <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded-md">
                                          <Check className="w-3 h-3" />
                                          <span>Проведён</span>
                                        </span>
                                      ) : isNext ? (
                                        <span className="text-xs font-bold text-indigo-700 bg-indigo-100 px-2 py-0.5 rounded-md">
                                          Следующий
                                        </span>
                                      ) : (
                                        <span className="text-xs font-medium text-slate-400">
                                          В плане
                                        </span>
                                      )}
                                    </div>
                                    <div className="text-xs font-medium leading-snug line-clamp-2 text-slate-700">
                                      {topic}
                                    </div>
                                    {isNext && s.nextZoomDate && (
                                      <div className="mt-2 text-xs text-indigo-600 font-semibold flex items-center gap-1">
                                        <Clock className="w-3 h-3" />
                                        <span>{s.nextZoomDate}</span>
                                      </div>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
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
                    <span>По ученицам ({studentDisciplines.length})</span>
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
                          {analyticsPeriod === 'week' ? '223 000 ₽' : analyticsPeriod === 'month' ? '742 000 ₽' : '895 000 ₽'}
                        </div>
                        <div className="text-xs text-slate-500 mt-1">
                          {analyticsPeriod === 'week' ? '+223 000 ₽ за 7 дней' : analyticsPeriod === 'month' ? '+310% за 30 дней' : '+490% с момента старта'}
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
                        <div className="text-3xl font-extrabold text-slate-900 mt-2 tracking-tight">40 600 ₽</div>
                        <div className="text-xs text-slate-500 mt-1">вырос с 4 800 ₽ на старте</div>
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
                          {analyticsPeriod === 'week' ? '11' : analyticsPeriod === 'month' ? '33' : totalCarouselsCount}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Smartphone className="w-4 h-4 text-slate-400" strokeWidth={1.8} />
                        <span className="text-slate-500">Сторителлы:</span>
                        <span className="font-bold text-slate-900">
                          {analyticsPeriod === 'week' ? '13' : analyticsPeriod === 'month' ? '40' : totalStorytellsCount}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Users className="w-4 h-4 text-slate-400" strokeWidth={1.8} />
                        <span className="text-slate-500">Новые клиенты:</span>
                        <span className="font-bold text-indigo-600">
                          {analyticsPeriod === 'week' ? '+9' : analyticsPeriod === 'month' ? '+28' : `+${totalNewClientsCount}`}
                        </span>
                        <span className="text-xs text-slate-400">(субботний опрос)</span>
                      </div>
                      <div className="text-xs text-slate-500 bg-slate-50 px-3 py-1.5 rounded-lg border border-slate-100">
                        Фильтр периода: <span className="font-semibold text-slate-800">{analyticsPeriod === 'week' ? '7 дней' : analyticsPeriod === 'month' ? '30 дней' : 'Весь период'}</span>
                      </div>
                    </div>

                    {/* Telegram Ingestion Status Notice */}
                    <div className="bg-slate-50 border border-slate-200/60 rounded-2xl p-4.5 flex items-start gap-3.5">
                      <div className="w-8 h-8 rounded-xl bg-indigo-50 flex items-center justify-center shrink-0 mt-0.5">
                        <Sparkles className="w-4 h-4 text-indigo-600" strokeWidth={1.8} />
                      </div>
                      <div className="text-xs sm:text-sm text-slate-600 leading-relaxed">
                        <strong className="font-semibold text-slate-900">Автоматическая агрегация из Telegram:</strong> Все метрики поступают непрерывно из персональных чатов учениц по хэштегам <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#рилс</code>, <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#карусель</code>, <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#сторителл</code>, <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#рассылки</code>, <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#продажи</code> и субботним опросам <code className="font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 text-slate-800">#клиенты</code>. Войсы рефлексии анализируются через Whisper AI.
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
                              const period = s.periodStats?.[analyticsPeriod];
                              const incomeDisplay = analyticsPeriod === 'all'
                                ? `${s.initialIncome.toLocaleString('ru-RU')} → ${s.currentIncome.toLocaleString('ru-RU')} ₽`
                                : `${(period?.income ?? s.currentIncome).toLocaleString('ru-RU')} ₽`;
                              const reelsDisp = period?.reels ?? s.totalReels;
                              const carouselsDisp = period?.carousels ?? s.totalCarousels;
                              const storytellsDisp = period?.storytells ?? s.totalStorytells;
                              const mailingsDisp = period?.mailings ?? s.totalMailings;
                              const clientsDisp = period?.newClients ?? s.totalNewClients;
                              const discDisp = period?.disciplineRate ?? s.disciplineRate;

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
                        {studentDisciplines.map(s => (
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
                          <div className="text-xs sm:text-sm text-slate-500 mt-1">{selectedStudent.niche} · Telegram данные обновлены сегодня</div>
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
                              : `${(selectedStudent.periodStats?.[analyticsPeriod]?.income ?? selectedStudent.currentIncome).toLocaleString('ru-RU')} ₽`
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
                              {selectedStudent.periodStats?.[analyticsPeriod]?.reels ?? selectedStudent.totalReels}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalReels} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <Layers className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Карусели</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudent.periodStats?.[analyticsPeriod]?.carousels ?? selectedStudent.totalCarousels}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalCarousels} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <Smartphone className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Сторителлы</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudent.periodStats?.[analyticsPeriod]?.storytells ?? selectedStudent.totalStorytells}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalStorytells} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <MessageSquare className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Рассылки</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudent.periodStats?.[analyticsPeriod]?.mailings ?? selectedStudent.totalMailings}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalMailings} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <Users className="w-3.5 h-3.5 text-indigo-500" />
                              <span>Новые клиенты</span>
                            </div>
                            <div className="text-lg font-bold text-indigo-600">
                              +{selectedStudent.periodStats?.[analyticsPeriod]?.newClients ?? selectedStudent.totalNewClients}
                            </div>
                            <div className="text-xs text-slate-400">из {selectedStudent.totalNewClients} за поток</div>
                          </div>

                          <div className="bg-white p-3.5 rounded-xl border border-slate-200/60">
                            <div className="text-xs text-slate-400 flex items-center gap-1 mb-1">
                              <CheckCheck className="w-3.5 h-3.5 text-emerald-500" />
                              <span>Дисциплина</span>
                            </div>
                            <div className="text-lg font-bold text-slate-900">
                              {selectedStudent.periodStats?.[analyticsPeriod]?.disciplineRate ?? selectedStudent.disciplineRate}%
                            </div>
                            <div className="text-xs text-slate-400">соблюдение дедлайна</div>
                          </div>
                        </div>
                      </div>

                      {/* AI Voice Reflection & Sentiment Card */}
                      <div className="px-6 py-5 border-b border-slate-100 space-y-3">
                        <div className="flex items-center gap-2">
                          <Sparkles className="w-4 h-4 text-indigo-600" />
                          <span className="text-xs font-semibold text-slate-900 uppercase tracking-wider">
                            AI-выжимка рефлексии из Telegram-войсов ({analyticsPeriod === 'week' ? 'за неделю' : analyticsPeriod === 'month' ? 'за месяц' : 'за весь период'})
                          </span>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                          <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-100">
                            <div className="text-xs font-medium text-slate-400 mb-1">Эмоциональный трек & затыки</div>
                            <div className="text-slate-800 leading-relaxed font-medium">
                              {selectedStudent.periodStats?.[analyticsPeriod]?.reflectionSentiment ?? 'Стабильное состояние, регулярное выполнение намеченных задач без сбоев'}
                            </div>
                          </div>
                          <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-100">
                            <div className="text-xs font-medium text-slate-400 mb-1">Ключевая победа периода</div>
                            <div className="text-slate-800 leading-relaxed font-medium">
                              {selectedStudent.periodStats?.[analyticsPeriod]?.keyWin ?? 'Уверенный рост показателей и внедрение системных рассылок'}
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Retention Radar 360 Dossier for Anya Petrova */}
                      {selectedStudent.id === 'st-2' && (
                        <div className="px-6 py-5 border-b border-slate-100 bg-rose-50/40">
                          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-3">
                            <div className="flex items-center gap-2">
                              <AlertTriangle className="w-4 h-4 text-rose-600" />
                              <span className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                                Досье удержания 360° & Риск слёта: 78% (Высокий)
                              </span>
                            </div>
                            <div className="flex items-center gap-2">
                              <button
                                onClick={() => {
                                  setRehabOffered(true);
                                  showToast('Офер амнистии стрика отправлен Ане в Telegram: 1 Reels + 15 рассылок вернут дисциплину до 92%');
                                }}
                                disabled={rehabOffered}
                                className={`px-3 py-1 rounded-lg text-xs font-semibold transition cursor-pointer flex items-center gap-1.5 ${
                                  rehabOffered
                                    ? 'bg-emerald-100 text-emerald-800'
                                    : 'bg-indigo-600 hover:bg-indigo-700 text-white'
                                }`}
                              >
                                <Zap className="w-3 h-3" />
                                <span>{rehabOffered ? 'Амнистия предложена' : 'Предложить амнистию стрика'}</span>
                              </button>
                              <button
                                onClick={() => {
                                  navigator.clipboard?.writeText(selectedStudent.telegram || '');
                                  showToast(`Юзернейм ${selectedStudent.telegram} скопирован для связи в TG`);
                                }}
                                className="px-3 py-1 rounded-lg text-xs font-semibold bg-slate-900 text-white hover:bg-slate-800 transition cursor-pointer flex items-center gap-1"
                              >
                                <Send className="w-3 h-3 text-indigo-400" />
                                <span>Написать в TG</span>
                              </button>
                            </div>
                          </div>

                          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs sm:text-sm">
                            <div className="p-3.5 bg-white rounded-xl border border-rose-100 text-slate-700">
                              <div className="text-xs font-semibold text-slate-400 mb-1">AI-диагноз психологического барьера:</div>
                              <p className="leading-relaxed">
                                {selectedStudent.retentionDossier?.aiDiagnosis || 'Аня на грани выгорания из-за попытки совместить полный рабочий день и 25 рассылок. Синдром самозванца при чеке 15 000 ₽. Сдаёт отчёты глубокой ночью (23:15). Высокий риск схода.'}
                              </p>
                            </div>
                            <div className="p-3.5 bg-white rounded-xl border border-rose-100 text-slate-700">
                              <div className="text-xs font-semibold text-slate-400 mb-1">Фокус Ксении на созвон 12.11 (19:00):</div>
                              <ul className="space-y-1 text-xs">
                                <li>• Снизить норму рассылок с 25 до 15 целевых контактов</li>
                                <li>• Шаблоны сценариев Reels на 20 минут съемки</li>
                                <li>• Отработать страх чека 15 000 ₽ (продажа уже состоялась!)</li>
                                <li>• Субботняя амнистия стрика для перекрытия пропусков</li>
                              </ul>
                            </div>
                          </div>
                        </div>
                      )}

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
                      onClick={() => showToast('Регламент синхронизирован с Telegram-ботом')}
                      className="px-3.5 py-2 rounded-xl bg-white border border-slate-200 text-slate-700 text-xs font-semibold hover:bg-slate-50 transition cursor-pointer flex items-center gap-1.5"
                      title="Синхронизировать правила с Telegram-ботом"
                    >
                      <RefreshCw className="w-3.5 h-3.5 text-slate-400" />
                      <span>Синхронизировать</span>
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
                                  onClick={() => deleteRule(rule.id, rule.hashtag)}
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
                                  onClick={() => deleteScript(idx)}
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

                    {/* AI Engine & Anti-Fraud Protection */}
                    <div className="bg-white rounded-2xl border border-slate-200/60 p-6 space-y-4 hover:border-slate-300 transition">
                      <div className="flex items-center gap-2 pb-3 border-b border-slate-100">
                        <ShieldCheck className="w-5 h-5 text-indigo-600" />
                        <div>
                          <h3 className="text-base font-bold text-slate-900">Интеллектуальные функции и защита от накруток</h3>
                          <p className="text-xs text-slate-500">Нейросетевая обработка отчётов учениц и валидация Reels</p>
                        </div>
                      </div>

                      <div className="divide-y divide-slate-100">
                        {/* Setting 6: AI Audio Transcription */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">Транскрибация голосовых рефлексий (Whisper AI)</div>
                            <div className="text-xs text-slate-500">Автоматический перевод аудио-сообщений учениц в структурированный текст для наставника</div>
                          </div>
                          <button
                            onClick={() => toggleSystemSetting('aiWhisperEnabled', 'AI-транскрибация голосовых')}
                            className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                              systemSettings.aiWhisperEnabled ? 'bg-indigo-600' : 'bg-slate-200'
                            }`}
                          >
                            <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white  transition duration-200 ease-in-out ${
                              systemSettings.aiWhisperEnabled ? 'translate-x-4' : 'translate-x-0'
                            }`} />
                          </button>
                        </div>

                        {/* Setting 7: AI Reflection Summary */}
                        <div className="py-3.5 flex items-center justify-between">
                          <div>
                            <div className="text-sm font-semibold text-slate-900">AI-выжимка рефлексий (эмоции, действия, вопрос ментору)</div>
                            <div className="text-xs text-slate-500">Формирует 3 главных тезиса из длинных сообщений учениц в ленту «На проверку»</div>
                          </div>
                          <button
                            onClick={() => toggleSystemSetting('aiSummaryEnabled', 'AI-выжимка рефлексий')}
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
                        prev ? { ...prev, day: { ...prev.day, status: opt.status as any } } : null
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

      {/* ─── MODAL 2: STUDENT PROFILE & FINANCIALS EDITOR ─── */}
      {editingStudent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 border border-slate-200 space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <h3 className="text-base font-bold text-slate-900">Редактирование профиля ученицы</h3>
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

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Следующий Zoom-созвон:</label>
                  <input
                    type="text"
                    value={editingStudent.nextZoomDate || ''}
                    onChange={e => setEditingStudent({ ...editingStudent, nextZoomDate: e.target.value })}
                    placeholder="Например: 12.11 в 19:00"
                    className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-600 block mb-1">Проведено созвонов (из 8):</label>
                  <input
                    type="number"
                    min={0}
                    max={8}
                    value={editingStudent.completedZoomsCount}
                    onChange={e =>
                      setEditingStudent({
                        ...editingStudent,
                        completedZoomsCount: Math.min(8, Math.max(0, Number(e.target.value) || 0)),
                      })
                    }
                    className="w-full text-xs sm:text-sm px-3.5 py-2 rounded-xl border border-slate-200 focus:outline-none focus:border-indigo-500"
                  />
                </div>
              </div>

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
                  saveStudentProfile(editingStudent);
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
                    addScript(newScript);
                    setNewScript({ title: '', description: '', text: '' });
                    setIsAddingScript(false);
                  } else if (editingScript) {
                    saveScript(editingScript.index, {
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
                    addRule(newRule);
                    setNewRule({ hashtag: '#', title: '', description: '', required: false, example: '' });
                    setIsAddingRule(false);
                  } else if (editingRule) {
                    saveRule(editingRule);
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
