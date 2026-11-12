package com.mentorhub.service;

import com.mentorhub.dto.ScheduleZoomRequest;
import com.mentorhub.dto.SyncTelegramResponse;
import com.mentorhub.dto.SystemStatusResponse;
import com.mentorhub.dto.ZoomMeetingDto;
import com.mentorhub.dto.ZoomTrackerResponse;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.StudentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SystemServiceTest {

    @Autowired
    private SystemService systemService;

    @Autowired
    private StudentRepository studentRepository;

    @Test
    @DisplayName("getSystemStatus reports healthy system status with virtual threads and zoom tracker")
    void testGetSystemStatus() {
        SystemStatusResponse status = systemService.getSystemStatus();

        assertThat(status.botUsername()).isEqualTo("MockTestBot");
        assertThat(status.businessConnectionStatus()).isEqualTo("DISCONNECTED");
        assertThat(status.canReplyOnBehalf()).isFalse();
        assertThat(status.springBootVersion()).isNotBlank();
        assertThat(status.virtualThreadsEnabled()).isTrue();
        assertThat(status.springAiStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(status.activeStudentsCount()).isEqualTo(5);
        assertThat(status.pendingReportsToday()).isGreaterThanOrEqualTo(1);

        assertThat(status.zoomTracker()).isNotNull();
        assertThat(status.zoomTracker().completedCallsTotal()).isEqualTo(19);
        assertThat(status.zoomTracker().nextCallAt()).isNotNull();
    }

    @Test
    @DisplayName("syncTelegram reports unavailable bot honestly")
    void testSyncTelegram() {
        SyncTelegramResponse response = systemService.syncTelegram();
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("недоступен");
        assertThat(response.syncedAt()).isNotNull();
    }

    @Test
    @DisplayName("getZoomTracker returns overview with completed meetings count and upcoming list")
    void testGetZoomTracker() {
        ZoomTrackerResponse tracker = systemService.getZoomTracker();
        assertThat(tracker.completedCallsTotal()).isEqualTo(19);
        assertThat(tracker.nextCallAt()).isNotNull();
        assertThat(tracker.upcomingMeetings()).isNotEmpty();
    }

    @Test
    @DisplayName("scheduleZoom creates zoom meeting and updates student nextZoomAt")
    void testScheduleZoom() {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();
        Instant newTime = Instant.parse("2026-11-20T14:00:00Z");

        ScheduleZoomRequest request = new ScheduleZoomRequest(
                milana.getId(),
                "https://us02web.zoom.us/j/999888777",
                newTime,
                "Стратегический созвон по масштабированию"
        );

        ZoomMeetingDto meeting = systemService.scheduleZoom(request);
        assertThat(meeting.studentId()).isEqualTo(milana.getId());
        assertThat(meeting.zoomUrl()).isEqualTo("https://us02web.zoom.us/j/999888777");
        assertThat(meeting.scheduledAt()).isEqualTo(newTime);
        assertThat(meeting.meetingNotes()).isEqualTo("Стратегический созвон по масштабированию");

        Student updated = studentRepository.findById(milana.getId()).orElseThrow();
        assertThat(updated.getNextZoomAt()).isEqualTo(newTime);
    }

    @Test
    @DisplayName("scheduleZoom throws ResourceNotFoundException for unknown student ID")
    void testScheduleZoomUnknownStudent() {
        ScheduleZoomRequest request = new ScheduleZoomRequest(
                999999L,
                "https://zoom.us",
                Instant.now(),
                "Notes"
        );
        assertThatThrownBy(() -> systemService.scheduleZoom(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
