package com.mentorhub.scheduler;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.telegram.service.TelegramSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CallbackQueryHandlerTest {

    private StudentRepository studentRepository;
    private DailyReportRepository dailyReportRepository;

    private String answeredQueryId;
    private String answeredQueryText;
    private Long editedChatId;
    private Integer editedMessageId;
    private String editedText;

    private TelegramSenderService stubSenderService;
    private CallbackQueryHandler handler;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        dailyReportRepository = mock(DailyReportRepository.class);

        when(dailyReportRepository.save(any(DailyReport.class))).thenAnswer(i -> i.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(i -> i.getArgument(0));

        stubSenderService = new TelegramSenderService(null) {
            @Override
            public boolean answerCallbackQuery(String callbackQueryId, String text) {
                answeredQueryId = callbackQueryId;
                answeredQueryText = text;
                return true;
            }

            @Override
            public boolean editMessageText(Long chatId, Integer messageId, String text) {
                editedChatId = chatId;
                editedMessageId = messageId;
                editedText = text;
                return true;
            }
        };

        handler = new CallbackQueryHandler(studentRepository, dailyReportRepository, stubSenderService);
    }

    @Test
    @DisplayName("Handles survey:clients:{studentId}:{count} callback query, updates report & student, answers callback")
    void handleCallbackQuery_validSurvey() {
        Student student = Student.builder()
                .id(1L)
                .fullName("Милана Горохова")
                .tgChatId(71010101L)
                .totalNewClients(7)
                .build();

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(dailyReportRepository.findByStudentIdAndReportDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder().messageId(777).chat(chat).build();

        CallbackQuery query = new CallbackQuery();
        query.setId("cb_12345");
        query.setData("survey:clients:1:3");
        query.setMessage(message);

        boolean handled = handler.handleCallbackQuery(query);

        assertThat(handled).isTrue();

        // Student total_new_clients updated 7 + 3 = 10
        assertThat(student.getTotalNewClients()).isEqualTo(10);
        verify(studentRepository).save(student);

        // DailyReport saved with newClientsCount = 3
        verify(dailyReportRepository).save(argThat(r -> r.getNewClientsCount() == 3));

        // Answer callback query called
        assertThat(answeredQueryId).isEqualTo("cb_12345");
        assertThat(answeredQueryText).isEqualTo("Зафиксировано: +3 клиентов! 🎉");

        // Message edited with confirmation
        assertThat(editedChatId).isEqualTo(-10071010101L);
        assertThat(editedMessageId).isEqualTo(777);
        assertThat(editedText).contains("Спасибо! Зафиксировано новых клиентов за неделю: +3 🚀");
    }

    @Test
    @DisplayName("Non-survey callback data is gracefully ignored")
    void handleCallbackQuery_nonMatchingData() {
        CallbackQuery query = new CallbackQuery();
        query.setId("cb_other");
        query.setData("other:action:123");

        boolean handled = handler.handleCallbackQuery(query);

        assertThat(handled).isFalse();
        verifyNoInteractions(studentRepository);
        verifyNoInteractions(dailyReportRepository);
    }
}
