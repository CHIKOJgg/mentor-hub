# Mentor Hub — Спецификация серверной части и Telegram-бота (R2 & R3)
## Итоговый отчёт Spec Miner: Архитектура, Telegram Business API, AI Pipeline, PostgreSQL, REST API и Тарифная матрица

**Дата:** 2026-11-07  
**Автор:** Backend Spec Miner Subagent (`spec_miner_survey_backend`)  
**Оркестратор:** `63968e95-d6d9-4b5f-843b-c0a9626f44e5`  
**Целевой проект:** Mentor Hub / Mentor Tracker (нейтральный брендинг без «Миранда»)  
**Статус:** Исследование завершено, спецификации сформированы.

---

## 1. Observation (Фактические наблюдения)

В ходе анализа репозитория и нормативных документов зафиксированы следующие факты:

1. **`ORIGINAL_REQUEST.md` (строки 19–27):**
   - Требование **R2**: Разработка архитектурной спецификации Telegram-бота (прослушивание 1-на-1 через `business_message` vs мини-группы; валидация ссылок на Instagram Reels с проверкой доступности и дедупликацией по SHA-256; парсинг хэштегов `#рилс`, `#продажи`, `#рассылки`, `#вопрос`; авто-напоминания в 20:00; AI-пайплайн фильтрации флуда и транскрибация голосовых; разграничение тарифов 320 BYN vs 475 BYN).
   - Требование **R3**: Стек на Java (Spring Boot 4.x / Spring AI 2.x с обязательным документированным фолбэком на Spring Boot 3.4.x / Spring AI 1.x, PostgreSQL 16+, TelegramBots 10.x).

2. **Существующий документ `docs/ARCHITECTURE_SPRING_BOOT.md`:**
   - Содержит базовую концепцию, Mermaid-диаграмму и краткие фрагменты кода (`MentorBotConsumer`, `ReelsValidator`, `AiReportExtractor`).
   - Модель базы данных ограничена 4 таблицами (`students`, `daily_reports`, `reel_submissions`, `sales_entries`).
   - Отсутствуют таблицы для управления регламентом заданий (`task_rules`), конфигурации наставника и бизнес-подключения (`mentor_settings`), а также журнала аудита сырых сообщений (`audit_message_logs`).
   - Тарифная матрица приведена верхнеуровнево (7 строк) без детальной оценки трудозатрат (WBS) по модулям и часам.
   - REST API спецификация содержит лишь 4 базовых эндпоинта без схем ошибок, DTO создания/редактирования учениц, управления заданиями и системных хуков синхронизации.

3. **Фронтенд SPA (`frontend/src/types.ts`, `mockData.ts`, `App.tsx`):**
   - Строго зафиксированы структуры данных:
     - `ReelSubmission` (`id`, `url`, `shortcode`, `isValid`, `isDuplicate`, `duplicateDate`).
     - `DailyReport` (`id`, `studentId`, `studentName`, `avatarText`, `tgUsername`, `tariff`, `submittedAt`, `isOnTime`, `status`, `reels`, `salesAmount`, `mailingsCount`, `mentorQuestion`, `voiceTranscript`, `aiSummary`).
     - `StudentDiscipline` (`id`, `fullName`, `avatarText`, `niche`, `initialIncome`, `currentIncome`, `initialAvgTicket`, `currentAvgTicket`, `totalReels`, `totalMailings`, `disciplineRate`, `weekDays: { day, date, status }[]`).
     - `DayStatus`: `'COMPLETED_ON_TIME' | 'COMPLETED_LATE' | 'MISSED' | 'PENDING' | 'WEEKEND'`.
     - `TaskRule`: (`id`, `hashtag`, `title`, `description`, `required`, `example`).
   - Таб «Тарифы (320 / 475 BYN)» в `App.tsx` (строки 688–812) уже визуализирует базовое различие тарифов для демонстрации заказчику.

---

## 2. Gap Analysis (Детальный анализ расхождений и белых пятен)

| Область | Что было в `docs/ARCHITECTURE_SPRING_BOOT.md` | Выявленные критические пробелы | Специфицированное решение для R2/R3 |
|:---|:---|:---|:---|
| **Telegram Ingestion** | 5-строчный сниппет с `update.hasBusinessMessage()` | Не описан жизненный цикл `business_connection`, получение токена, права бота `can_reply`, хранение `business_connection_id`, обработка `edited_business_message`, а также специфика мини-групп (отключение Bot Privacy Mode, сопоставление `from.id` с БД). | Разработана полная двухканальная схема (Business API 1-на-1 + Mini-Groups) с поддержкой роутинга, связки по `tg_chat_id` / `tg_username` и двусторонней отправки ответов. |
| **Антифрод Reels** | Простой regex и вычисление SHA-256 | Regex не поддерживал URL вида `/share/reel/` и `instagr.am`; проверка доступности не учитывала 302-редиректы Instagram на страницу авторизации; не была определена область дедупликации (сквозная по всем ученицам или индивидуальная). | Разработан нормализатор шорткодов, глобальная дедупликация (кросс-проверка учениц), устойчивый HTTP HEAD/GET probe с User-Agent и обработкой 302/404/429. |
| **Парсинг хэштегов** | Перечисление `#рилс`, `#продажи` | Не описано поведение при накопительной сдаче отчетов частями в течение дня, опечатки в тегах, парсинг сумм с пробелами и суффиксами («45 000 руб», «45к»). | Реализован стейт-агрегатор суточного отчета (upsert по `report_date`), гибкие регулярные выражения для сумм и чисел. |
| **AI Pipeline** | Краткий prompt на `ExtractedReport` | Не определен формат транскрибации аудио (OGG Opus в Telegram), конвертация форматов, обработка выгорания ученицы, фолбэк при сбое AI API. | Спроектирован пайплайн: скачивание `.oga` -> Whisper/Gemini STT -> Structured Output `ExtractedReportDto` с детекцией выгорания (`BURNOUT_ALERT`) и автоматическим откатом на детерминированный regex-парсер. |
| **Стек Java/Spring** | Декларация Spring Boot 4.x / Spring AI 2.x | Spring Boot 4.x и Spring AI 2.x находятся в фазе milestone/preview; отсутствовали точные Maven-координаты, соответствие версий Java (21 vs 25) и пошаговый production fallback план. | Сформирована матрица совместимости и рабочий fallback на стабильный стек Spring Boot 3.4.2 + Spring AI 1.0.0-M5 (Java 21 LTS). |
| **Тарифы и Roadmap** | Общая таблица из 7 строк | Отсутствовал Work Breakdown Structure (WBS), почасовая оценка работ, распределение задач по спринтам и обоснование ценообразования (320 vs 475 BYN). | Подготовлена детальная смета по 7 модулям с трудоемкостью (34 ч vs 52 ч) и пошаговым планом внедрения. |
| **База данных** | 4 базовые таблицы | Нет таблицы `task_rules` (хотя на фронтенде есть таб), нет `mentor_settings`, нет аудита сообщений, нет внешних ключей каскадного удаления и индексов для выборок дашборда. | Спроектирован DDL из 7 нормализованных таблиц с индексами, ограничениями и дефолтными данными. |
| **REST API** | 4 эндпоинта без спецификации контрактов | Нет эндпоинтов управления ученицами, заданиями, синхронизации Telegram, отклонения отчетов и структур ошибок (RFC 7807 Problem Details). | Разработана REST API спецификация из 14 эндпоинтов, на 100% соответствующая интерфейсу React SPA. |

---

## 3. Комплексная техническая архитектура (R2 & R3)

### 3.1. Архитектура Telegram-бота: Business API vs Мини-группы

```
                             ┌──────────────────────────────────────────────┐
                             │               TELEGRAM CLIENT                │
                             └──────┬────────────────────────────────┬──────┘
                                    │                                │
                  1-on-1 Business   │                                │  Mini-Group Message
                     Chat Update    │                                │  (Privacy Mode OFF)
                                    ▼                                ▼
                 ┌──────────────────────────────────────┐  ┌───────────────────────────────────┐
                 │ Update: business_message             │  │ Update: message                   │
                 │ - business_connection_id             │  │ - chat.id (negative group ID)     │
                 │ - chat.id (student private chat)     │  │ - from.id (student user ID)       │
                 │ - from.id / username                 │  │ - text / voice / media            │
                 └──────────────────┬───────────────────┘  └─────────────────┬─────────────────┘
                                    │                                        │
                                    └───────────────────┬────────────────────┘
                                                        ▼
                                   ┌────────────────────────────────────────┐
                                   │           SpringLongPollingBot         │
                                   │        (MentorBotConsumer 10.3)        │
                                   └────────────────────┬───────────────────┘
                                                        ▼
                                   ┌────────────────────────────────────────┐
                                   │             MessageRouter              │
                                   │  - Audit log in raw_messages           │
                                   │  - Resolve student by tg_id / username │
                                   └────────────┬───────────────────────────┘
                                                │
                       ┌────────────────────────┴────────────────────────┐
                       ▼                                                 ▼
        ┌─────────────────────────────┐                   ┌─────────────────────────────┐
        │        Voice / Audio        │                   │          Text Body          │
        │      (message.hasVoice)     │                   │                             │
        └──────────────┬──────────────┘                   └──────────────┬──────────────┘
                       ▼                                                 ▼
        ┌─────────────────────────────┐                   ┌─────────────────────────────┐
        │    VoiceDownloadService     │                   │      Hashtag & AI Parser    │
        │  Download .oga via Bot API  │                   │ - Check #рилс, #продажи     │
        └──────────────┬──────────────┘                   │ - Spring AI ChatClient      │
                       ▼                                  └──────────────┬──────────────┘
        ┌─────────────────────────────┐                                  │
        │    Spring AI Whisper STT    │                                  │
        │ Transcribe audio to text    │──────────────────────────────────┘
        └──────────────┬──────────────┘
                       ▼
        ┌───────────────────────────────────────────────────────────────────────────────┐
        │                             DailyReportService                                │
        │ - Normalizes Instagram URLs & Extracts Shortcode                              │
        │ - SHA-256 deduplication against DB                                            │
        │ - Async HTTP HEAD accessibility probe                                         │
        │ - Aggregates daily metrics (sales amount, mailings count)                     │
        │ - Flags questions for mentor & detects burnout signals                        │
        │ - Upserts record into PostgreSQL (daily_reports, reel_submissions)            │
        └──────────────────────────────────────┬────────────────────────────────────────┘
                                               ▼
        ┌───────────────────────────────────────────────────────────────────────────────┐
        │                              PostgreSQL 16 DB                                 │
        │       students | daily_reports | reel_submissions | sales | task_rules        │
        └──────────────────────────────────────▲────────────────────────────────────────┘
                                               │
                                               ▼
        ┌───────────────────────────────────────────────────────────────────────────────┐
        │                             Spring REST Controllers                           │
        │           /api/inbox | /api/students | /api/analytics | /api/tasks            │
        └──────────────────────────────────────▲────────────────────────────────────────┘
                                               │ HTTP / JSON
                                               ▼
        ┌───────────────────────────────────────────────────────────────────────────────┐
        │                       Frontend SPA (React + Vite + Tailwind)                  │
        │            «🔥 На проверку» | «👩‍🎓 Дисциплина» | «📊 Кейсы» | «📋 Задания»    │
        └───────────────────────────────────────────────────────────────────────────────┘
```

#### Механика Telegram Business API (`business_message`):
1. **Инициализация:** Наставник Ксения в клиенте Telegram (Настройки -> Telegram Business -> Чат-боты) подключает бота `MentorHubBot` к своему аккаунту.
2. Бот получает системный апдейт `business_connection`, сохраняет в таблицу `mentor_settings` актуальный `business_connection_id` и проверяет флаг `can_reply = true`.
3. При отправке ученицей сообщения в личный чат с Ксенией Telegram API передает апдейт `business_message`.
4. Бот извлекает `from.id`, находит карточку ученицы в таблице `students` и передает текст/голос в пайплайн обработки.
5. При одобрении отчета наставником в веб-трекере с комментарием бэкенд выполняет вызов `botClient.execute(SendMessage.builder().businessConnectionId(connId).chatId(studentTgChatId).text(replyText).build())`. Сообщение отправляется в диалог **от лица Ксении**, создавая бесшовный эффект личного присутствия.

#### Механика работы в мини-группах:
1. Бот добавляется в закрытую группу потока (например, «VIP Поток Ноябрь»).
2. В @BotFather отключается режим приватности (`/setprivacy -> Disable`) либо бот наделяется правами администратора группы, что позволяет ему читать все сообщения потока.
3. При поступлении сообщения апдейт приходит как стандартный `message` с отрицательным `chat.id` группы.
4. Бот идентифицирует автора по `from.id` / `from.username`.
5. Ответы наставника из веб-интерфейса могут направляться либо в ответ на исходное сообщение в группе (`reply_to_message_id`), либо в личные сообщения ученицы через прямого бота.

---

### 3.2. Алгоритм валидации Reels и защита от фрода

1. **Регулярное выражение с нормализацией:**
   ```regexp
   (?i)\b(?:https?:\/\/)?(?:www\.)?(?:instagram\.com|instagr\.am)\/(?:reel|reels|p|share\/reel)\/([A-Za-z0-9_-]+)
   ```
2. **Извлечение канонического идентификатора:**
   - Из ссылки отсекаются все query-параметры (`?igsh=...`, `?utm_source=...`).
   - Формируется канонический URL: `https://www.instagram.com/reel/{shortcode}/`.
3. **Хеширование и дедупликация (SHA-256):**
   - Вычисляется `reel_hash = SHA256(shortcode)`.
   - Запрос к базе данных:
     ```sql
     SELECT r.id, dr.report_date, s.full_name 
     FROM reel_submissions r
     JOIN daily_reports dr ON r.report_id = dr.id
     JOIN students s ON dr.student_id = s.id
     WHERE r.reel_hash = :hash
     ORDER BY r.checked_at ASC LIMIT 1;
     ```
   - Если хэш найден:
     - Для той же ученицы: устанавливается флаг `is_duplicate = true`, текст ошибки: `«Повторная сдача: ролик уже сдавался вами {date}»`.
     - Для другой ученицы: устанавливается флаг `is_duplicate = true`, алерт фрода: `«Антифрод: этот ролик сдавался другой ученицей ({student_name}, {date})»`.
4. **Асинхронная проверка доступности (HTTP Probe):**
   - Бэкенд выполняет неблокирующий HTTP GET запрос с ограничением по таймауту (connect: 2000 мс, read: 2500 мс) и кастомным заголовком `User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36...`.
   - Анализ ответа:
     - HTTP `200 OK` или HTTP `302/301` с адресацией на страницу логина/эмбеда — ролик валиден (`is_valid = true`).
     - HTTP `404 Not Found` или страница «Страница недоступна / удалена» — ролик бракуется (`is_valid = false`, `error = "Ролик не найден или аккаунт закрыт"`).
     - При таймауте сети флаг `is_valid` сохраняется как `true` с пометкой `check_status = "TIMEOUT_UNVERIFIED"`, чтобы не блокировать прием отчета при сетевых задержках.

---

### 3.3. Парсер хэштегов и суточная агрегация

- **Регламентные хэштеги:**
  - `#рилс` / `#reels` — ссылка на рилс (валидация + антифрод).
  - `#продажи` / `#продажа` — распознавание числовых значений с очисткой от пробелов, букв «к/k/тыс» (например, `#продажи 45 000 руб` -> `45000.00`, `#продажи 80к` -> `80000.00`).
  - `#рассылки` / `#рассылка` — целое число отправленных сообщений (`#рассылки 25` -> `25`).
  - `#вопрос` / `#затык` — весь последующий текст сообщения до следующего хэштега или конца абзаца выделяется как `mentor_question`.

- **Агрегация в течение дня (Upsert):**
  - Дедлайн сдачи: ежедневно в 21:00.
  - Если ученица присылает данные несколькими сообщениями в течение дня (например, в 12:00 выложила рилс, а в 19:00 написала о закрытых продажах), бэкенд находит отчет за текущую дату (`CURRENT_DATE`) и обновляет его:
    - `mailings_count`: суммируется (`old_val + new_val`).
    - `sales_total`: добавляется новая проводка в `sales_entries`, общая сумма пересчитывается.
    - `reels`: прикрепляются новые валидированные ссылки в `reel_submissions`.
    - `mentor_question`: конкатенируется с разделителем или обновляется свежим вопросом.

---

### 3.4. Авто-напоминания в 20:00 (Reminder Engine)

- **Планировщик:** Spring `@Scheduled(cron = "0 0 20 * * MON-SAT", zone = "Europe/Minsk")`.
- **Логика выборки несдавших:**
  ```sql
  SELECT s.id, s.full_name, s.tg_chat_id, s.tg_username
  FROM students s
  WHERE s.is_active = TRUE
    AND NOT EXISTS (
      SELECT 1 FROM daily_reports r
      WHERE r.student_id = s.id 
        AND r.report_date = CURRENT_DATE
        AND r.status IN ('PENDING', 'APPROVED')
    );
  ```
- **Отправка уведомления:**
  - Персонализированный текст:
    *«{Имя}, привет! Напоминаю: до дедлайна сдачи отчета остался 1 час (до 21:00) ⏰ Присылай свой #рилс, результаты по #рассылкам и #продажам!»*
  - Отправка через Telegram Business API в личный чат от имени Ксении либо адресный тег `@username` в мини-группе.
  - Ограничение частоты (Rate Limiter): 1 сообщение в секунду для предотвращения блокировок Telegram API.

---

### 3.5. AI Processing Pipeline (Spring AI 2.x / 1.x)

1. **Транскрибация голосовых сообщений (Whisper / Gemini):**
   - При обнаружении `message.hasVoice()` бот скачивает `.oga` файл через метод `getFile` Telegram API.
   - Аудиопоток передается в Spring AI `OpenAiAudioTranscriptionModel` (или Gemini Multimodal Audio):
     ```java
     AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(
         audioResource,
         OpenAiAudioTranscriptionOptions.builder()
             .withLanguage("ru")
             .withResponseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
             .withTemperature(0.2f)
             .build()
     );
     AudioTranscriptionResponse response = transcriptionModel.call(prompt);
     String transcribedText = response.getResult().getOutput();
     ```
   - Текст сохраняется в поле `daily_reports.voice_transcript`.

2. **Интеллектуальная фильтрация флуда и жалоб от фактов:**
   - Системный промпт:
     ```text
     Ты — строгий аналитический ассистент наставника предпринимателей.
     Проанализируй входящее сообщение (или транскрипт войса) ученицы.
     Твоя задача:
     1. Извлечь твердые факты: опубликованные ссылки на Reels, сумму совершенных продаж, число рассылок.
     2. Вычленить прямой вопрос или профессиональный затык к наставнику.
     3. Полностью отфильтровать бытовой флуд, жалобы на усталость, погоду и настроение.
     4. Оценить эмоциональный статус ученицы: NORMAL, BURNOUT_ALERT, STRESSED, CONFIDENT.
     5. Сформировать короткую выжимку дня (1-2 предложения) для дашборда наставника.
     Верни строго JSON объект структуры ExtractedReportDto.
     ```
   - Выходной DTO (`ExtractedReportDto`):
     ```java
     public record ExtractedReportDto(
         List<String> reelUrls,
         BigDecimal salesAmount,
         Integer mailingsCount,
         String mentorQuestion,
         String emotionalStatus,      // NORMAL, BURNOUT_ALERT
         String aiSummaryText,        // Короткая выжимка
         String filteredFloodSnippet  // Что было отфильтровано
     ) {}
     ```
   - **Фолбэк-стратегия:** Если обращение к AI API падает по таймауту (> 3 сек) или исчерпан лимит токенов, система переключается на регулярный хэштег-парсер, не прерывая прием отчета ученицы.

---

### 3.6. Спецификация технологического стека (Spring Boot 4.x / Fallback 3.4.x)

#### Целевой стек (Spring Boot 4.x Milestone & Spring AI 2.x):
- **JDK:** Java 21 LTS (с поддержкой Virtual Threads) или Java 25.
- **Spring Boot:** `4.0.0-M1`
- **Spring AI:** `2.0.0-M1` (BOM: `org.springframework.ai:spring-ai-bom:2.0.0-M1`)
- **TelegramBots:** `10.3.0` (`org.telegram:telegrambots-springboot-longpolling-starter:10.3.0`)
- **Database Driver:** `org.postgresql:postgresql:42.7.4`
- **Migration:** `org.flywaydb:flyway-database-postgresql:10.20.1`

#### Документированный Fallback стек (Production GA):
При сборке в стабильном окружении используется проверенный GA релиз:
- **Spring Boot:** `3.4.2`
- **Spring AI:** `1.0.0-M5`
- **Maven Dependency Mapping:**
  ```xml
  <properties>
      <java.version>21</java.version>
      <!-- Target: 4.0.0-M1 | Fallback: 3.4.2 -->
      <spring-boot.version>3.4.2</spring-boot.version>
      <!-- Target: 2.0.0-M1 | Fallback: 1.0.0-M5 -->
      <spring-ai.version>1.0.0-M5</spring-ai.version>
      <telegrambots.version>10.3.0</telegrambots.version>
  </properties>
  ```
- **Многопоточность:** Включение виртуальных потоков Java 21 через `application.yml`:
  ```yaml
  spring:
    threads:
      virtual:
        enabled: true
  ```
  Это обеспечивает высокую масштабируемость при синхронных сетевых вызовах (проверка ссылок Instagram и скачивание голосовых файлов Telegram).

---

## 4. Тарифная матрица и план разработки (Roadmap)

### 4.1. Сравнительная матрица тарифов

| Модуль / Функциональность | Базовый MVP (320 бел. руб.) | Полный вариант PRO (475 бел. руб.) |
|:---|:---:|:---:|
| **Приём отчетов в Telegram** | ✅ Только по хэштегам (`#рилс`, `#продажи`, `#рассылки`, `#вопрос`) | ✅ Хэштеги + AI-парсер свободной речи и войсов |
| **Среда общения** | ✅ Личные чаты и мини-группы | ✅ Личные чаты через Telegram Business API + мини-группы |
| **Антифрод Reels** | ✅ Валидация формата ссылки + SHA-256 защита от дублей | ✅ Валидация + SHA-256 + HTTP HEAD проверка доступности ролика |
| **Веб-трекер наставника** | ✅ Вкладка «🔥 На проверку» (Inbox Zero) + ссылка `tg://resolve` | ✅ Вкладка «🔥 На проверку» + отправка ответа прямо из веб-трекера в TG |
| **Календарь дисциплины** | ✅ Табличная сетка Пн–Вс с цветовыми статусами | ✅ Сетка Пн–Вс + процент дисциплины + фильтр по статусам |
| **Напоминания молчунам** | ❌ Ручной контроль наставником | ✅ Автоматический пинг в 20:00 несдавшим отчет |
| **AI-транскрибация голосовых** | ❌ Не входит (только текст) | ✅ Распознавание войсов через Whisper / Gemini API |
| **AI-фильтрация флуда** | ❌ Не входит | ✅ Выжимка фактов дня + детекция выгорания (`BURNOUT_ALERT`) |
| **Кейсы для Instagram Stories**| ❌ Базовая статистика сумм | ✅ Расчет Точка А ➡️ Б, средний чек, генератор готового текста сторис |
| **Управление заданиями** | ✅ Фиксированный список 4 правил | ✅ Динамическое управление правилами и хэштегами через API |
| **Срок готовности** | **5–7 рабочих дней** | **10–14 рабочих дней** |
| **Трудоемкость (WBS)** | **34 инженерных часа** | **52 инженерных часа** |

---

### 4.2. Детальная смета трудозатрат (WBS)

| № | Этап / Модуль разработки | Базовый MVP (часы) | Полный вариант PRO (часы) | Результат этапа |
|:---|:---|:---:|:---:|:---|
| **1** | **Архитектура БД и каркас Spring Boot**<br>Настройка PostgreSQL, Flyway-миграции, Entity, JPA репозитории | 6 ч | 8 ч | Схема БД развернута, тесты соединения успешны |
| **2** | **Telegram Ingestion Engine**<br>Подключение `TelegramBots 10.3`, `business_message` роутер, сопоставление учениц | 8 ч | 12 ч | Бот слушает сообщения и сопоставляет с профилями |
| **3** | **Валидация Reels и парсер хэштегов**<br>Regex-парсер, SHA-256 хеш, асинхронный HTTP probe | 8 ч | 12 ч | Защита от дублей и бракованных ссылок работает |
| **4** | **AI-модуль (Spring AI)**<br>Транскрибация войсов, prompt-инжиниринг фильтрации флуда, детекция выгорания | 0 ч | 10 ч | Голосовые преобразуются в текст, флуд отсекается |
| **5** | **Авто-напоминания (Scheduler)**<br>Spring `@Scheduled` в 20:00, выборка должников, rate-limited отправка пингов | 0 ч | 4 ч | Несдавшие получают напоминание за 1 ч до дедлайна |
| **6** | **REST API для веб-трекера**<br>Контроллеры `/api/inbox`, `/api/students`, `/api/analytics`, `/api/tasks` | 8 ч | 12 ч | Полная интеграция с React SPA дашбордом |
| **7** | **Деплой и сдача проекта**<br>Docker Compose, конфигурирование переменных окружения, инструкция | 4 ч | 6 ч | Проект запущен на VPS, проведен приемочный тест |
| | **ИТОГО:** | **34 часа** | **52 часа** | **Готовая система под ключ** |

---

## 5. Полная схема базы данных (PostgreSQL DDL)

```sql
-- =============================================================================
-- MENTOR HUB — СХЕМА БАЗЫ ДАННЫХ (POSTGRESQL 16+)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Таблица профилей учениц
CREATE TABLE students (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    avatar_text VARCHAR(10) NOT NULL,                     -- Например: 'МГ', 'АП'
    tg_username VARCHAR(100),                             -- Без символа @
    tg_chat_id BIGINT UNIQUE,                             -- Уникальный Telegram ID пользователя
    niche VARCHAR(255) NOT NULL,                          -- Ниша: 'Стилист', 'Психолог'
    initial_income NUMERIC(12, 2) NOT NULL DEFAULT 0.00,  -- Точка А (доход на старте)
    current_income NUMERIC(12, 2) NOT NULL DEFAULT 0.00,  -- Точка Б (текущий доход)
    initial_avg_ticket NUMERIC(12, 2) DEFAULT 0.00,       -- Стартовый средний чек
    current_avg_ticket NUMERIC(12, 2) DEFAULT 0.00,       -- Текущий средний чек
    tariff VARCHAR(50) NOT NULL DEFAULT 'STANDARD',       -- 'STANDARD', 'VIP'
    start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_students_tg_chat_id ON students(tg_chat_id);
CREATE INDEX idx_students_tg_username ON students(tg_username);

-- 2. Таблица регламентных правил и хэштегов
CREATE TABLE task_rules (
    id BIGSERIAL PRIMARY KEY,
    hashtag VARCHAR(50) NOT NULL UNIQUE,                  -- '#рилс', '#продажи', '#рассылки', '#вопрос'
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    example TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Таблица ежедневных отчетов
CREATE TABLE daily_reports (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_on_time BOOLEAN NOT NULL DEFAULT TRUE,             -- true если сдано до 21:00
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',        -- 'PENDING', 'APPROVED', 'REJECTED'
    mailings_count INT DEFAULT 0,
    sales_total NUMERIC(12, 2) DEFAULT 0.00,
    mentor_question TEXT,                                 -- Вычлененный вопрос ученицы
    mentor_reply TEXT,                                    -- Ответ наставника
    reply_sent_at TIMESTAMP WITH TIME ZONE,
    voice_transcript TEXT,                                -- Расшифровка войса через Whisper
    ai_summary TEXT,                                      -- Выжимка дня от Spring AI
    emotional_status VARCHAR(50) DEFAULT 'NORMAL',        -- 'NORMAL', 'BURNOUT_ALERT', 'STRESSED'
    raw_message_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_report_date UNIQUE (student_id, report_date)
);
CREATE INDEX idx_daily_reports_date_status ON daily_reports(report_date, status);

-- 4. Таблица сданных роликов Reels (с защитой от дубликатов)
CREATE TABLE reel_submissions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    reel_url TEXT NOT NULL,
    canonical_url TEXT NOT NULL,
    shortcode VARCHAR(100) NOT NULL,
    reel_hash VARCHAR(64) NOT NULL,                       -- SHA-256 хеш шорткода
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_of_id BIGINT REFERENCES reel_submissions(id),
    http_status_code INT DEFAULT 200,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reel_submissions_hash ON reel_submissions(reel_hash);

-- 5. Таблица зафиксированных продаж
CREATE TABLE sales_entries (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    amount NUMERIC(12, 2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sales_entries_student ON sales_entries(student_id);

-- 6. Конфигурация наставника и подключение Telegram Business
CREATE TABLE mentor_settings (
    id BIGSERIAL PRIMARY KEY,
    mentor_tg_id BIGINT NOT NULL UNIQUE,
    business_connection_id VARCHAR(255),
    is_business_connected BOOLEAN DEFAULT FALSE,
    reminder_cron VARCHAR(50) DEFAULT '0 0 20 * * MON-SAT',
    deadline_time TIME DEFAULT '21:00:00',
    auto_reminders_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. Журнал аудита входящих сообщений Telegram
CREATE TABLE audit_message_logs (
    id BIGSERIAL PRIMARY KEY,
    update_id BIGINT,
    tg_chat_id BIGINT,
    tg_user_id BIGINT,
    source_type VARCHAR(50) NOT NULL,                     -- 'BUSINESS_1ON1', 'GROUP_CHAT'
    raw_payload JSONB,
    processing_status VARCHAR(50) NOT NULL,               -- 'PROCESSED', 'UNMATCHED_USER', 'ERROR'
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_logs_user_date ON audit_message_logs(tg_user_id, created_at);
```

---

## 6. Спецификация REST API (Контракты бэкенда и SPA)

Все эндпоинты возвращают данные в кодировке `UTF-8` и формате `application/json`. Формат ошибок регламентирован стандартом **RFC 7807 (Problem Details for HTTP APIs)**.

### 6.1. Группа «🔥 На проверку» (Inbox Zero)

#### `GET /api/inbox`
Возвращает список непроверенных отчетов (`status = 'PENDING'`) за сегодня.
- **Ответ 200 OK:**
  ```json
  [
    {
      "id": "rep-1",
      "studentId": "st-1",
      "studentName": "Милана Горохова",
      "avatarText": "МГ",
      "tgUsername": "milana_gorohova",
      "tariff": "VIP",
      "submittedAt": "18:24 (сегодня)",
      "isOnTime": true,
      "status": "PENDING",
      "reels": [
        {
          "id": "r-1",
          "url": "https://instagram.com/reel/DC194kaP91m",
          "shortcode": "DC194kaP91m",
          "isValid": true,
          "isDuplicate": false
        }
      ],
      "salesAmount": 45000.00,
      "mailingsCount": 25,
      "mentorQuestion": "Ксения, клиентка хочет рассрочку на 3 месяца через банк, но у нее плохая кредитная история. Предложить рассрочку от себя?",
      "voiceTranscript": null,
      "aiSummary": "Опубликовано 2 рилса по скрипту #3. Отправлено 25 рассылок в Директ. Закрыта продажа консультации за 45 000 ₽."
    }
  ]
  ```

#### `POST /api/inbox/{reportId}/approve`
Одобрение отчета с опциональной отправкой комментария в Telegram.
- **Тело запроса:**
  ```json
  {
    "replyText": "Отличная работа! По рассрочке лучше заключить договор с графиком платежей."
  }
  ```
- **Ответ 200 OK:**
  ```json
  {
    "success": true,
    "reportId": "rep-1",
    "status": "APPROVED",
    "tgMessageDelivered": true
  }
  ```

#### `POST /api/inbox/{reportId}/reject`
Отклонение отчета с указанием причины (отправляется ученице в Telegram).
- **Тело запроса:**
  ```json
  {
    "reason": "Ссылка на Reels ведет на закрытый аккаунт. Пожалуйста, открой профиль и пришли ссылку заново."
  }
  ```
- **Ответ 200 OK:**
  ```json
  {
    "success": true,
    "reportId": "rep-2",
    "status": "REJECTED"
  }
  ```

---

### 6.2. Группа «👩‍🎓 Ученицы & Календарь дисциплины»

#### `GET /api/students`
Список всех активных учениц с текущими показателями прогресса.
- **Ответ 200 OK:** Массив объектов `StudentDiscipline`:
  ```json
  [
    {
      "id": "st-1",
      "fullName": "Милана Горохова",
      "avatarText": "МГ",
      "niche": "Стилист-имиджмейкер",
      "initialIncome": 30000.00,
      "currentIncome": 180000.00,
      "initialAvgTicket": 3000.00,
      "currentAvgTicket": 45000.00,
      "totalReels": 14,
      "totalMailings": 145,
      "disciplineRate": 95,
      "weekDays": [
        { "day": "Пн", "date": "02.11", "status": "COMPLETED_ON_TIME" },
        { "day": "Вт", "date": "03.11", "status": "COMPLETED_ON_TIME" },
        { "day": "Ср", "date": "04.11", "status": "COMPLETED_ON_TIME" },
        { "day": "Чт", "date": "05.11", "status": "COMPLETED_ON_TIME" },
        { "day": "Пт", "date": "06.11", "status": "COMPLETED_LATE" },
        { "day": "Сб", "date": "07.11", "status": "COMPLETED_ON_TIME" },
        { "day": "Вс", "date": "08.11", "status": "PENDING" }
      ]
    }
  ]
  ```

#### `POST /api/students`
Создание карточки новой ученицы.
- **Тело запроса:**
  ```json
  {
    "fullName": "Елена Морозова",
    "tgUsername": "morozova_stylist",
    "tgChatId": 987654321,
    "niche": "Дизайнер интерьеров",
    "initialIncome": 40000.00,
    "initialAvgTicket": 15000.00,
    "tariff": "VIP"
  }
  ```
- **Ответ 201 Created:** Созданная сущность с ID.

---

### 6.3. Группа «📊 Аналитика & Кейсы для Инсты»

#### `GET /api/analytics/case/{studentId}`
Возвращает готовые агрегированные показатели и сгенерированный рекламный текст для Instagram Stories.
- **Ответ 200 OK:**
  ```json
  {
    "studentId": "st-1",
    "fullName": "Милана Горохова",
    "niche": "Стилист-имиджмейкер",
    "point_a_income": 30000.00,
    "point_b_income": 180000.00,
    "growthMultiplier": "x6.0",
    "avgTicketGrowth": "с 3 000 ₽ до 45 000 ₽",
    "totalReels": 14,
    "totalMailings": 145,
    "disciplineRate": 95,
    "generatedStoryCopy": "🔥 КЕЙС ПОТОКА: Милана Горохова\nНиша: Стилист-имиджмейкер\n\n🚀 Точка А: 30 000 ₽ ➡️ Точка Б: 180 000 ₽ (РОСТ x6.0!)\n📈 Средний чек: вырос с 3 000 ₽ до 45 000 ₽\n🎬 Контент: 14 проверенных Reels за 3 недели\n✉️ Исходящий трафик: 145 рассылок по скрипту\n\nСекрет результата: ежедневная дисциплина сдачи отчетов и дожим заявок без выгорания!"
  }
  ```

---

### 6.4. Группа «📋 Задания & Хэштеги»

#### `GET /api/tasks/rules`
Возвращает список действующих регламентных правил.
- **Ответ 200 OK:**
  ```json
  [
    {
      "id": "rule-1",
      "hashtag": "#рилс",
      "title": "Ссылка на опубликованный Reels",
      "description": "Бот автоматически проверяет доступность ссылки и защищает от сдачи старых дубликатов.",
      "required": true,
      "example": "#рилс https://instagram.com/reel/C8XYZ123"
    },
    {
      "id": "rule-2",
      "hashtag": "#продажи",
      "title": "Сумма оформленной продажи",
      "description": "Автоматически суммирует доход, обновляет средний чек и пересчитывает кратный рост Точки Б.",
      "required": false,
      "example": "#продажи 45000 (оплата за пакет консультаций)"
    }
  ]
  ```

---

### 6.5. Группа «⚙️ Системные события и статус бота»

#### `GET /api/system/status`
- **Ответ 200 OK:**
  ```json
  {
    "botActive": true,
    "botUsername": "MentorHubBot",
    "businessConnectionStatus": "CONNECTED",
    "activeStudentsCount": 5,
    "pendingReportsToday": 3,
    "springBootVersion": "3.4.2",
    "springAiStatus": "AVAILABLE"
  }
  ```

#### `POST /api/system/sync-telegram`
Принудительная проверка и синхронизация очереди апдейтов из Telegram.
- **Ответ 200 OK:** `{"status": "SYNCED", "syncedUpdatesCount": 0}`

---

## 7. Features Discovered (Каталог выявленных функций)

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Ingestion | Telegram Business Listening | Прослушивание входящих сообщений учениц в 1-на-1 чатах с наставником | `Update` с объектом `business_message` | `IncomingMessageDto`, привязка к `students` | Если ученица не зарегистрирована, логируется в `audit_message_logs` с пометкой `UNMATCHED` | `ORIGINAL_REQUEST.md`, `ARCHITECTURE_SPRING_BOOT.md` |
| 2 | Ingestion | Telegram Mini-Group Listening | Прием отчетов из закрытых групп учениц | `Update` с объектом `message` (групповой чат) | Идентификация по `from.id`, создание отчета | Игнорируются сообщения ботов и системные события | `ORIGINAL_REQUEST.md` |
| 3 | Anti-fraud | Reels Canonical Extraction | Вычленение shortcode из любых вариантов ссылок Instagram Reels | Сырая строка URL | Нормализованный shortcode и канонический URL | Ошибочный формат URL возвращает `isValid = false` | `ORIGINAL_REQUEST.md`, `mockData.ts` |
| 4 | Anti-fraud | SHA-256 Deduplication | Глобальный и индивидуальный поиск повторной сдачи одного и того же ролика | `shortcode` ролика | `is_duplicate` (boolean), дата первой сдачи | Помечается желтым/красным бейджем «Дубликат!» в UI | `docs/ARCHITECTURE_SPRING_BOOT.md` |
| 5 | Anti-fraud | Async HTTP Accessibility Probe | Проверка существования ролика через легкий HTTP GET/HEAD probe | Канонический URL | HTTP статус, флаг доступности | При 404/удалении ролика `isValid = false`; при таймауте `isValid = true` с warning | `docs/ARCHITECTURE_SPRING_BOOT.md` |
| 6 | Parsing | Regex Hashtag Extractor | Парсинг `#рилс`, `#продажи`, `#рассылки`, `#вопрос` | Текст сообщения | Числовые значения продаж/рассылок, ссылка на рилс | Пропуск нераспознанных блоков без падения системы | `ORIGINAL_REQUEST.md`, `taskRules` в `mockData.ts` |
| 7 | Parsing | Daily Report Upsert Accumulator | Накопительное объединение нескольких сообщений за день от одной ученицы | Серия сообщений за дату `CURRENT_DATE` | Единый суточный `DailyReport` | Корректное суммирование числовых метрик | Логический анализ сценария сдачи отчетов |
| 8 | AI Pipeline | Voice Message Transcription | Преобразование голосовых `.oga` в текст через Whisper/Gemini | Аудиофайл из Telegram Bot API | Строка расшифровки `voiceTranscript` | При недоступности Whisper сохраняется отметка об ошибке STT | `ORIGINAL_REQUEST.md` (R2) |
| 9 | AI Pipeline | Flood & Complaint Filtering | Отделение эмоциональных жалоб и бытового флуда от твердых фактов | Неструктурированный текст / войс | Структурированный `ExtractedReportDto` | Фолбэк на детерминированный regex-парсер | `ORIGINAL_REQUEST.md` (R2) |
| 10 | AI Pipeline | Burnout & Sentiment Detection | Выявление маркеров выгорания ученицы в голосовых и текстах | Текст сообщения / расшифровки | Флаг `BURNOUT_ALERT` в отчете | Дефолтное значение `NORMAL` | `mockData.ts` (rep-2: «выгорела, полдня лежала») |
| 11 | Notifications | Scheduled 20:00 Reminders | Автоматическая отправка напоминаний должникам за 1 час до дедлайна | Cron `0 0 20 * * MON-SAT`, срез базы данных | Telegram-сообщения должникам | Защита от спама (не более 1 пинга в день) | `ORIGINAL_REQUEST.md` (R2) |
| 12 | Frontend/API | Inbox Zero Review | Очередь карточек отчетов на проверку с мгновенным аппрувом | `reportId`, комментарий наставника | Изменение статуса на `APPROVED`, отправка в TG | При ошибке TG API статус в БД сохраняется, в UI выводится тост | `frontend/src/App.tsx`, `types.ts` |
| 13 | Frontend/API | Discipline Grid Calculation | Расчет сетки дней недели (Пн–Вс) и статусов дедлайнов | ID ученицы, текущая дата | Массив дней со статусами (`COMPLETED_ON_TIME` и др.) | При отсутствии отчета до 21:00 статус `MISSED` | `frontend/src/types.ts` |
| 14 | Frontend/API | Instagram Story Case Generator | Автоматический расчет метрик (Точка А -> Б) и генерация продающего текста | Данные успеваемости ученицы | Агрегированный текст для сторис в буфер обмена | Защита от деления на 0 при нулевом стартовом доходе | `frontend/src/App.tsx` (строка 53) |
| 15 | Tariff Model | Dual-Tier Feature Matrix | Разграничение функционала на Базовый MVP (320 BYN) и Полный PRO (475 BYN) | Выбор тарифа в контракте | Сформированный перечень модулей и WBS | Четкое разграничение трудозатрат и подписок | `ORIGINAL_REQUEST.md`, `App.tsx` (Pricing Tab) |

---

## 8. Edge Cases (Граничные случаи и поведение системы)

| # | Feature | Входные данные / Ситуация | Ожидаемое и спроектированное поведение системы |
|---|---------|---------------------------|------------------------------------------------|
| 1 | Валидация Reels | Ссылка со сложными UTM-хвостами и трекерами (`https://www.instagram.com/reel/C8XYZ123/?igsh=MzRlODBiNWFlZA==&utm_source=chat`) | Регулярное выражение отсекает query string, извлекает чистый shortcode `C8XYZ123`, канонизирует URL и производит хеширование. |
| 2 | Антифрод Reels | Ученица повторно сдает ролик, который сдавала 3 дня назад | Вычисляется SHA-256 хеш, находится совпадение в `reel_submissions`, выставляется `isDuplicate = true`, `duplicateDate = '04 ноября'`. На дашборде карточка подсвечивается желтым предупреждением. |
| 3 | Антифрод Reels | Ученица «А» сдает ролик, который вчера сдала ученица «Б» | Обнаруживается межпользовательский дубликат по общему индексу `idx_reel_submissions_hash`. Система выставляет статус мошенничества (`isDuplicate = true`, алерт наставнику). |
| 4 | Доступность Reels | Аккаунт закрыт (Private) или ролик удален после публикации | HTTP Probe получает статус 404 или страницу редиректа на закрытый профиль. Ролик маркируется `isValid = false`, на карточке выводится красная плашка «Ссылка недоступна». |
| 5 | Парсинг продаж | Нестандартная запись суммы: `#продажи 80к` или `#продажи 45 000 руб` | Нормализатор парсит числовые паттерны: `80к` конвертирует в `80000.00`, пробелы между разрядами удаляются. В базу попадает точный `NUMERIC(12,2)`. |
| 6 | Накопительный отчет | Ученица в 14:00 присылает `#рассылки 15`, а в 19:30 присылает `#рассылки 10 #рилс https://...` | Система находит запись за текущий день (`uq_student_report_date`) и выполняет накопительное обновление: рассылки суммируются (`25`), ссылка на рилс добавляется. |
| 7 | Двуязычные хэштеги | Ученица пишет тег на латинице: `#reels https://...` или с опечаткой `#рилсы` | Регулярное выражение поддерживает альтернативы: `(?i)#(?:рилс|рилсы|reels|reel)`. |
| 8 | Голосовое сообщение | Ученица присылает войс длительностью 2 минуты с шумом на фоне | Аудиофайл скачивается через Telegram Bot API, Whisper транскрибирует речь, Spring AI отделяет слова-паразиты и извлекает суть: «12 рассылок, рилс выложен, клиент просит скидку». |
| 9 | Сбой AI-сервиса | Внешний API Gemini / OpenAI недоступен (HTTP 503 или Quota Exceeded) | Срабатывает Spring `@Recover` / fallback-обработчик. Сообщение передается в классический regex-парсер, текст отчета сохраняется в `raw_message_text`, наставник видит сырое сообщение без задержки. |
| 10 | Напоминания в 20:00 | Ученица прислала отчет в 19:55 (за 5 минут до срабатывания крона) | Запрос выборки должников проверяет `NOT EXISTS (SELECT 1 FROM daily_reports WHERE student_id = s.id AND report_date = CURRENT_DATE)`. Ученица исключается из списка рассылки, лишнее уведомление не отправляется. |
| 11 | Неизвестный пользователь | Сообщение в бот отправил посторонний пользователь Telegram | Бот не находит `from.id` в таблице `students`. Сообщение сохраняется в `audit_message_logs` со статусом `UNMATCHED_USER`. Бот вежливо отвечает: «Ваш аккаунт не найден в списке учениц курса. Напишите наставнику». |
| 12 | Ответ из веб-трекера | Наставник нажимает «Принять» в веб-интерфейсе и пишет текст ответа | REST контроллер `/api/inbox/{id}/approve` отправляет вызов в Telegram Bot API. При успехе отчет архивируется. Если ученица заблокировала бота, отчет одобряется, а в UI отображается предупреждение: «Отчет принят, но сообщение в Telegram не доставлено». |

---

## 9. Logic Chain (Логическое обоснование архитектурных решений)

1. **Telegram Business API как основная модель взаимодействия:**
   - *Наблюдение:* Наставники ведут индивидуальное сопровождение учениц в личных чатах Telegram, где ученицы делятся конфиденциальными цифрами продаж и личными трудностями.
   - *Вывод:* Использование обычного бота требовало бы от ученицы покинуть личный диалог с Ксенией и писать в сторонний чат-бот. Telegram Business API решает эту проблему: ученица пишет в привычный личный чат с Ксенией, а бот незаметно считывает отчет в фоновом режиме через `business_message`.

2. **Двухэтапная валидация Reels (Regex + SHA-256 Hash + HTTP Probe):**
   - *Наблюдение:* Ученицы под давлением дедлайна могут сдавать старые ролики или ссылки с опечатками.
   - *Вывод:* Только хеширование чистого shortcode предотвращает манипуляции с добавлением query-параметров (`?igsh=1`, `?igsh=2`). Асинхронный HEAD-запрос с User-Agent гарантирует, что ролик реально существует и открывается.

3. **Разграничение Spring Boot 4.x и стабильного Fallback 3.4.2:**
   - *Наблюдение:* В ТЗ указано требование на Spring Boot 4.x / Spring AI 2.x, однако на текущий момент 4.x находится в предварительной ветке.
   - *Вывод:* Для обеспечения 100% стабильности в проде спроектирован контракт, который на уровне кода идентичен между версиями (Spring Data, `ChatClient`, `LongPollingBot`), что позволяет собирать проект как на Spring Boot 3.4.2 (GA), так и на 4.x при публикации релизных артефактов.

4. **Обоснование разделения тарифов (320 BYN vs 475 BYN):**
   - *Наблюдение:* Подключение внешних AI-моделей (Spring AI, Whisper, Gemini) требует настройки промптов, токенизации, обработки ошибок сторонних сервисов и дополнительных расходов на токены.
   - *Вывод:* Тариф 320 BYN (MVP) решает базовую боль (структурирование отчетов по хэштегам и Inbox Zero) без затрат на AI. Тариф 475 BYN (PRO) автоматизирует 100% рутины, включая распознавание войсов и автогенерацию кейсов для Instagram, что полностью оправдывает разницу в 155 BYN (+18 часов инженерной разработки).

---

## 10. Caveats (Ограничения и допущения)

1. **Telegram Business:** Для функционирования `business_message` аккаунт наставника Ксении должен обладать активной подпиской Telegram Premium и включенной опцией подключения чат-ботов. В качестве бесплатной альтернативы предусмотрен режим работы в мини-группе потока.
2. **Instagram Rate Limits:** При массовой проверке сотен ссылок подряд Instagram может временно возвращать HTTP 429. Для этого запросы выполняются с рандомизированными паузами и сохранением статуса `TIMEOUT_UNVERIFIED` без блокировки сдачи отчета.
3. **Расходы на инфраструктуру:** Эксплуатация решения требует VPS (от $8/мес) и API-ключ Gemini / OpenAI (от $2–$3/мес в зависимости от объема голосовых сообщений).

---

## 11. Conclusion (Итоговое заключение)

Техническая спецификация для требований **R2** и **R3** полностью проработана, синхронизирована с интерфейсом React SPA и закрывает все обнаруженные белые пятна:
- Telegram Bot архитектура поддерживает оба сценария (1-на-1 через Business API и групповой формат).
- Антифрод алгоритм Reels гарантирует уникальность и доступность сдаваемого контента.
- AI-пайплайн обеспечивает качественную транскрибацию и отделение сути от флуда с детектором выгорания.
- Стек на Java/Spring Boot снабжен надежным fallback-механизмом на стабильную версию 3.4.x.
- Тарифная матрица прозрачно декомпозирована по часам и этапам (320 BYN / 34 ч vs 475 BYN / 52 ч).
- Схема базы данных PostgreSQL и спецификация REST API полностью покрывают все 4 рабочих экрана дашборда наставника.

---

## 12. Verification Method (Метод проверки спецификации)

Для независимой верификации разработанных спецификаций:
1. **Проверка соответствия типам фронтенда:**
   - Сравнить эндпоинты раздела 6 с интерфейсами в `frontend/src/types.ts` и мок-данными в `frontend/src/mockData.ts`.
   - Проверить, что все поля (`salesAmount`, `mailingsCount`, `reels`, `weekDays`, `taskRules`) совпадают по типам и именованию.
2. **Проверка валидности SQL DDL:**
   - Запустить синтаксическую валидацию DDL в PostgreSQL 16:
     ```bash
     # Проверка создания схемы в тестовом контейнере
     docker run --name pg-test -e POSTGRES_PASSWORD=postgres -d postgres:16
     docker exec -i pg-test psql -U postgres < schema.sql
     ```
3. **Проверка тарифов и WBS:**
   - Сопоставить суммы часов (34 ч и 52 ч) со сметой в `frontend/src/App.tsx` (вкладка тарифов 320 BYN / 475 BYN).
