package com.mentorhub.service;

import com.mentorhub.dto.*;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.entity.Student;
import com.mentorhub.entity.ZoomMeeting;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.repository.ZoomMeetingRepository;
import com.mentorhub.telegram.service.TelegramSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final MentorSettingRepository mentorSettingRepository;
    private final StudentRepository studentRepository;
    private final DailyReportRepository dailyReportRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final TelegramSenderService telegramSenderService;

    @Value("${telegram.bot.username:MentorHubBot}")
    private String botUsername;

    @Value("${telegram.bot.enabled:false}")
    private Boolean botEnabled;

    @Value("${spring.ai.openai.api-key:}")
    private String aiApiKey;

    @Transactional(readOnly = true)
    public SystemStatusResponse getSystemStatus() {
        Optional<MentorSetting> settingOpt = mentorSettingRepository.findAll().stream().findFirst();
        boolean businessConnected = settingOpt
                .map(s -> Boolean.TRUE.equals(s.getIsBusinessConnected())
                        && s.getBusinessConnectionId() != null
                        && !s.getBusinessConnectionId().isBlank())
                .orElse(false);

        long activeStudentsCount = studentRepository.findByIsActiveTrue().size();
        long pendingReportsToday = dailyReportRepository.countByStatus("PENDING");
        String springBootVersion = SpringBootVersion.getVersion();

        Instant nextCallAt = resolveNextCallAt();
        long completedCallsTotal = resolveCompletedCallsTotal();

        ZoomTrackerSummaryDto zoomSummary = new ZoomTrackerSummaryDto(nextCallAt, completedCallsTotal);

        boolean virtualThreads;
        try {
            Thread vThread = Thread.ofVirtual().unstarted(() -> {});
            virtualThreads = vThread.isVirtual();
        } catch (Throwable t) {
            virtualThreads = true;
        }

        return new SystemStatusResponse(
                Boolean.TRUE.equals(botEnabled) && telegramSenderService.isConfigured(),
                botUsername,
                businessConnected ? "CONNECTED" : "DISCONNECTED",
                businessConnected,
                activeStudentsCount,
                pendingReportsToday,
                springBootVersion == null ? "unknown" : springBootVersion,
                virtualThreads,
                aiApiKey != null && !aiApiKey.isBlank() && !"mock-key".equalsIgnoreCase(aiApiKey) ? "CONFIGURED" : "NOT_CONFIGURED",
                zoomSummary
        );
    }

    @Transactional(readOnly = true)
    public ZoomTrackerResponse getZoomTracker() {
        Instant nextCallAt = resolveNextCallAt();
        long completedCallsTotal = resolveCompletedCallsTotal();

        List<ZoomMeetingDto> upcoming = zoomMeetingRepository.findAll().stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsCompleted()))
                .sorted(Comparator.comparing(ZoomMeeting::getScheduledAt))
                .map(this::mapZoomMeetingToDto)
                .toList();

        return new ZoomTrackerResponse(nextCallAt, completedCallsTotal, upcoming);
    }

    @Transactional(readOnly = true)
    public SystemSettingsDto getSettings() {
        MentorSetting setting = mentorSettingRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Настройки наставника не найдены"));
        return mapSettings(setting);
    }

    @Transactional
    public SystemSettingsDto updateSettings(SystemSettingsDto request) {
        MentorSetting setting = mentorSettingRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Настройки наставника не найдены"));
        setting.setMorningPushEnabled(request.morningPushEnabled());
        if (request.morningPushTime() != null) setting.setMorningPushTime(request.morningPushTime());
        setting.setEveningReminderEnabled(request.eveningReminderEnabled());
        if (request.eveningReminderTime() != null) setting.setEveningReminderTime(request.eveningReminderTime());
        if (request.dailyDeadlineTime() != null) setting.setDeadlineTime(request.dailyDeadlineTime());
        setting.setStrictDeadlineEnabled(request.strictDeadlineEnabled());
        setting.setSaturdayPollEnabled(request.saturdayPollEnabled());
        if (request.saturdayPollTime() != null) setting.setSaturdayPollTime(request.saturdayPollTime());
        setting.setSundayRestEnabled(request.sundayRestEnabled());
        setting.setAiWhisperEnabled(request.aiWhisperEnabled());
        setting.setAiSummaryEnabled(request.aiSummaryEnabled());
        setting.setAntiFraudDedupEnabled(request.antiFraudDedupEnabled());
        setting.setZoomAutoDetectEnabled(request.zoomAutoDetectEnabled());
        setting.setAutoRemindersEnabled(request.eveningReminderEnabled());
        java.time.LocalTime eveningTime = request.eveningReminderTime() != null ? request.eveningReminderTime() : setting.getEveningReminderTime();
        if (eveningTime != null) setting.setReminderCron("0 " + eveningTime.getMinute() + " " + eveningTime.getHour() + " * * MON-SAT");
        java.time.LocalTime saturdayTime = request.saturdayPollTime() != null ? request.saturdayPollTime() : setting.getSaturdayPollTime();
        if (saturdayTime != null) setting.setSaturdayPollCron("0 " + saturdayTime.getMinute() + " " + saturdayTime.getHour() + " * * SAT");
        return mapSettings(mentorSettingRepository.save(setting));
    }

    @Transactional
    public ZoomMeetingDto scheduleZoom(ScheduleZoomRequest request) {
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + request.studentId()));

        ZoomMeeting meeting = ZoomMeeting.builder()
                .student(student)
                .meetingTitle(request.title() == null || request.title().isBlank() ? "Созвон" : request.title().trim())
                .callNumber(request.callNumber())
                .zoomUrl(request.zoomUrl())
                .recordingUrl(request.recordingUrl())
                .scheduledAt(request.scheduledAt())
                .linkSentAt(request.linkSentAt())
                .evidenceUrl(request.evidenceUrl())
                .meetingNotes(request.meetingNotes())
                .isCompleted(false)
                .build();

        ZoomMeeting saved = zoomMeetingRepository.save(meeting);

        student.setNextZoomAt(request.scheduledAt());
        studentRepository.save(student);

        log.info("Scheduled Zoom call for student '{}' at {}", student.getFullName(), request.scheduledAt());
        return mapZoomMeetingToDto(saved);
    }

    @Transactional
    public ZoomMeetingDto updateZoomMeeting(Long meetingId, UpdateZoomMeetingRequest request) {
        ZoomMeeting meeting = zoomMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Zoom meeting not found with id: " + meetingId));
        if (request.title() != null) meeting.setMeetingTitle(request.title().trim());
        if (request.callNumber() != null) meeting.setCallNumber(request.callNumber());
        if (request.zoomUrl() != null) meeting.setZoomUrl(request.zoomUrl());
        if (request.recordingUrl() != null) meeting.setRecordingUrl(request.recordingUrl());
        if (request.scheduledAt() != null) meeting.setScheduledAt(request.scheduledAt());
        if (request.linkSentAt() != null) meeting.setLinkSentAt(request.linkSentAt());
        if (request.evidenceUrl() != null) meeting.setEvidenceUrl(request.evidenceUrl());
        if (request.meetingNotes() != null) meeting.setMeetingNotes(request.meetingNotes());
        if (request.isCompleted() != null) {
            meeting.setIsCompleted(request.isCompleted());
            meeting.setCompletedAt(request.isCompleted()
                    ? Optional.ofNullable(meeting.getCompletedAt()).orElseGet(Instant::now)
                    : null);
        }
        ZoomMeeting saved = zoomMeetingRepository.save(meeting);
        Student student = saved.getStudent();
        if (student != null) {
            long completed = zoomMeetingRepository.findByStudentIdOrderByCallNumberAscIdAsc(student.getId()).stream()
                    .filter(item -> Boolean.TRUE.equals(item.getIsCompleted())).count();
            student.setCompletedZoomsCount(Math.toIntExact(completed));
            student.setNextZoomAt(zoomMeetingRepository.findByStudentIdOrderByCallNumberAscIdAsc(student.getId()).stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getIsCompleted()))
                    .map(ZoomMeeting::getScheduledAt)
                    .filter(java.util.Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null));
            studentRepository.save(student);
        }
        return mapZoomMeetingToDto(saved);
    }

    @Transactional
    public void deleteZoomMeeting(Long meetingId) {
        ZoomMeeting meeting = zoomMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Zoom meeting not found with id: " + meetingId));
        Student student = meeting.getStudent();
        zoomMeetingRepository.delete(meeting);
        if (student != null) {
            List<ZoomMeeting> remaining = zoomMeetingRepository.findByStudentIdOrderByCallNumberAscIdAsc(student.getId());
            for (int index = 0; index < remaining.size(); index++) remaining.get(index).setCallNumber(index + 1);
            zoomMeetingRepository.saveAll(remaining);
            student.setCompletedZoomsCount(Math.toIntExact(remaining.stream().filter(item -> Boolean.TRUE.equals(item.getIsCompleted())).count()));
            student.setNextZoomAt(remaining.stream().filter(item -> !Boolean.TRUE.equals(item.getIsCompleted()))
                    .map(ZoomMeeting::getScheduledAt).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
            studentRepository.save(student);
        }
    }

    public SyncTelegramResponse syncTelegram() {
        boolean botReachable = telegramSenderService.checkBotConnection();
        log.info("Telegram connection check completed. reachable={}", botReachable);
        return new SyncTelegramResponse(
                botReachable,
                botReachable
                        ? "Подключение к Telegram проверено; входящие обновления обрабатываются в фоне"
                        : "Telegram недоступен или токен бота не настроен",
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public TelegramMessageDeliveryResponse sendMessageToStudent(Long studentId, String text) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));
        if (student.getTgChatId() == null) {
            return new TelegramMessageDeliveryResponse(false, "У ученицы не указан Telegram chat ID", Instant.now());
        }
        String businessConnectionId = mentorSettingRepository.findFirstByOrderByIdAsc()
                .filter(setting -> Boolean.TRUE.equals(setting.getIsBusinessConnected()))
                .map(MentorSetting::getBusinessConnectionId)
                .orElse(null);
        boolean delivered = telegramSenderService.sendStudentMessage(student.getTgChatId(), businessConnectionId, text);
        return new TelegramMessageDeliveryResponse(
                delivered,
                delivered ? "Сообщение доставлено" : "Telegram не принял сообщение",
                Instant.now()
        );
    }

    private SystemSettingsDto mapSettings(MentorSetting setting) {
        return new SystemSettingsDto(
                Boolean.TRUE.equals(setting.getMorningPushEnabled()),
                setting.getMorningPushTime(),
                Boolean.TRUE.equals(setting.getEveningReminderEnabled()),
                setting.getEveningReminderTime(),
                setting.getDeadlineTime(),
                Boolean.TRUE.equals(setting.getStrictDeadlineEnabled()),
                Boolean.TRUE.equals(setting.getSaturdayPollEnabled()),
                setting.getSaturdayPollTime(),
                Boolean.TRUE.equals(setting.getSundayRestEnabled()),
                Boolean.TRUE.equals(setting.getAiWhisperEnabled()),
                Boolean.TRUE.equals(setting.getAiSummaryEnabled()),
                Boolean.TRUE.equals(setting.getAntiFraudDedupEnabled()),
                Boolean.TRUE.equals(setting.getZoomAutoDetectEnabled())
        );
    }

    private Instant resolveNextCallAt() {
        return zoomMeetingRepository.findFirstByIsCompletedFalseOrderByScheduledAtAsc()
                .map(ZoomMeeting::getScheduledAt)
                .or(() -> studentRepository.findAll().stream()
                        .map(Student::getNextZoomAt)
                        .filter(java.util.Objects::nonNull)
                        .min(Comparator.naturalOrder()))
                .orElse(null);
    }

    private long resolveCompletedCallsTotal() {
        long count = zoomMeetingRepository.countByIsCompletedTrue();
        if (count > 0) return count;

        long fromStudents = studentRepository.findAll().stream()
                .mapToLong(s -> s.getCompletedZoomsCount() != null ? s.getCompletedZoomsCount() : 0)
                .sum();
        return fromStudents;
    }

    private ZoomMeetingDto mapZoomMeetingToDto(ZoomMeeting meeting) {
        String studentName = meeting.getStudent() != null ? meeting.getStudent().getFullName() : "";
        Long studentId = meeting.getStudent() != null ? meeting.getStudent().getId() : null;
        return new ZoomMeetingDto(
                meeting.getId(),
                studentId,
                studentName,
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
}
