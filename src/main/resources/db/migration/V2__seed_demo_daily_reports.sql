-- =============================================================================
-- MENTOR HUB — V2 DEMO WEEK DAILY REPORTS SEED MIGRATION
-- Seeds authentic daily_reports, content_drafts, reel_submissions, and sales_entries
-- for demo cohort week: 2026-11-02 (Mon) to 2026-11-07 (Sat).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. DAILY REPORTS (daily_reports)
-- -----------------------------------------------------------------------------

-- Student 1: Милана Горохова (id: 1)
-- Mon-Thu on time (APPROVED), Fri late (APPROVED), Sat on time (PENDING)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(1, '2026-11-02', '2026-11-02 18:24:00+03:00', TRUE, 'APPROVED', 20, 0.00, 1, 'Сдан черновик Reels #11, 20 рассылок, +1 клиент.'),
(1, '2026-11-03', '2026-11-03 19:15:00+03:00', TRUE, 'APPROVED', 25, 0.00, 0, 'Сдана карусель #5, 25 рассылок.'),
(1, '2026-11-04', '2026-11-04 18:40:00+03:00', TRUE, 'APPROVED', 20, 0.00, 1, 'Сдан сторителл #3, 20 рассылок, +1 клиент.'),
(1, '2026-11-05', '2026-11-05 19:50:00+03:00', TRUE, 'APPROVED', 25, 0.00, 0, 'Сдан черновик Reels #12, 25 рассылок.'),
(1, '2026-11-06', '2026-11-06 22:40:00+03:00', FALSE, 'APPROVED', 15, 0.00, 0, 'Сдано с опозданием в 22:40: Reels #13, 15 рассылок.');

-- Sat 07.11 for Milana (PENDING inbox card)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, mentor_question, reflection_emotions, reflection_actions, reflection_question, ai_summary, emotional_status)
VALUES
(1, '2026-11-07', '2026-11-07 18:24:00+03:00', TRUE, 'PENDING', 25, 45000.00, 2,
 'Ксения, клиентка хочет рассрочку на 3 месяца через банк, но у нее плохая кредитная история. Предложить внутреннюю рассрочку от себя или лучше не рисковать?',
 'Бодрое состояние, вдохновлена первой крупной продажей за неделю',
 'Отправлено 25 рассылок по скрипту #3, смонтирован черновик Reels и карусель на 5 слайдов',
 'Как лучше оформить внутреннюю рассрочку без банка?',
 'Сдан черновик Reels #14 и карусель на 5 слайдов. 25 рассылок в Директ, продажа 45 000 ₽, +2 новых клиента.',
 'NORMAL');

-- Student 2: Аня Петрова (id: 2)
-- Mon on time (APPROVED), Tue missed, Wed late (APPROVED), Thu on time (APPROVED), Fri missed, Sat late (PENDING)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(2, '2026-11-02', '2026-11-02 19:10:00+03:00', TRUE, 'APPROVED', 15, 0.00, 0, 'Сдан черновик Reels #6, 15 рассылок.'),
(2, '2026-11-04', '2026-11-04 23:15:00+03:00', FALSE, 'APPROVED', 12, 0.00, 0, 'Сдано в 23:15 (опоздание): карусель, 12 рассылок.'),
(2, '2026-11-05', '2026-11-05 20:30:00+03:00', TRUE, 'APPROVED', 15, 0.00, 1, 'Сдан сторителл, 15 рассылок, +1 клиент.');

-- Sat 07.11 for Anya (PENDING inbox card with burnout alert & duplicate reel)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, voice_transcript, reflection_emotions, reflection_actions, reflection_question, ai_summary, emotional_status)
VALUES
(2, '2026-11-07', '2026-11-07 21:15:00+03:00', FALSE, 'PENDING', 12, 0.00, 0,
 'Ксюш, я сегодня совсем выгорела, полдня лежала. Смонтировала рилс из старых исходников, но завтра соберусь и нагоню план.',
 'Сильный спад энергии и выгорание, тяжело выходить на связь после отказов',
 '12 рассылок, черновой перемонтаж старых исходников видео',
 'Как преодолеть ступор перед новыми контактами?',
 'Обнаружен дубликат черновика Reels от 04 ноября! Продаж нет, 12 рассылок. Сигнал выгорания в голосовой рефлексии.',
 'BURNOUT_ALERT');

-- Student 3: Карина Волкова (id: 3)
-- Mon-Fri on time (APPROVED), Sat on time (PENDING)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(3, '2026-11-02', '2026-11-02 19:00:00+03:00', TRUE, 'APPROVED', 30, 0.00, 2, 'Reels #15, 30 рассылок, +2 клиента.'),
(3, '2026-11-03', '2026-11-03 19:20:00+03:00', TRUE, 'APPROVED', 30, 0.00, 0, 'Карусель #9, 30 рассылок.'),
(3, '2026-11-04', '2026-11-04 18:50:00+03:00', TRUE, 'APPROVED', 30, 0.00, 1, 'Сторителл #10, 30 рассылок, +1 клиент.'),
(3, '2026-11-05', '2026-11-05 19:40:00+03:00', TRUE, 'APPROVED', 30, 0.00, 0, 'Reels #16, 30 рассылок.'),
(3, '2026-11-06', '2026-11-06 18:30:00+03:00', TRUE, 'APPROVED', 30, 0.00, 2, 'Карусель #10, 30 рассылок, +2 клиента.');

-- Sat 07.11 for Karina (PENDING inbox card)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, mentor_question, reflection_emotions, reflection_actions, reflection_question, ai_summary, emotional_status)
VALUES
(3, '2026-11-07', '2026-11-07 19:40:00+03:00', TRUE, 'PENDING', 30, 80000.00, 3,
 'Клиент внес предоплату 50%. Куда лучше прикрепить договор оферты — в чат или на почту отправить?',
 'Максимальный фокус, уверенность в скрипте закрытия и высоком чеке',
 '30 рассылок, оформлен черновик сторителлинга и кейс-карусель',
 'Куда прикрепить договор оферты клиенту?',
 'Закрыта крупная сделка на 80 000 ₽ (наставничество). Отправлено 30 рассылок по новой базе, +3 новых клиента.',
 'NORMAL');

-- Student 4: Виктория Лебедева (id: 4)
-- Mon-Wed on time (APPROVED), Thu late (APPROVED), Fri-Sat PAUSED (no reports)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(4, '2026-11-02', '2026-11-02 19:10:00+03:00', TRUE, 'APPROVED', 20, 0.00, 0, 'Reels #10, 20 рассылок.'),
(4, '2026-11-03', '2026-11-03 19:30:00+03:00', TRUE, 'APPROVED', 25, 0.00, 1, 'Карусель #4, 25 рассылок, +1 клиент.'),
(4, '2026-11-04', '2026-11-04 18:50:00+03:00', TRUE, 'APPROVED', 20, 0.00, 0, 'Сторителл #6, 20 рассылок.'),
(4, '2026-11-05', '2026-11-05 22:10:00+03:00', FALSE, 'APPROVED', 20, 0.00, 0, 'Сдано в 22:10 (опоздание): Reels #11, 20 рассылок.');

-- Student 5: Полина Соколова (id: 5)
-- Mon-Fri on time (APPROVED), Sat late (APPROVED)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(5, '2026-11-02', '2026-11-02 19:00:00+03:00', TRUE, 'APPROVED', 25, 0.00, 1, 'Reels #13, 25 рассылок, +1 клиент.'),
(5, '2026-11-03', '2026-11-03 19:25:00+03:00', TRUE, 'APPROVED', 25, 0.00, 0, 'Карусель #7, 25 рассылок.'),
(5, '2026-11-04', '2026-11-04 18:40:00+03:00', TRUE, 'APPROVED', 25, 0.00, 1, 'Сторителл #8, 25 рассылок, +1 клиент.'),
(5, '2026-11-05', '2026-11-05 19:15:00+03:00', TRUE, 'APPROVED', 25, 0.00, 0, 'Reels #14, 25 рассылок.'),
(5, '2026-11-06', '2026-11-06 18:50:00+03:00', TRUE, 'APPROVED', 30, 0.00, 2, 'Карусель #8, 30 рассылок, +2 клиента.'),
(5, '2026-11-07', '2026-11-07 21:50:00+03:00', FALSE, 'APPROVED', 30, 0.00, 2, 'Сдано в 21:50: сторителл, 30 рассылок, +2 клиента.');

-- -----------------------------------------------------------------------------
-- 2. CONTENT DRAFTS (content_drafts)
-- -----------------------------------------------------------------------------
-- Milana (Sat 07.11)
INSERT INTO content_drafts (report_id, draft_type, label, url, is_duplicate)
SELECT id, 'REEL', 'Reels #14 — продающий хук для стилиста', 'https://instagram.com/reel/DC194kaP91m', FALSE
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

INSERT INTO content_drafts (report_id, draft_type, label, is_duplicate)
SELECT id, 'CAROUSEL', 'Карусель: 5 ошибок при подборе капсулы (PDF)', FALSE
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

-- Anya (Sat 07.11 — duplicate reel)
INSERT INTO content_drafts (report_id, draft_type, label, url, is_duplicate, duplicate_date)
SELECT id, 'REEL', 'Reels #8 — разбор страха продаж', 'https://instagram.com/reel/DB890aaXYZ1', TRUE, '04 ноября'
FROM daily_reports WHERE student_id = 2 AND report_date = '2026-11-07';

-- Karina (Sat 07.11)
INSERT INTO content_drafts (report_id, draft_type, label, is_duplicate)
SELECT id, 'STORYTELL', 'Сторителл: Прогрев на высокий чек (12 историй)', FALSE
FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-07';

INSERT INTO content_drafts (report_id, draft_type, label, is_duplicate)
SELECT id, 'CAROUSEL', 'Карусель: Кейс ученицы Марии (+120 000 ₽)', FALSE
FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-07';

-- Weekday Drafts for Tooltip Rendering
-- Student 1: Milana
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #11' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-02';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'CAROUSEL', 'Карусель #5' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-03';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'STORYTELL', 'Сторителл #3' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-04';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #12' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-05';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #13' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-06';

-- Student 2: Anya
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #6' FROM daily_reports WHERE student_id = 2 AND report_date = '2026-11-02';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'CAROUSEL', 'Карусель' FROM daily_reports WHERE student_id = 2 AND report_date = '2026-11-04';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'STORYTELL', 'Сторителл' FROM daily_reports WHERE student_id = 2 AND report_date = '2026-11-05';

-- Student 3: Karina
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #15' FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-02';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'CAROUSEL', 'Карусель #9' FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-03';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'STORYTELL', 'Сторителл #10' FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-04';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #16' FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-05';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'CAROUSEL', 'Карусель #10' FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-06';

-- Student 4: Victoria
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #10' FROM daily_reports WHERE student_id = 4 AND report_date = '2026-11-02';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'CAROUSEL', 'Карусель #4' FROM daily_reports WHERE student_id = 4 AND report_date = '2026-11-03';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'STORYTELL', 'Сторителл #6' FROM daily_reports WHERE student_id = 4 AND report_date = '2026-11-04';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #11' FROM daily_reports WHERE student_id = 4 AND report_date = '2026-11-05';

-- Student 5: Polina
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #13' FROM daily_reports WHERE student_id = 5 AND report_date = '2026-11-02';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'CAROUSEL', 'Карусель #7' FROM daily_reports WHERE student_id = 5 AND report_date = '2026-11-03';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'STORYTELL', 'Сторителл #8' FROM daily_reports WHERE student_id = 5 AND report_date = '2026-11-04';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #14' FROM daily_reports WHERE student_id = 5 AND report_date = '2026-11-05';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'CAROUSEL', 'Карусель #8' FROM daily_reports WHERE student_id = 5 AND report_date = '2026-11-06';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'STORYTELL', 'Сторителл' FROM daily_reports WHERE student_id = 5 AND report_date = '2026-11-07';

-- -----------------------------------------------------------------------------
-- 3. REEL SUBMISSIONS (reel_submissions)
-- -----------------------------------------------------------------------------
INSERT INTO reel_submissions (report_id, reel_url, canonical_url, shortcode, reel_hash, is_valid, is_duplicate)
SELECT id, 'https://instagram.com/reel/DC194kaP91m', 'https://instagram.com/reel/DC194kaP91m', 'DC194kaP91m',
       'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', TRUE, FALSE
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

INSERT INTO reel_submissions (report_id, reel_url, canonical_url, shortcode, reel_hash, is_valid, is_duplicate)
SELECT id, 'https://instagram.com/reel/DC201zzL40a', 'https://instagram.com/reel/DC201zzL40a', 'DC201zzL40a',
       '7c222fb2927d828af22f592134e8932480637c0d', TRUE, FALSE
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

INSERT INTO reel_submissions (report_id, reel_url, canonical_url, shortcode, reel_hash, is_valid, is_duplicate, duplicate_date)
SELECT id, 'https://instagram.com/reel/DB890aaXYZ1', 'https://instagram.com/reel/DB890aaXYZ1', 'DB890aaXYZ1',
       'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e', TRUE, TRUE, '04 ноября'
FROM daily_reports WHERE student_id = 2 AND report_date = '2026-11-07';

INSERT INTO reel_submissions (report_id, reel_url, canonical_url, shortcode, reel_hash, is_valid, is_duplicate)
SELECT id, 'https://instagram.com/reel/DC551bbK22q', 'https://instagram.com/reel/DC551bbK22q', 'DC551bbK22q',
       'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', TRUE, FALSE
FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-07';

-- -----------------------------------------------------------------------------
-- 4. SALES ENTRIES (sales_entries)
-- -----------------------------------------------------------------------------
INSERT INTO sales_entries (report_id, student_id, amount, description)
SELECT id, 1, 45000.00, 'Продажа пакета консультаций'
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

INSERT INTO sales_entries (report_id, student_id, amount, description)
SELECT id, 3, 80000.00, 'Наставничество (предоплата 50%)'
FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-07';
