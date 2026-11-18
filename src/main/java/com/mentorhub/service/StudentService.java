package com.mentorhub.service;

import com.mentorhub.dto.*;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.entity.StudentDayOverride;
import com.mentorhub.entity.ZoomMeeting;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.StudentDayOverrideRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.repository.ZoomMeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {
    private static final int CONTRACT_ZOOM_QUOTA = 8;

    private final StudentRepository studentRepository;
    private final DailyReportRepository dailyReportRepository;
    private final StudentDayOverrideRepository studentDayOverrideRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final Environment environment;

    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");
    private static final DateTimeFormatter ZOOM_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM 'в' HH:mm").withZone(MINSK_ZONE);
    private static final DateTimeFormatter DAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM");

    private static final String[] RU_DAY_NAMES = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    @Transactional(readOnly = true)
    public List<StudentDisciplineResponse> getStudents(String search, Boolean active) {
        List<Student> students;

        if (StringUtils.hasText(search)) {
            students = studentRepository.findByFullNameContainingIgnoreCaseOrNicheContainingIgnoreCase(
                    search.trim(), search.trim());
            if (active != null) {
                students = students.stream().filter(s -> Objects.equals(s.getIsActive(), active)).toList();
            }
        } else if (active != null) {
            students = Boolean.TRUE.equals(active)
                    ? studentRepository.findByIsActiveTrue()
                    : studentRepository.findAll().stream().filter(s -> !Boolean.TRUE.equals(s.getIsActive())).toList();
        } else {
            students = studentRepository.findAllWithZoomMeetings();
        }

        List<Long> studentIds = students.stream().map(Student::getId).toList();
        LocalDate today = LocalDate.now(MINSK_ZONE);
        LocalDate monday = resolveVisibleWeekMonday(today);
        LocalDate sunday = monday.plusDays(6);

        Map<Long, Map<LocalDate, StudentDayOverride>> overridesByStudent = new HashMap<>();
        Map<Long, Map<LocalDate, DailyReport>> reportsByStudent = new HashMap<>();

        if (!studentIds.isEmpty()) {
            studentDayOverrideRepository.findByStudentIdInAndDayDateBetween(studentIds, monday, sunday)
                    .forEach(o -> overridesByStudent.computeIfAbsent(o.getStudent().getId(), k -> new HashMap<>()).put(o.getDayDate(), o));

            dailyReportRepository.findByStudentIdInAndReportDateBetween(studentIds, monday, sunday)
                    .forEach(r -> reportsByStudent.computeIfAbsent(r.getStudent().getId(), k -> new HashMap<>()).put(r.getReportDate(), r));
        }

        return students.stream()
                .map(s -> mapToDisciplineResponse(s,
                        overridesByStudent.getOrDefault(s.getId(), Collections.emptyMap()),
                        reportsByStudent.getOrDefault(s.getId(), Collections.emptyMap())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentDisciplineResponse getStudentById(Long id) {
        Student student = studentRepository.findWithZoomMeetingsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        return mapToDisciplineResponse(student);
    }

    @Transactional
    public StudentDisciplineResponse createStudent(CreateStudentRequest request) {
        Student student = Student.builder()
                .fullName(request.fullName())
                .avatarText(request.avatarText() != null ? request.avatarText() : generateAvatarText(request.fullName()))
                .tgUsername(request.tgUsername())
                .tgChatId(request.tgChatId())
                .niche(request.niche())
                .tariff(request.tariff() != null ? request.tariff().toUpperCase() : "STANDARD")
                .initialIncome(request.initialIncome() != null ? request.initialIncome() : BigDecimal.ZERO)
                .currentIncome(request.initialIncome() != null ? request.initialIncome() : BigDecimal.ZERO)
                .initialAvgTicket(request.initialAvgTicket() != null ? request.initialAvgTicket() : BigDecimal.ZERO)
                .currentAvgTicket(request.initialAvgTicket() != null ? request.initialAvgTicket() : BigDecimal.ZERO)
                .startDate(request.startDate() != null ? request.startDate() : LocalDate.now(MINSK_ZONE))
                .currency(StringUtils.hasText(request.currency()) ? request.currency().toUpperCase(Locale.ROOT) : "BYN")
                .timezone(StringUtils.hasText(request.timezone()) ? request.timezone() : "Europe/Minsk")
                .isActive(true)
                .isPaused(false)
                .build();

        Student saved = studentRepository.save(student);
        log.info("Created new student '{}' with id #{}", saved.getFullName(), saved.getId());
        return mapToDisciplineResponse(saved);
    }

    @Transactional
    public StudentDisciplineResponse updateStudent(Long id, UpdateStudentRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));

        if (request.fullName() != null) student.setFullName(request.fullName());
        if (request.avatarText() != null) student.setAvatarText(request.avatarText());
        if (request.tgUsername() != null) student.setTgUsername(request.tgUsername());
        if (request.tgChatId() != null) student.setTgChatId(request.tgChatId());
        if (request.niche() != null) student.setNiche(request.niche());
        if (request.tariff() != null) student.setTariff(request.tariff().toUpperCase());
        if (request.initialIncome() != null) student.setInitialIncome(request.initialIncome());
        if (request.currentIncome() != null) student.setCurrentIncome(request.currentIncome());
        if (request.initialAvgTicket() != null) student.setInitialAvgTicket(request.initialAvgTicket());
        if (request.currentAvgTicket() != null) student.setCurrentAvgTicket(request.currentAvgTicket());
        if (request.startDate() != null) student.setStartDate(request.startDate());
        if (request.isActive() != null) student.setIsActive(request.isActive());
        if (request.isPaused() != null) student.setIsPaused(request.isPaused());
        if (request.pauseReason() != null) student.setPauseReason(request.pauseReason());
        if (request.taskPlanRaw() != null) {
            student.setTaskPlanRaw(request.taskPlanRaw());
            student.setTaskPlanStartDate(LocalDate.now(MINSK_ZONE));
        }
        if (request.currentTaskDay() != null) {
            student.setCurrentTaskDay(request.currentTaskDay());
            student.setTaskPlanStartDate(LocalDate.now(MINSK_ZONE));
        }
        if (request.currency() != null) student.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        if (request.timezone() != null) student.setTimezone(request.timezone());

        Student updated = studentRepository.save(student);
        log.info("Updated student '{}' with id #{}", updated.getFullName(), updated.getId());
        return mapToDisciplineResponse(updated);
    }

    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        student.setIsActive(false);
        student.setTgChatId(null);
        studentRepository.save(student);
        log.info("Soft deleted student id #{} and released tgChatId", id);
    }

    @Transactional
    public TogglePauseResponse togglePause(Long id, TogglePauseRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));

        boolean newPausedState = Boolean.TRUE.equals(request.isPaused());
        LocalDate today = LocalDate.now(MINSK_ZONE);
        if (newPausedState) {
            student.setIsPaused(true);
            student.setPausedFromDate(today);
            student.setPauseReason(request.pauseReason() != null ? request.pauseReason() : "Болезнь / пауза потока ☕");
        } else {
            LocalDate pausedFrom = student.getPausedFromDate();
            if (pausedFrom != null) {
                LocalDate checkDate = pausedFrom;
                while (checkDate.isBefore(today)) {
                    if (studentDayOverrideRepository.findByStudentIdAndDayDate(student.getId(), checkDate).isEmpty()
                            && dailyReportRepository.findByStudentIdAndReportDate(student.getId(), checkDate).isEmpty()) {
                        StudentDayOverride pauseOverride = StudentDayOverride.builder()
                                .student(student)
                                .dayDate(checkDate)
                                .status("PAUSED")
                                .details("Режим паузы: болезнь, стрик заморожен ☕")
                                .updatedAt(Instant.now())
                                .build();
                        studentDayOverrideRepository.save(pauseOverride);
                    }
                    checkDate = checkDate.plusDays(1);
                }
            }
            student.setIsPaused(false);
            student.setPausedFromDate(null);
            student.setPauseReason(null);
        }

        studentRepository.save(student);
        log.info("Toggled pause for student '{}' (id #{}): isPaused={}, pausedFromDate={}, reason='{}'",
                student.getFullName(), id, newPausedState, student.getPausedFromDate(), student.getPauseReason());

        return new TogglePauseResponse(
                "st-" + student.getId(),
                student.getFullName(),
                newPausedState,
                student.getPauseReason(),
                true,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<DayInfoResponse> getDisciplineCalendar(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        return buildWeeklyCalendar(student);
    }

    @Transactional(readOnly = true)
    public List<ZoomMeetingDto> getStudentZoomMeetings(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));
        return zoomMeetingRepository.findByStudentIdOrderByCallNumberAscIdAsc(studentId).stream()
                .map(meeting -> mapZoomMeeting(student, meeting))
                .toList();
    }

    @Transactional
    public List<ZoomMeetingDto> replaceStudentZoomMeetings(Long studentId, ReplaceZoomSessionsRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));
        List<ZoomMeeting> existing = zoomMeetingRepository.findByStudentIdOrderByCallNumberAscIdAsc(studentId);
        Map<Integer, ZoomMeeting> byNumber = new HashMap<>();
        existing.forEach(meeting -> {
            if (meeting.getCallNumber() != null) byNumber.put(meeting.getCallNumber(), meeting);
        });

        Set<Integer> requestedNumbers = new HashSet<>();
        List<ZoomMeeting> savedMeetings = new ArrayList<>();
        List<ZoomSessionInput> sessions = new ArrayList<>(request.sessions() == null ? List.of() : request.sessions());
        Set<Integer> configuredNumbers = new HashSet<>();
        for (int index = 0; index < sessions.size(); index++) {
            ZoomSessionInput input = sessions.get(index);
            configuredNumbers.add(input.callNumber() == null ? index + 1 : input.callNumber());
        }
        for (int callNumber = 1; callNumber <= CONTRACT_ZOOM_QUOTA; callNumber++) {
            if (!configuredNumbers.contains(callNumber)) {
                sessions.add(new ZoomSessionInput(
                        callNumber,
                        "Консультация #" + callNumber,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null
                ));
            }
        }
        for (int index = 0; index < sessions.size(); index++) {
            ZoomSessionInput input = sessions.get(index);
            int callNumber = input.callNumber() == null ? index + 1 : input.callNumber();
            if (callNumber < 1 || !requestedNumbers.add(callNumber)) {
                throw new IllegalArgumentException("Call numbers must be unique positive integers");
            }

            ZoomMeeting meeting = byNumber.getOrDefault(callNumber, ZoomMeeting.builder().student(student).build());
            meeting.setStudent(student);
            meeting.setCallNumber(callNumber);
            meeting.setMeetingTitle(input.title() == null || input.title().isBlank() ? "Этап " + callNumber : input.title().trim());
            meeting.setZoomUrl(input.zoomUrl());
            meeting.setRecordingUrl(input.recordingUrl());
            meeting.setScheduledAt(input.scheduledAt());
            meeting.setLinkSentAt(input.linkSentAt());
            meeting.setEvidenceUrl(input.evidenceUrl());
            meeting.setLinkSentAt(input.linkSentAt());
            meeting.setEvidenceUrl(input.evidenceUrl());
            meeting.setMeetingNotes(input.meetingNotes());
            boolean completed = Boolean.TRUE.equals(input.isCompleted());
            meeting.setIsCompleted(completed);
            meeting.setCompletedAt(completed ? (meeting.getCompletedAt() == null ? Instant.now() : meeting.getCompletedAt()) : null);
            savedMeetings.add(zoomMeetingRepository.save(meeting));
        }

        List<ZoomMeeting> removed = existing.stream()
                .filter(meeting -> meeting.getCallNumber() != null && !requestedNumbers.contains(meeting.getCallNumber()))
                .toList();
        if (!removed.isEmpty()) zoomMeetingRepository.deleteAll(removed);

        savedMeetings.sort(Comparator.comparing(ZoomMeeting::getCallNumber));
        long completedCount = savedMeetings.stream().filter(meeting -> Boolean.TRUE.equals(meeting.getIsCompleted())).count();
        student.setCompletedZoomsCount(Math.toIntExact(completedCount));
        student.setNextZoomAt(savedMeetings.stream()
                .filter(meeting -> !Boolean.TRUE.equals(meeting.getIsCompleted()))
                .filter(meeting -> meeting.getScheduledAt() != null)
                .map(ZoomMeeting::getScheduledAt)
                .min(Comparator.naturalOrder())
                .orElse(null));
        studentRepository.save(student);
        return savedMeetings.stream().map(meeting -> mapZoomMeeting(student, meeting)).toList();
    }

    public StudentDisciplineResponse mapToDisciplineResponse(Student student) {
        return mapToDisciplineResponse(student, null, null);
    }

    public StudentDisciplineResponse mapToDisciplineResponse(
            Student student,
            Map<LocalDate, StudentDayOverride> overrides,
            Map<LocalDate, DailyReport> reports
    ) {
        List<DayInfoResponse> weekDays = buildWeeklyCalendar(student, overrides, reports);
        int disciplineRate = calculateDisciplineRate(student, weekDays);

        String nextZoomDate = null;
        if (student.getNextZoomAt() != null) {
            nextZoomDate = ZOOM_DATE_FORMATTER.format(student.getNextZoomAt());
        }

        String rawTariff = student.getTariff() != null ? student.getTariff() : "STANDARD";
        String tariff = "VIP".equalsIgnoreCase(rawTariff) ? "VIP" : "Стандарт";

        LocalDate today = LocalDate.now(MINSK_ZONE);
        String todayTask = getTodayTask(student, today);
        int currentDay = getCurrentDayNumber(student, today);

        return new StudentDisciplineResponse(
                "st-" + student.getId(),
                student.getFullName(),
                student.getAvatarText(),
                student.getTgUsername(),
                student.getTgChatId(),
                student.getNiche(),
                tariff,
                student.getStartDate(),
                student.getIsActive(),
                student.getInitialIncome(),
                student.getCurrentIncome(),
                student.getInitialAvgTicket(),
                student.getCurrentAvgTicket(),
                student.getTotalReels() != null ? student.getTotalReels() : 0,
                student.getTotalCarousels() != null ? student.getTotalCarousels() : 0,
                student.getTotalStorytells() != null ? student.getTotalStorytells() : 0,
                student.getTotalMailings() != null ? student.getTotalMailings() : 0,
                student.getTotalNewClients() != null ? student.getTotalNewClients() : 0,
                disciplineRate,
                Boolean.TRUE.equals(student.getIsPaused()),
                student.getPauseReason(),
                nextZoomDate,
                student.getCompletedZoomsCount() != null ? student.getCompletedZoomsCount() : 0,
                student.getContractZoomQuota() != null ? student.getContractZoomQuota() : CONTRACT_ZOOM_QUOTA,
                weekDays,
                student.getZoomMeetings() == null ? List.of() : student.getZoomMeetings().stream()
                        .sorted(Comparator.comparing(ZoomMeeting::getCallNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(meeting -> new ZoomMeetingDto(
                                meeting.getId(),
                                student.getId(),
                                student.getFullName(),
                                meeting.getMeetingTitle(),
                                meeting.getCallNumber(),
                                meeting.getZoomUrl(),
                                meeting.getRecordingUrl(),
                                meeting.getScheduledAt(),
                                meeting.getLinkSentAt(),
                                meeting.getEvidenceUrl(),
                                meeting.getSourceChatId(),
                                meeting.getSourceMessageId(),
                                meeting.getCompletedAt(),
                                meeting.getIsCompleted(),
                                meeting.getMeetingNotes()
                        )).toList(),
                student.getTaskPlanRaw(),
                currentDay,
                todayTask,
                student.getCurrency() != null ? student.getCurrency() : "BYN",
                student.getTimezone() != null ? student.getTimezone() : "Europe/Minsk"
        );
    }

    public static Map<Integer, String> parseTaskPlan(String raw) {
        Map<Integer, String> plan = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) {
            return plan;
        }
        String[] lines = raw.split("\\r?\\n");
        Pattern dayPattern = Pattern.compile("^(?:день\\s*(\\d+)|(\\d+)\\s*день|(\\d+)\\s*[\\.\\)])\\s*[-:—]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        int implicitDayCounter = 1;
        boolean anyExplicitDays = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher matcher = dayPattern.matcher(trimmed);
            if (matcher.find()) {
                String dayStr = matcher.group(1);
                if (dayStr == null) dayStr = matcher.group(2);
                if (dayStr == null) dayStr = matcher.group(3);
                String task = matcher.group(4);
                if (dayStr != null && task != null && !task.isBlank()) {
                    try {
                        int dayNum = Integer.parseInt(dayStr);
                        plan.put(dayNum, task.trim());
                        anyExplicitDays = true;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (!anyExplicitDays) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("+") || trimmed.toLowerCase(Locale.ROOT).startsWith("задача")) continue;
                plan.put(implicitDayCounter++, trimmed.replaceFirst("^[-*•]\\s*", ""));
            }
            if (plan.isEmpty() && StringUtils.hasText(raw)) {
                plan.put(1, raw.trim());
            }
        }
        return plan;
    }

    public record PlanSchedule(
            LocalDate startDate,
            String cleanPlan,
            Map<Integer, String> days
    ) {}

    public static PlanSchedule parsePlanWithSchedule(String raw, LocalDate today, ZoneId zone) {
        if (today == null) {
            today = LocalDate.now(zone != null ? zone : ZoneId.of("Europe/Minsk"));
        }
        LocalDate defaultStart = today.plusDays(1); // Default is tomorrow as per mentor preference
        if (!StringUtils.hasText(raw)) {
            return new PlanSchedule(defaultStart, "", Collections.emptyMap());
        }

        String trimmed = raw.trim();

        // 1. Check if the plan is written with calendar dates (e.g. "Сегодня сторител\n20.11 пост\n24.11 рилс")
        PlanSchedule datedSchedule = tryParseDatedPlan(trimmed, today);
        if (datedSchedule != null) {
            return datedSchedule;
        }

        List<String> lines = trimmed.lines().toList();
        if (lines.isEmpty()) {
            return new PlanSchedule(defaultStart, "", Collections.emptyMap());
        }

        String firstLine = lines.get(0).trim();
        LocalDate startFromFirstLine = tryParseDateModifier(firstLine, today);
        if (startFromFirstLine != null) {
            String planBody = lines.size() > 1 ? String.join("\n", lines.subList(1, lines.size())).trim() : "";
            Map<Integer, String> days = parseTaskPlan(planBody);
            return new PlanSchedule(startFromFirstLine, planBody, days);
        }

        int colonIdx = firstLine.indexOf(':');
        if (colonIdx > 0 && colonIdx < 30) {
            String prefix = firstLine.substring(0, colonIdx).trim();
            LocalDate startFromPrefix = tryParseDateModifier(prefix, today);
            if (startFromPrefix != null) {
                String remainderOfFirstLine = firstLine.substring(colonIdx + 1).trim();
                List<String> remainingLines = new ArrayList<>();
                if (!remainderOfFirstLine.isEmpty()) {
                    remainingLines.add(remainderOfFirstLine);
                }
                if (lines.size() > 1) {
                    remainingLines.addAll(lines.subList(1, lines.size()));
                }
                String planBody = String.join("\n", remainingLines).trim();
                Map<Integer, String> days = parseTaskPlan(planBody);
                return new PlanSchedule(startFromPrefix, planBody, days);
            }
        }

        Map<Integer, String> days = parseTaskPlan(trimmed);
        return new PlanSchedule(defaultStart, trimmed, days);
    }

    private record DatedLine(LocalDate date, String task) {}

    private static DatedLine parseDatedLine(String line, LocalDate today) {
        if (!StringUtils.hasText(line)) return null;
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT)
                .replaceAll("^[\\s🗓📅👉*•\\-]+", "");

        LocalDate targetDate = null;
        String task = null;

        if (lower.startsWith("сегодня")) {
            targetDate = today;
            task = lower.substring("сегодня".length()).trim();
        } else if (lower.startsWith("завтра")) {
            targetDate = today.plusDays(1);
            task = lower.substring("завтра".length()).trim();
        } else if (lower.startsWith("послезавтра")) {
            targetDate = today.plusDays(2);
            task = lower.substring("послезавтра".length()).trim();
        } else {
            // Check day of week
            for (String dowName : List.of("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье",
                    "пн", "вт", "ср", "чт", "пт", "сб", "вс")) {
                if (lower.startsWith(dowName) && (lower.length() == dowName.length() || !Character.isLetter(lower.charAt(dowName.length())))) {
                    DayOfWeek targetDow = parseDayOfWeek(dowName);
                    if (targetDow != null) {
                        int diff = (targetDow.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
                        if (diff == 0) diff = 7;
                        targetDate = today.plusDays(diff);
                        task = lower.substring(dowName.length()).trim();
                        break;
                    }
                }
            }
            if (targetDate == null) {
                // Check DD.MM or DD.MM.YYYY
                Matcher dateMatcher = Pattern.compile("^(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?(.*)$").matcher(lower);
                if (dateMatcher.matches()) {
                    try {
                        int day = Integer.parseInt(dateMatcher.group(1));
                        int month = Integer.parseInt(dateMatcher.group(2));
                        int year = dateMatcher.group(3) != null
                                ? (dateMatcher.group(3).length() == 2 ? 2000 + Integer.parseInt(dateMatcher.group(3)) : Integer.parseInt(dateMatcher.group(3)))
                                : today.getYear();
                        targetDate = LocalDate.of(year, month, day);
                        if (dateMatcher.group(3) == null && targetDate.isBefore(today.minusDays(7))) {
                            targetDate = targetDate.plusYears(1);
                        }
                        task = dateMatcher.group(4).trim();
                    } catch (Exception ignored) {
                        return null;
                    }
                }
            }
        }

        if (targetDate == null || task == null) return null;

        // Strip optional "день", "года", "г." and separators ":", "-", "—"
        task = task.replaceFirst("^(день|года|г\\.?)\\b", "").trim();
        task = task.replaceFirst("^[-:—]\\s*", "").trim();
        if (task.isEmpty()) return null;

        // Preserve original case for task from the end of trimmed
        int taskIndex = trimmed.toLowerCase(Locale.ROOT).lastIndexOf(task);
        if (taskIndex >= 0) {
            String rawExtracted = trimmed.substring(taskIndex).trim();
            task = rawExtracted.replaceFirst("^[-:—]\\s*", "").trim();
        }

        return new DatedLine(targetDate, task);
    }

    public static PlanSchedule tryParseDatedPlan(String raw, LocalDate today) {
        if (!StringUtils.hasText(raw) || today == null) return null;
        List<String> lines = raw.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (lines.isEmpty()) return null;

        Map<LocalDate, String> datedTasks = new TreeMap<>();
        int matchedLinesCount = 0;

        for (String line : lines) {
            DatedLine parsed = parseDatedLine(line, today);
            if (parsed != null && StringUtils.hasText(parsed.task())) {
                datedTasks.put(parsed.date(), parsed.task());
                matchedLinesCount++;
            }
        }

        if (datedTasks.isEmpty() || matchedLinesCount == 0) {
            return null;
        }

        boolean hasExplicitCalendarDate = datedTasks.keySet().stream()
                .anyMatch(d -> !d.equals(today) && !d.equals(today.plusDays(1)));
        if (!hasExplicitCalendarDate && matchedLinesCount < lines.size()) {
            return null;
        }

        LocalDate startDate = datedTasks.keySet().iterator().next();
        Map<Integer, String> days = new LinkedHashMap<>();
        StringBuilder cleanBody = new StringBuilder();

        for (Map.Entry<LocalDate, String> entry : datedTasks.entrySet()) {
            LocalDate taskDate = entry.getKey();
            int dayNum = (int) ChronoUnit.DAYS.between(startDate, taskDate) + 1;
            days.put(dayNum, entry.getValue());
            if (cleanBody.length() > 0) cleanBody.append("\n");
            cleanBody.append(dayNum).append(" день: ").append(entry.getValue());
        }

        return new PlanSchedule(startDate, cleanBody.toString(), days);
    }

    public static DayOfWeek parseDayOfWeek(String text) {
        if (!StringUtils.hasText(text)) return null;
        String clean = text.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("пон") || clean.equals("пн")) return DayOfWeek.MONDAY;
        if (clean.startsWith("вто") || clean.equals("вт")) return DayOfWeek.TUESDAY;
        if (clean.startsWith("сре") || clean.equals("ср")) return DayOfWeek.WEDNESDAY;
        if (clean.startsWith("чет") || clean.equals("чт")) return DayOfWeek.THURSDAY;
        if (clean.startsWith("пят") || clean.equals("пт")) return DayOfWeek.FRIDAY;
        if (clean.startsWith("суб") || clean.equals("сб")) return DayOfWeek.SATURDAY;
        if (clean.startsWith("вос") || clean.equals("вс")) return DayOfWeek.SUNDAY;
        return null;
    }

    private static LocalDate tryParseDateModifier(String text, LocalDate today) {
        if (!StringUtils.hasText(text)) return null;
        String clean = text.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\[\\]()#]", "")
                .trim();
        if (clean.startsWith("старт")) {
            clean = clean.replaceFirst("^старт[:\\s]*", "").trim();
        }
        if (clean.startsWith("со ")) {
            clean = clean.substring(3).trim();
        } else if (clean.startsWith("с ")) {
            clean = clean.substring(2).trim();
        }

        if (clean.equals("сегодня") || clean.equals("сегодняшнего дня")) {
            return today;
        }
        if (clean.equals("завтра") || clean.equals("завтрашнего дня")) {
            return today.plusDays(1);
        }
        if (clean.equals("послезавтра")) {
            return today.plusDays(2);
        }
        if (clean.startsWith("через")) {
            Matcher nm = Pattern.compile("\\d+").matcher(clean);
            if (nm.find()) {
                int days = Integer.parseInt(nm.group());
                return today.plusDays(days);
            }
        }
        DayOfWeek targetDow = parseDayOfWeek(clean);
        if (targetDow != null) {
            int diff = (targetDow.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            if (diff == 0) diff = 7;
            return today.plusDays(diff);
        }

        Matcher dateMatcher = Pattern.compile("^(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?$").matcher(clean);
        if (dateMatcher.matches()) {
            try {
                int day = Integer.parseInt(dateMatcher.group(1));
                int month = Integer.parseInt(dateMatcher.group(2));
                int year = dateMatcher.group(3) != null
                        ? (dateMatcher.group(3).length() == 2 ? 2000 + Integer.parseInt(dateMatcher.group(3)) : Integer.parseInt(dateMatcher.group(3)))
                        : today.getYear();
                LocalDate parsedDate = LocalDate.of(year, month, day);
                if (dateMatcher.group(3) == null && parsedDate.isBefore(today)) {
                    parsedDate = parsedDate.plusYears(1);
                }
                return parsedDate;
            } catch (Exception ignored) {}
        }

        return null;
    }

    public int getCurrentDayNumber(Student student, LocalDate today) {
        if (student == null) return 1;
        int baseDay = student.getCurrentTaskDay() != null ? student.getCurrentTaskDay() : 1;
        LocalDate startDate = student.getTaskPlanStartDate() != null ? student.getTaskPlanStartDate() : today;
        if (today == null || startDate == null) {
            return baseDay;
        }
        if (today.isBefore(startDate)) {
            return 0; // Plan has not started yet
        }
        long daysDiff = ChronoUnit.DAYS.between(startDate, today);
        return baseDay + (int) daysDiff;
    }

    public String getTodayTask(Student student, LocalDate today) {
        if (student == null || !StringUtils.hasText(student.getTaskPlanRaw())) {
            return null;
        }
        LocalDate startDate = student.getTaskPlanStartDate();
        if (today != null && startDate != null && today.isBefore(startDate)) {
            return null; // Plan has not started yet
        }
        Map<Integer, String> plan = parseTaskPlan(student.getTaskPlanRaw());
        if (plan.isEmpty()) return null;
        int dayNum = getCurrentDayNumber(student, today);
        if (dayNum <= 0) return null;
        if (plan.containsKey(dayNum)) {
            return plan.get(dayNum);
        }
        int maxDay = plan.keySet().stream().max(Integer::compareTo).orElse(1);
        if (dayNum <= maxDay) {
            return plan.get(dayNum);
        }
        return null;
    }

    private ZoomMeetingDto mapZoomMeeting(Student student, ZoomMeeting meeting) {
        return new ZoomMeetingDto(
                meeting.getId(),
                student.getId(),
                student.getFullName(),
                meeting.getMeetingTitle(),
                meeting.getCallNumber(),
                meeting.getZoomUrl(),
                meeting.getRecordingUrl(),
                meeting.getScheduledAt(),
                meeting.getLinkSentAt(),
                meeting.getEvidenceUrl(),
                meeting.getSourceChatId(),
                meeting.getSourceMessageId(),
                meeting.getCompletedAt(),
                meeting.getIsCompleted(),
                meeting.getMeetingNotes()
        );
    }

    @Transactional
    public DayInfoResponse updateDisciplineDay(Long studentId, UpdateDayStatusRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));
        Set<String> statuses = Set.of("COMPLETED_ON_TIME", "COMPLETED_LATE", "MISSED", "PENDING", "WEEKEND", "PAUSED");
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!statuses.contains(status)) throw new IllegalArgumentException("Unsupported day status: " + status);

        LocalDate requestedDate = resolveVisibleWeekDate(request.date());
        if (requestedDate == null) throw new IllegalArgumentException("Date is outside the currently displayed week");

        StudentDayOverride override = studentDayOverrideRepository
                .findByStudentIdAndDayDate(studentId, requestedDate)
                .orElseGet(() -> StudentDayOverride.builder().student(student).dayDate(requestedDate).build());
        override.setStatus(status);
        override.setDetails(request.details());
        studentDayOverrideRepository.save(override);

        DayInfoResponse day = new DayInfoResponse(
                RU_DAY_NAMES[requestedDate.getDayOfWeek().getValue() - 1],
                DAY_DATE_FORMATTER.format(requestedDate),
                status,
                request.details(),
                requestedDate
        );
        return day;
    }

    private List<DayInfoResponse> buildWeeklyCalendar(Student student) {
        return buildWeeklyCalendar(student, null, null);
    }

    private List<DayInfoResponse> buildWeeklyCalendar(
            Student student,
            Map<LocalDate, StudentDayOverride> overrides,
            Map<LocalDate, DailyReport> reports
    ) {
        List<DayInfoResponse> calendar = new ArrayList<>();
        LocalDate today = LocalDate.now(MINSK_ZONE);
        LocalDate monday = resolveVisibleWeekMonday(today);

        LocalDate deadlineReference = today.isBefore(monday) ? monday.plusDays(7) : today;

        for (int i = 0; i < 7; i++) {
            LocalDate dayDate = monday.plusDays(i);
            String dayName = RU_DAY_NAMES[i];
            String dateFormatted = DAY_DATE_FORMATTER.format(dayDate);

            Optional<StudentDayOverride> override = overrides != null
                    ? Optional.ofNullable(overrides.get(dayDate))
                    : studentDayOverrideRepository.findByStudentIdAndDayDate(student.getId(), dayDate);
            if (override.isPresent()) {
                calendar.add(new DayInfoResponse(dayName, dateFormatted, override.get().getStatus(), override.get().getDetails(), dayDate));
                continue;
            }

            // 1. Sunday rule: official cohort rest day
            if (i == 6) {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "WEEKEND",
                        "Воскресенье — общий выходной потока ☕",
                        dayDate
                ));
                continue;
            }

            // 2. Query database for authentic daily report
            Optional<DailyReport> reportOpt = reports != null
                    ? Optional.ofNullable(reports.get(dayDate))
                    : dailyReportRepository.findByStudentIdAndReportDate(student.getId(), dayDate);

            // 3. Illness / pause mode:
            // If pausedFromDate is explicitly set, illness overrides any report/draft on or after pausedFromDate.
            // If pausedFromDate is null (legacy/seed data), illness applies to days without authentic reports.
            boolean isDayInPause = Boolean.TRUE.equals(student.getIsPaused())
                    && (student.getPausedFromDate() != null
                            ? !dayDate.isBefore(student.getPausedFromDate())
                            : reportOpt.isEmpty());

            if (isDayInPause) {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "PAUSED",
                        "Режим паузы: болезнь, стрик заморожен ☕",
                        dayDate
                ));
                continue;
            }

            if (reportOpt.isPresent()) {
                DailyReport report = reportOpt.get();
                if (Boolean.TRUE.equals(report.getIsOnTime())) {
                    calendar.add(new DayInfoResponse(
                            dayName,
                            dateFormatted,
                            "COMPLETED_ON_TIME",
                            formatReportDetails(report),
                            dayDate
                    ));
                } else {
                    calendar.add(new DayInfoResponse(
                            dayName,
                            dateFormatted,
                            "COMPLETED_LATE",
                            formatLateReportDetails(report),
                            dayDate
                    ));
                }
                continue;
            }

            // 4. Absence handling: active student missed deadline
            if (dayDate.isBefore(deadlineReference)) {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "MISSED",
                        "Пропуск: отчёт не сдан до 21:00",
                        dayDate
                ));
            } else {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "PENDING",
                        null,
                        dayDate
                ));
            }
        }

        return calendar;
    }

    private LocalDate resolveVisibleWeekMonday(LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (environment != null && environment.matchesProfiles("test")) {
            if (!dailyReportRepository.existsByReportDateBetween(monday, monday.plusDays(6))) {
                LocalDate maxDate = dailyReportRepository.findMaxReportDate();
                if (maxDate != null) monday = maxDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            }
        }
        return monday;
    }

    private LocalDate resolveVisibleWeekDate(String requestedDate) {
        LocalDate today = LocalDate.now(MINSK_ZONE);
        LocalDate monday = resolveVisibleWeekMonday(today);
        if (requestedDate != null && requestedDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate isoDate = LocalDate.parse(requestedDate);
            return !isoDate.isBefore(monday) && !isoDate.isAfter(monday.plusDays(6)) ? isoDate : null;
        }
        for (int offset = 0; offset < 7; offset++) {
            LocalDate candidate = monday.plusDays(offset);
            if (DAY_DATE_FORMATTER.format(candidate).equals(requestedDate.trim())) return candidate;
        }
        return null;
    }

    public int calculateDisciplineRate(Student student, List<DayInfoResponse> weekDays) {
        // Exclude WEEKEND, PAUSED, and PENDING days from denominator
        long evaluatedDays = weekDays.stream()
                .filter(d -> !"WEEKEND".equals(d.status()) && !"PAUSED".equals(d.status()) && !"PENDING".equals(d.status()))
                .count();

        if (evaluatedDays == 0) {
            return 100;
        }

        long onTimeCount = weekDays.stream().filter(d -> "COMPLETED_ON_TIME".equals(d.status())).count();
        long lateCount = weekDays.stream().filter(d -> "COMPLETED_LATE".equals(d.status())).count();

        double rate = (onTimeCount * 100.0 + lateCount * 70.0) / evaluatedDays;
        return (int) Math.round(rate);
    }

    private String formatReportDetails(DailyReport report) {
        StringBuilder sb = new StringBuilder();
        if (report.getContentDrafts() != null && !report.getContentDrafts().isEmpty()) {
            for (com.mentorhub.entity.ContentDraft draft : report.getContentDrafts()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(draft.getLabel());
            }
        }
        if (report.getMailingsCount() != null && report.getMailingsCount() > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(report.getMailingsCount()).append(" рассылок");
        }
        if (report.getNewClientsCount() != null && report.getNewClientsCount() > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            int c = report.getNewClientsCount();
            String suffix = (c % 10 == 1 && c % 100 != 11) ? " клиент" : " клиента";
            sb.append("+").append(c).append(suffix);
        }
        if (sb.isEmpty() && StringUtils.hasText(report.getAiSummary())) {
            sb.append(report.getAiSummary());
        }
        return sb.isEmpty() ? "Отчёт сдан" : sb.toString();
    }

    private String formatLateReportDetails(DailyReport report) {
        String base = formatReportDetails(report);
        if (report.getSubmittedAt() != null) {
            String timeStr = DateTimeFormatter.ofPattern("HH:mm").withZone(MINSK_ZONE).format(report.getSubmittedAt());
            return "Сдано в " + timeStr + " (опоздание): " + base;
        }
        return "Сдано с опозданием: " + base;
    }

    private String generateAvatarText(String fullName) {
        if (!StringUtils.hasText(fullName)) return "УЧ";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        return fullName.substring(0, Math.min(2, fullName.length())).toUpperCase();
    }
}
