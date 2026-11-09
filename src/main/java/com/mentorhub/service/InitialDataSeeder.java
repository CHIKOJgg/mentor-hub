package com.mentorhub.service;

import com.mentorhub.entity.Student;
import com.mentorhub.entity.ZoomMeeting;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.repository.ZoomMeetingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitialDataSeeder {

    private final StudentRepository studentRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;

    @PostConstruct
    @Transactional
    public void seedInitialDataIfEmpty() {
        List<Student> students = studentRepository.findAll();
        if (students.isEmpty()) {
            log.info("No students found to seed reports or zoom meetings.");
            return;
        }

        // 1. Update next_zoom_at on students if null
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElse(null);
        if (milana == null) {
            log.info("Non-demo dataset detected (no demo students found). Skipping mock data seeding.");
            return;
        }
        Student anya = studentRepository.findByTgUsername("anya_petrova_style").orElse(null);
        Student karina = studentRepository.findByTgUsername("volkova_coach").orElse(null);
        Student victoria = studentRepository.findByTgUsername("v_lebedeva_fit").orElse(null);
        Student polina = studentRepository.findByTgUsername("sokolova_smm").orElse(null);

        Instant baseZoomDate = Instant.parse("2026-11-11T14:00:00Z");
        if (milana != null && milana.getNextZoomAt() == null) {
            milana.setNextZoomAt(baseZoomDate);
            studentRepository.save(milana);
        }
        if (anya != null && anya.getNextZoomAt() == null) {
            anya.setNextZoomAt(baseZoomDate.plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS)); // 12.11 16:00 UTC (19:00 MSK)
            studentRepository.save(anya);
        }
        if (karina != null && karina.getNextZoomAt() == null) {
            karina.setNextZoomAt(baseZoomDate.plus(2, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS)); // 13.11 12:00 UTC (15:00 MSK)
            studentRepository.save(karina);
        }
        if (victoria != null && victoria.getNextZoomAt() == null) {
            victoria.setNextZoomAt(baseZoomDate.plus(3, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS)); // 14.11 13:00 UTC (16:00 MSK)
            studentRepository.save(victoria);
        }
        if (polina != null && polina.getNextZoomAt() == null) {
            polina.setNextZoomAt(baseZoomDate.plus(4, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)); // 15.11 15:00 UTC (18:00 MSK)
            studentRepository.save(polina);
        }

        // 2. Seed completed and upcoming Zoom meetings if empty
        if (zoomMeetingRepository.count() == 0) {
            seedZoomMeetings(milana, anya, karina, victoria, polina);
        }
    }

    private void seedZoomMeetings(Student milana, Student anya, Student karina, Student victoria, Student polina) {
        log.info("Seeding initial Zoom meetings...");

        // Upcoming meetings
        if (milana != null && milana.getNextZoomAt() != null) {
            zoomMeetingRepository.save(ZoomMeeting.builder()
                    .student(milana)
                    .meetingTitle("Разбор скрипта продаж на высокий чек")
                    .callNumber((milana.getCompletedZoomsCount() == null ? 0 : milana.getCompletedZoomsCount()) + 1)
                    .zoomUrl("https://us02web.zoom.us/j/123456789")
                    .recordingUrl(null)
                    .scheduledAt(milana.getNextZoomAt())
                    .isCompleted(false)
                    .meetingNotes("Разбор скрипта продаж на высокий чек")
                    .build());
        }
        if (anya != null && anya.getNextZoomAt() != null) {
            zoomMeetingRepository.save(ZoomMeeting.builder()
                    .student(anya)
                    .meetingTitle("Проработка страха продаж")
                    .callNumber((anya.getCompletedZoomsCount() == null ? 0 : anya.getCompletedZoomsCount()) + 1)
                    .zoomUrl("https://us02web.zoom.us/j/234567890")
                    .scheduledAt(anya.getNextZoomAt())
                    .isCompleted(false)
                    .meetingNotes("Проработка страха продаж и синдрома самозванца")
                    .build());
        }

        // Completed meetings: total 19 meetings
        // Milana: 4, Anya: 3, Karina: 5, Victoria: 3, Polina: 4
        createCompletedMeetings(milana, 4);
        createCompletedMeetings(anya, 3);
        createCompletedMeetings(karina, 5);
        createCompletedMeetings(victoria, 3);
        createCompletedMeetings(polina, 4);
    }

    private void createCompletedMeetings(Student student, int count) {
        if (student == null) return;
        Instant pastDate = Instant.parse("2026-10-20T14:00:00Z");
        for (int i = 0; i < count; i++) {
            zoomMeetingRepository.save(ZoomMeeting.builder()
                    .student(student)
                    .meetingTitle("Созвон " + (i + 1))
                    .callNumber(i + 1)
                    .zoomUrl("https://us02web.zoom.us/j/meeting" + student.getId() + "_" + i)
                    .scheduledAt(pastDate.plus(i * 4L, ChronoUnit.DAYS))
                    .completedAt(pastDate.plus(i * 4L, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                    .isCompleted(true)
                    .meetingNotes("Проведена консультация #" + (i + 1))
                    .build());
        }
    }
}
