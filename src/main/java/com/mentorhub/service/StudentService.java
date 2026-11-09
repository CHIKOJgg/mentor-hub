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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final DailyReportRepository dailyReportRepository;
    private final StudentDayOverrideRepository studentDayOverrideRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;

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

        return students.stream()
                .map(this::mapToDisciplineResponse)
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

        Student updated = studentRepository.save(student);
        log.info("Updated student '{}' with id #{}", updated.getFullName(), updated.getId());
        return mapToDisciplineResponse(updated);
    }

    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        student.setIsActive(false);
        studentRepository.save(student);
        log.info("Soft deleted student id #{}", id);
    }

    @Transactional
    public TogglePauseResponse togglePause(Long id, TogglePauseRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));

        boolean newPausedState = Boolean.TRUE.equals(request.isPaused());
        student.setIsPaused(newPausedState);
        student.setPauseReason(newPausedState ? request.pauseReason() : null);

        studentRepository.save(student);
        log.info("Toggled pause for student '{}' (id #{}): isPaused={}, reason='{}'",
                student.getFullName(), id, newPausedState, student.getPauseReason());

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
        List<ZoomSessionInput> sessions = request.sessions() == null ? List.of() : request.sessions();
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
        List<DayInfoResponse> weekDays = buildWeeklyCalendar(student);
        int disciplineRate = calculateDisciplineRate(student, weekDays);

        String nextZoomDate = null;
        if (student.getNextZoomAt() != null) {
            nextZoomDate = ZOOM_DATE_FORMATTER.format(student.getNextZoomAt());
        }

        String rawTariff = student.getTariff() != null ? student.getTariff() : "STANDARD";
        String tariff = "VIP".equalsIgnoreCase(rawTariff) ? "VIP" : "Стандарт";

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
                                meeting.getCompletedAt(),
                                meeting.getIsCompleted(),
                                meeting.getMeetingNotes()
                        )).toList()
        );
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
        List<DayInfoResponse> calendar = new ArrayList<>();
        LocalDate today = LocalDate.now(MINSK_ZONE);
        LocalDate monday = resolveVisibleWeekMonday(today);

        LocalDate deadlineReference = today.isBefore(monday) ? monday.plusDays(7) : today;

        for (int i = 0; i < 7; i++) {
            LocalDate dayDate = monday.plusDays(i);
            String dayName = RU_DAY_NAMES[i];
            String dateFormatted = DAY_DATE_FORMATTER.format(dayDate);

            Optional<StudentDayOverride> override = studentDayOverrideRepository.findByStudentIdAndDayDate(student.getId(), dayDate);
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
            Optional<DailyReport> reportOpt = dailyReportRepository.findByStudentIdAndReportDate(student.getId(), dayDate);
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

            // 3. Absence handling: check illness / pause mode
            if (Boolean.TRUE.equals(student.getIsPaused())) {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "PAUSED",
                        "Режим паузы: болезнь, стрик заморожен ☕",
                        dayDate
                ));
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
        if (!dailyReportRepository.existsByReportDateBetween(monday, monday.plusDays(6))) {
            LocalDate maxDate = dailyReportRepository.findMaxReportDate();
            if (maxDate != null) monday = maxDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
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
