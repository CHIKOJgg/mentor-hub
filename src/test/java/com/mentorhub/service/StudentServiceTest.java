package com.mentorhub.service;

import com.mentorhub.dto.CreateStudentRequest;
import com.mentorhub.dto.DayInfoResponse;
import com.mentorhub.dto.StudentDisciplineResponse;
import com.mentorhub.dto.TogglePauseRequest;
import com.mentorhub.dto.TogglePauseResponse;
import com.mentorhub.dto.UpdateStudentRequest;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.StudentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentServiceTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    @Test
    @DisplayName("getStudents returns full student cohort with 7-day weekly calendar")
    void testGetStudentsReturnsFullCohortWithCalendar() {
        List<StudentDisciplineResponse> students = studentService.getStudents(null, true);
        assertThat(students).hasSize(5);

        for (StudentDisciplineResponse s : students) {
            assertThat(s.weekDays()).hasSize(7);
            // Verify day abbreviations Mon-Sun
            assertThat(s.weekDays().get(0).day()).isEqualTo("Пн");
            assertThat(s.weekDays().get(1).day()).isEqualTo("Вт");
            assertThat(s.weekDays().get(2).day()).isEqualTo("Ср");
            assertThat(s.weekDays().get(3).day()).isEqualTo("Чт");
            assertThat(s.weekDays().get(4).day()).isEqualTo("Пт");
            assertThat(s.weekDays().get(5).day()).isEqualTo("Сб");
            assertThat(s.weekDays().get(6).day()).isEqualTo("Вс");
        }
    }

    @Test
    @DisplayName("Sunday is always WEEKEND with coffee tooltip and excluded from penalties")
    void testSundayWeekendRule() {
        List<StudentDisciplineResponse> students = studentService.getStudents(null, true);

        for (StudentDisciplineResponse s : students) {
            DayInfoResponse sunday = s.weekDays().get(6);
            assertThat(sunday.day()).isEqualTo("Вс");
            assertThat(sunday.status()).isEqualTo("WEEKEND");
            assertThat(sunday.missedDetails()).isEqualTo("Воскресенье — общий выходной потока ☕");
        }
    }

    @Test
    @DisplayName("Sick student has PAUSED status with streak freeze and no discipline penalties")
    void testSickStudentStreakFreeze() {
        Student victoria = studentRepository.findByTgUsername("v_lebedeva_fit")
                .orElseThrow(() -> new AssertionError("Victoria not found"));

        StudentDisciplineResponse response = studentService.getStudentById(victoria.getId());
        assertThat(response.isOnPause()).isTrue();
        assertThat(response.pauseReason()).contains("Заболела");

        // Verify paused days in calendar
        List<DayInfoResponse> pausedDays = response.weekDays().stream()
                .filter(d -> "PAUSED".equals(d.status()))
                .toList();
        assertThat(pausedDays).isNotEmpty();
        for (DayInfoResponse d : pausedDays) {
            assertThat(d.missedDetails()).contains("Режим паузы: болезнь, стрик заморожен ☕");
        }

        // Verify discipline rate remains high (93%) despite absence
        assertThat(response.disciplineRate()).isEqualTo(93);
    }

    @Test
    @DisplayName("togglePause toggles pause state and freezes streak")
    void testTogglePause() {
        Student milana = studentRepository.findByTgUsername("milana_gorohova")
                .orElseThrow(() -> new AssertionError("Milana not found"));

        assertThat(milana.getIsPaused()).isFalse();

        // 1. Pause student
        TogglePauseResponse paused = studentService.togglePause(
                milana.getId(),
                new TogglePauseRequest(true, "Поездка на конференцию")
        );
        assertThat(paused.isOnPause()).isTrue();
        assertThat(paused.pauseReason()).isEqualTo("Поездка на конференцию");
        assertThat(paused.streakFrozen()).isTrue();

        Student updatedMilana = studentRepository.findById(milana.getId()).orElseThrow();
        assertThat(updatedMilana.getIsPaused()).isTrue();
        assertThat(updatedMilana.getPauseReason()).isEqualTo("Поездка на конференцию");

        // 2. Unpause student
        TogglePauseResponse unpaused = studentService.togglePause(
                milana.getId(),
                new TogglePauseRequest(false, null)
        );
        assertThat(unpaused.isOnPause()).isFalse();
        assertThat(unpaused.pauseReason()).isNull();

        Student restoredMilana = studentRepository.findById(milana.getId()).orElseThrow();
        assertThat(restoredMilana.getIsPaused()).isFalse();
        assertThat(restoredMilana.getPauseReason()).isNull();
    }

    @Test
    @DisplayName("Search filter dynamically filters by full name and niche")
    void testSearchFilter() {
        List<StudentDisciplineResponse> byName = studentService.getStudents("Милана", true);
        assertThat(byName).hasSize(1);
        assertThat(byName.get(0).fullName()).isEqualTo("Милана Горохова");

        List<StudentDisciplineResponse> byNiche = studentService.getStudents("Косметолог", true);
        assertThat(byNiche).hasSize(1);
        assertThat(byNiche.get(0).fullName()).isEqualTo("Полина Соколова");

        List<StudentDisciplineResponse> notFound = studentService.getStudents("НесуществующийУченик", true);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("Student CRUD operations: create, update, and soft delete")
    void testStudentCrud() {
        // Create
        CreateStudentRequest createRequest = new CreateStudentRequest(
                "Елена Смирнова",
                "ЕС",
                "elena_smirnova",
                99887766L,
                "Коуч экспертов",
                "VIP",
                new BigDecimal("60000.00"),
                new BigDecimal("15000.00"),
                LocalDate.now()
        );
        StudentDisciplineResponse created = studentService.createStudent(createRequest);
        assertThat(created.fullName()).isEqualTo("Елена Смирнова");
        assertThat(created.initialIncome()).isEqualByComparingTo("60000.00");
        assertThat(created.tariff()).isEqualTo("VIP");

        Long newId = Long.parseLong(created.id().replace("st-", ""));

        // Update
        UpdateStudentRequest updateRequest = new UpdateStudentRequest(
                "Елена Смирнова-Иванова",
                "ЕИ",
                "elena_smirnova",
                99887766L,
                "Трансформационный коуч",
                "VIP",
                new BigDecimal("60000.00"),
                new BigDecimal("250000.00"),
                new BigDecimal("15000.00"),
                new BigDecimal("50000.00"),
                LocalDate.now(),
                true,
                false,
                null
        );
        StudentDisciplineResponse updated = studentService.updateStudent(newId, updateRequest);
        assertThat(updated.fullName()).isEqualTo("Елена Смирнова-Иванова");
        assertThat(updated.currentIncome()).isEqualByComparingTo("250000.00");

        // Soft Delete
        studentService.deleteStudent(newId);
        Student deleted = studentRepository.findById(newId).orElseThrow();
        assertThat(deleted.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("getStudentById throws ResourceNotFoundException for unknown ID")
    void testGetStudentByIdNotFound() {
        assertThatThrownBy(() -> studentService.getStudentById(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
