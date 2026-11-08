package com.mentorhub.scheduler;

import com.mentorhub.entity.Student;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.telegram.service.TelegramSenderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SaturdaySurveySchedulerTest {

    @Test
    @DisplayName("getEligibleStudents excludes paused students")
    void getEligibleStudents_filtersPaused() {
        StudentRepository studentRepository = mock(StudentRepository.class);
        TelegramSenderService stubSenderService = new TelegramSenderService(null);

        Student s1 = Student.builder().id(1L).fullName("Милана").tgChatId(71010101L).isActive(true).isPaused(false).build();
        Student s2 = Student.builder().id(2L).fullName("Виктория").tgChatId(71040404L).isActive(true).isPaused(true).build();
        Student s3 = Student.builder().id(3L).fullName("Полина").tgChatId(71050505L).isActive(true).isPaused(false).build();

        when(studentRepository.findByIsActiveTrue()).thenReturn(List.of(s1, s2, s3));

        SaturdaySurveyScheduler scheduler = new SaturdaySurveyScheduler(studentRepository, stubSenderService);
        List<Student> eligible = scheduler.getEligibleStudents();

        assertThat(eligible).hasSize(2);
        assertThat(eligible).extracting(Student::getId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("sendSurveyToStudent calls telegramSenderService with Saturday survey poll text")
    void sendSurveyToStudent() {
        StudentRepository studentRepository = mock(StudentRepository.class);

        final Long[] sentChatId = new Long[1];
        final Long[] sentStudentId = new Long[1];
        final String[] sentText = new String[1];

        TelegramSenderService stubSenderService = new TelegramSenderService(null) {
            @Override
            public boolean sendSaturdaySurveyPoll(Long chatId, Long studentId, String text) {
                sentChatId[0] = chatId;
                sentStudentId[0] = studentId;
                sentText[0] = text;
                return true;
            }
        };

        Student s1 = Student.builder().id(1L).fullName("Милана").tgChatId(71010101L).build();

        SaturdaySurveyScheduler scheduler = new SaturdaySurveyScheduler(studentRepository, stubSenderService);
        boolean sent = scheduler.sendSurveyToStudent(s1);

        assertThat(sent).isTrue();
        assertThat(sentChatId[0]).isEqualTo(71010101L);
        assertThat(sentStudentId[0]).isEqualTo(1L);
        assertThat(sentText[0]).contains("Субботний чекап недели! 📊\nСколько новых клиентов ты закрыла за эту неделю?");
    }
}
