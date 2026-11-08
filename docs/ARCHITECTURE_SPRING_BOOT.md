# Mentor Hub / Mentor Tracker — Руководство по архитектуре и реализации Spring Boot (Backend & Bot)

**Проект:** Сервис автоматизации сбора отчётов, контроля дисциплины и аналитики для наставников («Mentor Hub / Mentor Tracker»).  
**Статус документа:** Архитектурное руководство по реализации (Spring Boot 4.x / 3.4.x, Spring AI 2.x / 1.x, TelegramBots 10.3, PostgreSQL 16).  
**Авторитетная полная спецификация:** см. [`docs/ARCHITECTURE_SPECIFICATION.md`](./ARCHITECTURE_SPECIFICATION.md).

---

## 1. Концепция, бизнес-контекст и архитектурный контур

Наставник Ксения ежедневно контролирует выполнение ключевых действий ученицами (Instagram Reels, рассылки целевой аудитории, продажи).
- **Главные боли:** 1.5–2 часа рутинной проверки сотен разрозненных сообщений; пропуск дедлайнов ученицами («сдам в воскресенье за всю неделю»); риск сдачи одинаковых ссылок на Reels (дубликаты); ручной подсчёт кейсов для Instagram-сторис.
- **Решение:** Фоновый Telegram-бот (Telegram Business API + мини-группы) + Веб-трекер наставника (React SPA для iPad / Desktop) с принципом «Inbox Zero» и автогенератором кейсов.

```mermaid
flowchart TD
    subgraph Telegram_Ecosystem [Telegram Client Layer]
        TG_User[Ученица в Telegram]
        TG_Mentor[Наставник Ксения]
        TG_API[Telegram Bot API / Business API]
    end

    subgraph Spring_Backend [Java 21 Spring Boot Backend]
        direction TB
        subgraph Bot_Ingestion [Модуль приёма сообщений]
            TB_Consumer[SpringLongPollingBot / Webhook]
            Router[MessageRouter: business_message & group]
            AuditLog[(audit_message_logs)]
        end

        subgraph Validation_AI [Парсинг, Валидация и AI]
            HashParser[HashtagParser: #рилс, #продажи...]
            ReelValidator[ReelsAntiFraud & Probe: SHA-256 + HTTP HEAD]
            AiFilter[Spring AI ChatClient: фильтрация флуда & BURNOUT_ALERT]
            VoiceService[VoiceDownloadService: .oga -> Whisper/Gemini STT]
        end

        subgraph Business_Services [Бизнес-логика на Virtual Threads]
            ReportService[DailyReportService: Upsert uq_student_report_date]
            CaseGenerator[Discipline & Case Analytics Service]
            ReminderScheduler[@Scheduled Daily 20:00 Reminder Engine]
        end

        subgraph API_Layer [REST Controllers 14 Endpoints]
            InboxCtrl[/api/inbox]
            StudentsCtrl[/api/students]
            AnalyticsCtrl[/api/analytics]
            TasksCtrl[/api/tasks]
            SystemCtrl[/api/system]
        end
    end

    subgraph Data_Storage [PostgreSQL 16 Persistence]
        DB[(7 таблиц: students, daily_reports, reel_submissions, sales, task_rules, settings, audit)]
    end

    subgraph Web_Frontend [React + Vite SPA Dashboard]
        FE_Inbox[Вкладка '🔥 На проверку']
        FE_Calendar[Вкладка '👩‍🎓 Ученицы & Календарь']
        FE_Cases[Вкладка '📊 Аналитика & Кейсы']
        FE_Tasks[Вкладка '📋 Задания & Хэштеги']
    end

    TG_User -->|Сообщение / Войс / Рилс| TG_API
    TG_API --> TB_Consumer
    TB_Consumer --> Router
    Router -.-> AuditLog
    Router --> HashParser
    Router --> VoiceService
    VoiceService --> AiFilter
    HashParser --> ReelValidator
    HashParser --> AiFilter
    ReelValidator --> ReportService
    AiFilter --> ReportService
    ReportService --> DB
    ReminderScheduler -->|Пинг в 20:00| TG_API
    TG_API -->|Личное напоминание| TG_User

    DB --> API_Layer
    API_Layer --> Web_Frontend
    Web_Frontend -->|Ответ ученице от лица Ксении| API_Layer
    API_Layer -->|send_message business_connection_id| TG_API
    TG_API -->|Прозрачный ответ| TG_User
```

---

## 2. Разграничение тарифов разработки (BYN) и WBS

| Модуль / Фича | Базовый MVP (320 бел. руб.) | Полный вариант PRO (475 бел. руб.) | Обоснование |
| :--- | :---: | :---: | :---|
| **Telegram-бот сбора отчётов** | ✅ Приём по хэштегам `#рилс`, `#продажи`, `#рассылки`, `#вопрос` | ✅ Хэштеги + AI-парсер свободной речи и войсов | В PRO ученицы могут надиктовывать войсы голосом |
| **Среда взаимодействия** | ✅ Личные чаты и мини-группы | ✅ Telegram Business Bot API + мини-группы | В PRO прозрачные ответы от лица Ксении |
| **Анти-фрод Reels** | ✅ Валидация формата ссылки + SHA-256 дедупликация | ✅ Валидация ссылки + SHA-256 + HTTP HEAD проверка доступности | Исключена сдача закрытых профилей и удаленных видео |
| **Веб-трекер наставника** | ✅ Вкладка «🔥 На проверку» (Inbox Zero) + `tg://resolve` | ✅ «🔥 На проверку» + отправка ответа прямо с сайта в TG | Наставник отвечает за 1 клик прямо с iPad |
| **Календарь дисциплины** | ✅ Сетка Пн–Вс по дням с цветовыми статусами | ✅ Сетка Пн–Вс + процент дисциплины + фильтр статусов | Контроль стабильности группы за весь месяц |
| **Авто-напоминания (пинги)** | ❌ Ручной контроль наставником | ✅ Авто-пинг в 20:00 тем, кто не сдал отчёт | Автоматизация вечерней рутины |
| **AI-транскрибация голосовых** | ❌ Не входит (только текст) | ✅ Распознавание войсов через Whisper / Gemini STT | Экономия времени на прослушивании войсов |
| **AI-фильтрация флуда** | ❌ Не входит | ✅ Выжимка фактов + маркер выгорания (`BURNOUT_ALERT`) | Моментальная детекция учениц в стрессе |
| **Генератор кейсов для Instagram**| ❌ Базовая сводка сумм | ✅ Расчёт Точка А ➡️ Точка Б, средний чек, текст сторис | Копирование готового прогрева одной кнопкой |
| **Срок готовности** | **5–7 рабочих дней** | **10–14 рабочих дней** | Точные сроки сдачи |
| **Трудоемкость (WBS)** | **34 инженерных часа** | **52 инженерных часа** | Разница: +18 часов AI-интеграций |
| **Серверные расходы** | **~25 BYN / мес** (VPS) | **~35 BYN / мес** (VPS + AI-токены) | Предсказуемый OPEX |

---

## 3. Спецификация ключевых компонентов бэкенда

### 3.1. Telegram Bot Service (`TelegramBots 10.3.0`)
Бот поддерживает обработку входящих сообщений двух типов:
1. `business_message` (с `business_connection_id`) — личные сообщения учениц наставнику Ксении;
2. `message` — сообщения в закрытых мини-группах потока (при отключенном Bot Privacy Mode).

```java
package com.mentorhub.bot.consumer;

import com.mentorhub.bot.model.ChatSourceType;
import com.mentorhub.bot.service.MessageRouter;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
public class MentorBotConsumer implements LongPollingSingleThreadUpdateConsumer {

    private final MessageRouter messageRouter;
    private final MentorSettings mentorSettings;

    public MentorBotConsumer(MessageRouter messageRouter, MentorSettings mentorSettings) {
        this.messageRouter = messageRouter;
        this.mentorSettings = mentorSettings;
    }

    @Override
    public void consume(Update update) {
        if (update.hasBusinessConnection()) {
            messageRouter.handleBusinessConnection(update.getBusinessConnection());
            return;
        }

        if (update.hasBusinessMessage()) {
            Message msg = update.getBusinessMessage();
            // Фильтрация эхо: игнорируем исходящие сообщения самого наставника Ксении
            if (msg.getFrom() != null && msg.getFrom().getId().equals(mentorSettings.getMentorTgId())) {
                return;
            }
            messageRouter.routeIncomingMessage(msg, msg.getBusinessConnectionId(), ChatSourceType.BUSINESS_1ON1);
            return;
        }

        if (update.hasMessage()) {
            Message msg = update.getMessage();
            ChatSourceType type = msg.getChat().isGroupChat() || msg.getChat().isSuperGroupChat()
                    ? ChatSourceType.MINI_GROUP
                    : ChatSourceType.DIRECT_BOT;
            messageRouter.routeIncomingMessage(msg, null, type);
        }
    }
}
```

### 3.2. Антифрод и валидация Reels (`ReelsAntiFraudEngine`)
1. **Канонический regex:** `(?i)(?<![a-zA-Z0-9.-])(?:https?:\/\/)?(?:www\.)?(?:instagram\.com|instagr\.am)\/(?:reel|reels|p|share\/reel)\/([A-Za-z0-9_-]+)`.
2. **Извлечение канонического shortcode:** отсекаются все query-параметры (`?igsh=...`, `?utm_source=...`).
3. **Хеширование и защита от дублей (SHA-256):**
   ```java
   String reelHash = DigestUtils.sha256Hex(shortcode);
   var existingOpt = reelSubmissionRepository.findByReelHash(reelHash);
   if (existingOpt.isPresent()) {
       var prev = existingOpt.get();
       submission.setDuplicate(true);
       if (prev.getReport().getStudent().getId().equals(currentStudent.getId())) {
           submission.setDuplicateDate(prev.getReport().getReportDate().toString());
           submission.setNote("Повторная сдача ролика от " + prev.getReport().getReportDate());
       } else {
           submission.setNote("ФРОД: Ролик сдан другой ученицей (" + prev.getReport().getStudent().getFullName() + ")");
       }
   }
   ```
4. **Асинхронная проверка доступности (HTTP HEAD/GET Probe):**
   Отправка легковесного запроса с User-Agent браузера и таймаутом 2.5 сек. Статусы 200, 301, 302 считаются валидными; статус 404 бракует ролик; статус 429 или таймаут помечаются как `TIMEOUT_UNVERIFIED`.
5. **Нормализация финансовых сумм (Russian Locale Grouping Separator):**
   При обработке сумм продаж паттерны вида `\d+[.\s]\d{3}` (например, `35.500 руб`, `45 000 руб`, `120.000`) интерпретируют точку или пробел как разделитель тысяч (grouping separator), а не десятичную дробь, предотвращая 1000-кратное занижение выручки учениц при вычислении Точки Б.

### 3.3. AI-пайплайн (Spring AI 2.x / 1.x)
- **Транскрибация голосовых:** при обнаружении `message.hasVoice()` бот скачивает `.oga`-файл через Telegram API и отправляет в Whisper / Gemini API.
- **Интеллектуальная фильтрация флуда и детекция выгорания:**
  ```java
  @Service
  public class AiReportExtractor {
      private final ChatClient chatClient;

      public AiReportExtractor(ChatClient.Builder chatClientBuilder) {
          this.chatClient = chatClientBuilder.build();
      }

      public ExtractedReportDto extractFacts(String rawMessage, String studentName) {
          return chatClient.prompt()
              .system("""
                  Ты — бизнес-ассистент наставника предпринимателей.
                  Извлеки из сообщения ученицы (%s) твердые факты:
                  1. Ссылки на Reels
                  2. Сумму совершенных продаж (рубли)
                  3. Число отправленных рассылок
                  4. Прямой вопрос или затык к наставнику
                  Отсеки бытовой флуд и жалобы.
                  Если есть признаки истощения/апатии, выставь emotionalStatus = "BURNOUT_ALERT".
                  Сформируй краткую выжимку дня (1-2 предложения).
                  Верни строго ExtractedReportDto.
                  """.formatted(studentName))
              .user(rawMessage)
              .call()
              .entity(ExtractedReportDto.class);
      }
  }
  ```

### 3.4. Авто-напоминания в 20:00 (`@Scheduled DailyReminderEngine`)
```java
@Scheduled(cron = "0 0 20 * * MON-SAT", zone = "Europe/Minsk")
public void executeDailyReminderJob() {
    List<Student> debtors = studentRepository.findActiveStudentsWithoutReport(LocalDate.now());
    for (Student student : debtors) {
        notificationService.sendDeadlineReminder(student);
        Thread.sleep(1000); // Rate limiter: 1 msg/sec
    }
}
```

---

## 4. Полная модель данных (PostgreSQL 16 Schema — 7 Таблиц)

```sql
-- 1. Ученицы
CREATE TABLE students (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    avatar_text VARCHAR(10) NOT NULL,
    tg_username VARCHAR(100),
    tg_chat_id BIGINT UNIQUE,
    niche VARCHAR(255) NOT NULL,
    initial_income NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    current_income NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    initial_avg_ticket NUMERIC(12, 2) DEFAULT 0.00,
    current_avg_ticket NUMERIC(12, 2) DEFAULT 0.00,
    tariff VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_students_tg_chat_id ON students(tg_chat_id);

-- 2. Регламентные правила и хэштеги
CREATE TABLE task_rules (
    id BIGSERIAL PRIMARY KEY,
    hashtag VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    example TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Ежедневные отчеты
CREATE TABLE daily_reports (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_on_time BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    mailings_count INT DEFAULT 0,
    sales_total NUMERIC(12, 2) DEFAULT 0.00,
    mentor_question TEXT,
    mentor_reply TEXT,
    reply_sent_at TIMESTAMP WITH TIME ZONE,
    voice_transcript TEXT,
    ai_summary TEXT,
    emotional_status VARCHAR(50) DEFAULT 'NORMAL',
    raw_message_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_report_date UNIQUE (student_id, report_date)
);
CREATE INDEX idx_daily_reports_date_status ON daily_reports(report_date, status);

-- 4. Сданные Reels (с антифродом)
CREATE TABLE reel_submissions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    reel_url TEXT NOT NULL,
    canonical_url TEXT NOT NULL,
    shortcode VARCHAR(100) NOT NULL,
    reel_hash VARCHAR(64) NOT NULL,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_date VARCHAR(50),
    duplicate_of_id BIGINT REFERENCES reel_submissions(id),
    http_status_code INT DEFAULT 200,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reel_submissions_hash ON reel_submissions(reel_hash);

-- 5. Зафиксированные продажи
CREATE TABLE sales_entries (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    amount NUMERIC(12, 2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sales_entries_student ON sales_entries(student_id);

-- 6. Конфигурация наставника
CREATE TABLE mentor_settings (
    id BIGSERIAL PRIMARY KEY,
    mentor_tg_id BIGINT NOT NULL UNIQUE,
    mentor_name VARCHAR(255) NOT NULL DEFAULT 'Ксения',
    business_connection_id VARCHAR(255),
    is_business_connected BOOLEAN DEFAULT FALSE,
    reminder_cron VARCHAR(50) DEFAULT '0 0 20 * * MON-SAT',
    deadline_time TIME DEFAULT '21:00:00',
    auto_reminders_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. Журнал аудита сообщений Telegram
CREATE TABLE audit_message_logs (
    id BIGSERIAL PRIMARY KEY,
    update_id BIGINT NOT NULL,
    tg_chat_id BIGINT,
    tg_user_id BIGINT,
    source_type VARCHAR(50) NOT NULL,
    raw_payload JSONB,
    processing_status VARCHAR(50) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_logs_user_date ON audit_message_logs(tg_user_id, created_at);
```

---

## 5. Спецификация REST API (Интеграция с Frontend SPA)

Платформа предоставляет 14 стандартизированных эндпоинтов (OpenAPI 3.0, JSON, RFC 7807):

1. `GET /api/inbox` — список отчетов `PENDING` за текущую дату (Inbox Zero).
2. `POST /api/inbox/{reportId}/approve` — одобрение отчета с отправкой комментария в Telegram через `business_connection_id`.
3. `POST /api/inbox/{reportId}/reject` — отклонение отчета с указанием причины.
4. `GET /api/students` — список учениц с недельным календарем дисциплины (Пн–Вс).
5. `GET /api/students/{id}` — профиль ученицы с исторической статистикой.
6. `POST /api/students` — регистрация новой ученицы.
7. `PUT /api/students/{id}` — изменение параметров ученицы (Точка А, тариф, ниша).
8. `DELETE /api/students/{id}` — деактивация карточки ученицы.
9. `GET /api/students/{id}/discipline-calendar` — детальная сетка дней недели (`COMPLETED_ON_TIME`, `COMPLETED_LATE`, `MISSED`, `PENDING`).
10. `GET /api/analytics/case/{studentId}` — расчет Точка А ➡️ Точка Б, средний чек и сгенерированный текст для сторис Instagram.
11. `GET /api/analytics/summary` — общая сводка потока (выручка, средняя дисциплина).
12. `GET /api/tasks/rules` — список действующих правил заданий и хэштегов.
13. `PUT /api/tasks/rules/{id}` — обновление правил или примеров хэштегов.
14. `GET /api/system/status` — системный статус бота и соединения Telegram Business.

---

## 6. Технологический стек и Production Fallback

### Целевой стек (Target) vs Продакшн фолбэк (Fallback):
- **JDK:** Java 21 LTS (с виртуальными потоками) или Java 25.
- **Spring Boot:** Target `4.0.0-M1` ➡️ Fallback `3.4.2` (GA).
- **Spring AI:** Target `2.0.0-M1` ➡️ Fallback `1.0.0-M5` (BOM).
- **TelegramBots:** `10.3.0`.
- **PostgreSQL Driver:** `42.7.4`.

### Maven зависимость и Virtual Threads:
```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.4.2</spring-boot.version>
    <spring-ai.version>1.0.0-M5</spring-ai.version>
    <telegrambots.version>10.3.0</telegrambots.version>
</properties>
```
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

---

## 7. Этапы внедрения и Roadmap

1. **Этап 1 (Дни 1–4, 14 ч):** Схема БД PostgreSQL 16 (7 таблиц), каркас Spring Boot 3.4.2 / 4.x, Long Polling / Business Bot подключение, приём хэштегов и валидация Reels.
2. **Этап 2 (Дни 5–8, 16 ч):** REST API контроллеры (`/api/inbox`, `/api/students`), двусторонние ответы в Telegram, авто-напоминания в 20:00 по крону.
3. **Этап 3 (Дни 9–12, 16 ч, для тарифа PRO):** Подключение Spring AI (Whisper STT + ChatClient), фильтрация флуда, детекция выгорания, генератор сторис.
4. **Этап 4 (Дни 13–14, 6 ч):** Docker Compose деплой на VPS, сквозное E2E тестирование и передача наставнику Ксении.
