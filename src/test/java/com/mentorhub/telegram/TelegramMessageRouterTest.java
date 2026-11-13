package com.mentorhub.telegram;

import com.mentorhub.entity.AdminToken;
import com.mentorhub.entity.AuditMessageLog;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.AdminTokenRepository;
import com.mentorhub.repository.AuditMessageLogRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.scheduler.CallbackQueryHandler;
import com.mentorhub.telegram.handler.TelegramReportHandler;
import com.mentorhub.telegram.router.TelegramMessageRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramMessageRouterTest {

    private StudentRepository studentRepository;
    private MentorSettingRepository mentorSettingRepository;
    private AdminTokenRepository adminTokenRepository;
    private AuditMessageLogRepository auditMessageLogRepository;

    private Student recordedStudent;
    private Message recordedMessage;
    private String recordedFocusText;
    private CallbackQuery recordedCallbackQuery;

    private TelegramReportHandler stubReportHandler;
    private CallbackQueryHandler stubCallbackHandler;
    private TelegramMessageRouter router;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        mentorSettingRepository = mock(MentorSettingRepository.class);
        adminTokenRepository = mock(AdminTokenRepository.class);
        auditMessageLogRepository = mock(AuditMessageLogRepository.class);
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(mentorSettingRepository.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(MentorSetting.builder().mentorTgId(11223344L).build()));

        when(adminTokenRepository.findAll()).thenReturn(List.of(
                AdminToken.builder().role("MENTOR").tgUserId(11223344L).build(),
                AdminToken.builder().role("CURATOR").tgUserId(22334455L).build()
        ));

        stubReportHandler = new TelegramReportHandler(null, null, null, null, null, null, null, null, null) {
            @Override
            public DailyReport handleStudentMessage(Student s, Message m) {
                recordedStudent = s;
                recordedMessage = m;
                return DailyReport.builder().build();
            }

            @Override
            public DailyReport handleMorningFocusMessage(Student s, Message m, String raw) {
                recordedStudent = s;
                recordedMessage = m;
                recordedFocusText = raw;
                return DailyReport.builder().build();
            }
        };

        stubCallbackHandler = new CallbackQueryHandler(null, null, null) {
            @Override
            public boolean handleCallbackQuery(CallbackQuery query) {
                recordedCallbackQuery = query;
                return true;
            }
        };

        router = new TelegramMessageRouter(
                studentRepository,
                mentorSettingRepository,
                adminTokenRepository,
                auditMessageLogRepository,
                stubReportHandler,
                stubCallbackHandler
        );
    }

    @Test
    @DisplayName("Ignores echo messages from Mentor Ksenia (mentor_tg_id)")
    void routeIncomingMessage_filtersKseniaEcho() {
        User ksenia = User.builder().id(11223344L).isBot(false).firstName("Ксения").build();
        Chat chat = Chat.builder().id(-100123456789L).type("supergroup").build();
        Message message = Message.builder().messageId(10).from(ksenia).chat(chat).text("Привет ученицам").build();

        router.routeIncomingMessage(message, 100L, "MINI_GROUP");

        verifyNoInteractions(studentRepository);
        verifyNoInteractions(auditMessageLogRepository);
        assertThat(recordedStudent).isNull();
    }

    @Test
    @DisplayName("Ignores echo messages from Curator Katya (admin token tg_user_id)")
    void routeIncomingMessage_filtersKatyaEcho() {
        User katya = User.builder().id(22334455L).isBot(false).firstName("Катя").build();
        Chat chat = Chat.builder().id(-100123456789L).type("supergroup").build();
        Message message = Message.builder().messageId(11).from(katya).chat(chat).text("Напоминаю об оплате").build();

        router.routeIncomingMessage(message, 100L, "MINI_GROUP");

        verifyNoInteractions(studentRepository);
        verifyNoInteractions(auditMessageLogRepository);
        assertThat(recordedStudent).isNull();
    }

    @Test
    @DisplayName("Ignores messages from bots (is_bot = true)")
    void routeIncomingMessage_filtersBotEcho() {
        User botUser = User.builder().id(99999999L).isBot(true).firstName("SomeBot").build();
        Chat chat = Chat.builder().id(-100123456789L).type("supergroup").build();
        Message message = Message.builder().messageId(12).from(botUser).chat(chat).text("Hello").build();

        router.routeIncomingMessage(message, 100L, "MINI_GROUP");

        verifyNoInteractions(studentRepository);
        assertThat(recordedStudent).isNull();
    }

    @Test
    @DisplayName("Unknown user logged to audit_message_logs as UNMATCHED_USER")
    void routeIncomingMessage_unknownUserLogged() {
        User stranger = User.builder().id(999888777L).firstName("Stranger").isBot(false).userName("stranger").build();
        Chat chat = Chat.builder().id(-100123456789L).type("supergroup").build();
        Message message = Message.builder().messageId(13).from(stranger).chat(chat).text("#рассылки 10").build();

        when(studentRepository.findByTgChatId(any())).thenReturn(Optional.empty());
        when(studentRepository.findByTgUsername(any())).thenReturn(Optional.empty());

        router.routeIncomingMessage(message, 101L, "MINI_GROUP");

        ArgumentCaptor<AuditMessageLog> captor = ArgumentCaptor.forClass(AuditMessageLog.class);
        verify(auditMessageLogRepository).save(captor.capture());

        AuditMessageLog log = captor.getValue();
        assertThat(log.getProcessingStatus()).isEqualTo("UNMATCHED_USER");
        assertThat(log.getTgUserId()).isEqualTo(999888777L);
        assertThat(log.getTgChatId()).isEqualTo(-100123456789L);
        assertThat(recordedStudent).isNull();
    }

    @Test
    @DisplayName("Identified student message routed to TelegramReportHandler and logged as PROCESSED")
    void routeIncomingMessage_successStudent() {
        User studentUser = User.builder().id(71010101L).firstName("Милана").isBot(false).userName("milana_gorohova").build();
        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder().messageId(14).from(studentUser).chat(chat).text("#рассылки 25").build();

        Student student = Student.builder().id(1L).fullName("Милана Горохова").tgChatId(71010101L).build();
        when(studentRepository.findByTgChatId(71010101L)).thenReturn(Optional.of(student));

        router.routeIncomingMessage(message, 102L, "MINI_GROUP");

        assertThat(recordedStudent).isNotNull();
        assertThat(recordedStudent.getId()).isEqualTo(1L);
        assertThat(recordedMessage).isEqualTo(message);

        ArgumentCaptor<AuditMessageLog> captor = ArgumentCaptor.forClass(AuditMessageLog.class);
        verify(auditMessageLogRepository).save(captor.capture());

        AuditMessageLog log = captor.getValue();
        assertThat(log.getProcessingStatus()).isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("Callback query update routes to CallbackQueryHandler")
    void routeUpdate_callbackQuery() {
        CallbackQuery query = new CallbackQuery();
        query.setId("cb_999");
        query.setData("survey:clients:1:2");

        Update update = new Update();
        update.setUpdateId(200);
        update.setCallbackQuery(query);

        router.routeUpdate(update);

        assertThat(recordedCallbackQuery).isEqualTo(query);
        verify(auditMessageLogRepository).save(any(AuditMessageLog.class));
    }

    @Test
    @DisplayName("Service message (groupchatCreated) is ignored without routing to report handler")
    void routeIncomingMessage_serviceMessageIgnoredSilently() {
        User creator = User.builder().id(71010101L).firstName("Мирослав").isBot(false).build();
        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder().messageId(15).from(creator).chat(chat).groupchatCreated(true).build();

        Student student = Student.builder().id(1L).fullName("Милана Горохова").tgChatId(71010101L).build();
        when(studentRepository.findByTgChatId(71010101L)).thenReturn(Optional.of(student));

        router.routeIncomingMessage(message, 103L, "MINI_GROUP");

        assertThat(recordedStudent).isNull();
        ArgumentCaptor<AuditMessageLog> captor = ArgumentCaptor.forClass(AuditMessageLog.class);
        verify(auditMessageLogRepository).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo("SERVICE_EVENT_IGNORED");
    }

    @Test
    @DisplayName("New group member is linked by their username, not by the person who invited them")
    void routeIncomingMessage_autoBindsNewMemberByUsername() {
        User inviter = User.builder().id(889900L).firstName("Мирослав").userName("miroslav").isBot(false).build();
        User studentUser = User.builder().id(71010101L).firstName("Милана").lastName("Горохова")
                .userName("milana_gorohova").isBot(false).build();
        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder().messageId(30).from(inviter).chat(chat).newChatMembers(List.of(studentUser)).build();
        Student student = Student.builder().id(1L).fullName("Милана Горохова").tgUsername("milana_gorohova").build();

        when(studentRepository.findByTgChatId(-10071010101L)).thenReturn(Optional.empty());
        when(studentRepository.findByTgUsername("milana_gorohova")).thenReturn(Optional.of(student));

        router.routeIncomingMessage(message, 301L, "MINI_GROUP");

        assertThat(student.getTgChatId()).isEqualTo(-10071010101L);
        verify(studentRepository).save(student);
        verify(studentRepository, never()).findByTgChatId(889900L);
        ArgumentCaptor<AuditMessageLog> audit = ArgumentCaptor.forClass(AuditMessageLog.class);
        verify(auditMessageLogRepository, times(2)).save(audit.capture());
        assertThat(audit.getAllValues()).anySatisfy(entry -> {
            assertThat(entry.getSourceType()).isEqualTo("TELEGRAM_AUTO_BIND");
            assertThat(entry.getTgUserId()).isEqualTo(71010101L);
            assertThat(entry.getProcessingStatus()).isEqualTo("AUTO_BOUND");
        });
    }

    @Test
    @DisplayName("Unknown newly added group member is created as a student without mentor or curator actions")
    void routeIncomingMessage_autoCreatesStudentFromNewMember() {
        User inviter = User.builder().id(889900L).firstName("Мирослав").isBot(false).build();
        User newStudent = User.builder().id(81112233L).firstName("Ольга").lastName("Тестова")
                .userName("olga_test").isBot(false).build();
        Chat chat = Chat.builder().id(-10081112233L).type("supergroup").build();
        User ksenia = User.builder().id(11223344L).firstName("Ксения").isBot(false).build();
        User katya = User.builder().id(22334455L).firstName("Катя").isBot(false).build();
        Message message = Message.builder().messageId(31).from(inviter).chat(chat)
                .newChatMembers(List.of(ksenia, katya, newStudent)).build();

        when(studentRepository.findByTgChatId(-10081112233L)).thenReturn(Optional.empty());
        when(studentRepository.findByTgUsername("olga_test")).thenReturn(Optional.empty());
        when(studentRepository.findAll()).thenReturn(List.of());

        router.routeIncomingMessage(message, 302L, "MINI_GROUP");

        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(saved.capture());
        assertThat(saved.getValue().getFullName()).isEqualTo("Ольга Тестова");
        assertThat(saved.getValue().getTgUsername()).isEqualTo("olga_test");
        assertThat(saved.getValue().getTgChatId()).isEqualTo(-10081112233L);
        assertThat(saved.getValue().getNiche()).isEqualTo("Не указана");
    }

    @Test
    @DisplayName("Repeated join event does not create or rebind a second student")
    void routeIncomingMessage_autoBindIsIdempotent() {
        User inviter = User.builder().id(889900L).firstName("Мирослав").isBot(false).build();
        User newStudent = User.builder().id(81112233L).firstName("Ольга").isBot(false).build();
        Chat chat = Chat.builder().id(-10081112233L).type("supergroup").build();
        Message message = Message.builder().messageId(32).from(inviter).chat(chat).newChatMembers(List.of(newStudent)).build();
        Student alreadyBound = Student.builder().id(44L).fullName("Ольга").tgChatId(-10081112233L).build();
        when(studentRepository.findByTgChatId(-10081112233L)).thenReturn(Optional.of(alreadyBound));
        when(studentRepository.findAll()).thenReturn(List.of(alreadyBound));

        router.routeIncomingMessage(message, 303L, "MINI_GROUP");

        verify(studentRepository, never()).save(any(Student.class));
        assertThat(alreadyBound.getTgChatId()).isEqualTo(-10081112233L);
    }

    @Test
    @DisplayName("Any newly added non-staff group member is accepted as the student and replaces a stale group owner")
    void routeIncomingMessage_newUnknownMemberReplacesStaleGroupOwner() {
        User inviter = User.builder().id(889900L).firstName("Мирослав").isBot(false).build();
        User newStudent = User.builder().id(81112233L).firstName("Ольга").lastName("Тестова")
                .userName("olga_test").isBot(false).build();
        Chat chat = Chat.builder().id(-10081112233L).type("supergroup").build();
        Message message = Message.builder().messageId(35).from(inviter).chat(chat).newChatMembers(List.of(newStudent)).build();
        Student oldBinding = Student.builder().id(9L).fullName("Мирослав").tgChatId(-10081112233L).build();
        when(studentRepository.findByTgChatId(-10081112233L)).thenReturn(Optional.of(oldBinding));
        when(studentRepository.findByTgUsername("olga_test")).thenReturn(Optional.empty());
        when(studentRepository.findAll()).thenReturn(List.of(oldBinding));

        router.routeIncomingMessage(message, 306L, "MINI_GROUP");

        assertThat(oldBinding.getTgChatId()).isNull();
        ArgumentCaptor<Student> created = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).saveAndFlush(oldBinding);
        verify(studentRepository).save(created.capture());
        assertThat(created.getValue().getFullName()).isEqualTo("Ольга Тестова");
        assertThat(created.getValue().getTgChatId()).isEqualTo(-10081112233L);
    }

    @Test
    @DisplayName("New member with a known profile repairs a stale group binding")
    void routeIncomingMessage_autoBindRepairsStaleGroupOwner() {
        User inviter = User.builder().id(889900L).firstName("Мирослав").isBot(false).build();
        User newStudent = User.builder().id(81112233L).firstName("Ольга").lastName("Тестова")
                .userName("olga_test").isBot(false).build();
        Chat chat = Chat.builder().id(-10081112233L).type("supergroup").build();
        Message message = Message.builder().messageId(33).from(inviter).chat(chat).newChatMembers(List.of(newStudent)).build();
        Student oldBinding = Student.builder().id(9L).fullName("Мирослав").tgChatId(-10081112233L).build();
        Student realStudent = Student.builder().id(10L).fullName("Ольга Тестова").tgUsername("olga_test").build();
        when(studentRepository.findByTgChatId(-10081112233L)).thenReturn(Optional.of(oldBinding));
        when(studentRepository.findByTgUsername("olga_test")).thenReturn(Optional.of(realStudent));

        router.routeIncomingMessage(message, 304L, "MINI_GROUP");

        assertThat(oldBinding.getTgChatId()).isNull();
        assertThat(realStudent.getTgChatId()).isEqualTo(-10081112233L);
        verify(studentRepository).saveAndFlush(oldBinding);
        verify(studentRepository).save(realStudent);
    }

    @Test
    @DisplayName("Report sender identity wins over a stale shared group link")
    void routeIncomingMessage_knownSenderWinsOverStaleGroupLink() {
        User sender = User.builder().id(81112233L).firstName("Ольга").userName("olga_test").isBot(false).build();
        Chat chat = Chat.builder().id(-10081112233L).type("supergroup").build();
        Message message = Message.builder().messageId(34).from(sender).chat(chat).text("#продажи 60000").build();
        Student student = Student.builder().id(10L).fullName("Ольга Тестова").tgUsername("olga_test").build();
        when(studentRepository.findByTgChatId(81112233L)).thenReturn(Optional.empty());
        when(studentRepository.findByTgUsername("olga_test")).thenReturn(Optional.of(student));
        when(studentRepository.findByTgChatId(-10081112233L)).thenReturn(Optional.empty());

        router.routeIncomingMessage(message, 305L, "MINI_GROUP");

        assertThat(recordedStudent).isSameAs(student);
        assertThat(student.getTgChatId()).isEqualTo(-10081112233L);
        verify(studentRepository).save(student);
    }

    @Test
    @DisplayName("Morning focus text message routes to handleMorningFocusMessage and logs FOCUS_SET")
    void routeIncomingMessage_morningFocusText() {
        User studentUser = User.builder().id(71010101L).firstName("Милана").isBot(false).userName("milana_gorohova").build();
        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder().messageId(16).from(studentUser).chat(chat).text("Сегодня я буду делать рилсы").build();

        Student student = Student.builder().id(1L).fullName("Милана Горохова").tgChatId(71010101L).build();
        when(studentRepository.findByTgChatId(71010101L)).thenReturn(Optional.of(student));

        router.routeIncomingMessage(message, 104L, "MINI_GROUP");

        assertThat(recordedStudent).isNotNull();
        assertThat(recordedFocusText).isEqualTo("Сегодня я буду делать рилсы");

        ArgumentCaptor<AuditMessageLog> captor = ArgumentCaptor.forClass(AuditMessageLog.class);
        verify(auditMessageLogRepository).save(captor.capture());
        assertThat(captor.getValue().getProcessingStatus()).isEqualTo("FOCUS_SET");
    }

    @Test
    @DisplayName("Mentor command /задание sets daily task for student in group chat")
    void routeIncomingMessage_mentorSetsTask() {
        User ksenia = User.builder().id(11223344L).firstName("Ксения").isBot(false).userName("ksenia_mentor").build();
        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder().messageId(17).from(ksenia).chat(chat).text("/задание Снять 2 рилса и сделать 20 рассылок").build();

        Student student = Student.builder().id(1L).fullName("Милана Горохова").tgChatId(-10071010101L).build();
        when(studentRepository.findByTgChatId(-10071010101L)).thenReturn(Optional.of(student));

        router.routeIncomingMessage(message, 105L, "MINI_GROUP");

        assertThat(recordedStudent).isEqualTo(student);
        assertThat(recordedFocusText).contains("Задача от наставника Ксения: Снять 2 рилса и сделать 20 рассылок");
    }

    @Test
    @DisplayName("Student command /task sets daily focus")
    void routeIncomingMessage_studentTaskCommand() {
        User studentUser = User.builder().id(71010101L).firstName("Милана").isBot(false).userName("milana_gorohova").build();
        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder().messageId(18).from(studentUser).chat(chat).text("/task Выложить сторителлинг с прогревом").build();

        Student student = Student.builder().id(1L).fullName("Милана Горохова").tgChatId(71010101L).build();
        when(studentRepository.findByTgChatId(71010101L)).thenReturn(Optional.of(student));

        router.routeIncomingMessage(message, 106L, "MINI_GROUP");

        assertThat(recordedStudent).isNotNull();
        assertThat(recordedFocusText).isEqualTo("/task Выложить сторителлинг с прогревом");
    }
}
