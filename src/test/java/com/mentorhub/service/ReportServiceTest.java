package com.mentorhub.service;

import com.mentorhub.dto.ApproveReportRequest;
import com.mentorhub.dto.DailyReportResponse;
import com.mentorhub.dto.RejectReportRequest;
import com.mentorhub.dto.ReportActionResponse;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.repository.DailyReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Test
    @DisplayName("getInboxQueue returns seeded reports with content drafts and AI reflections")
    void testGetInboxQueue() {
        List<DailyReportResponse> queue = reportService.getInboxQueue(null, "PENDING");
        assertThat(queue).hasSize(3);

        // Verify Report 1 (Milana)
        DailyReportResponse rep1 = queue.stream()
                .filter(r -> "Милана Горохова".equals(r.studentName()))
                .findFirst().orElseThrow();

        assertThat(rep1.status()).isEqualTo("PENDING");
        assertThat(rep1.isOnTime()).isTrue();
        assertThat(rep1.salesAmount()).isEqualByComparingTo("45000.00");
        assertThat(rep1.mailingsCount()).isEqualTo(25);
        assertThat(rep1.newClientsCount()).isEqualTo(2);
        assertThat(rep1.contentDrafts()).hasSize(2);
        assertThat(rep1.reels()).hasSize(2);
        assertThat(rep1.mentorQuestion()).contains("рассрочку");
        assertThat(rep1.reflectionSummary()).isNotNull();
        assertThat(rep1.reflectionSummary().emotions()).contains("Бодрое");

        // Verify Report 2 (Anya) has duplicate reel and burnout alert
        DailyReportResponse rep2 = queue.stream()
                .filter(r -> "Аня Петрова".equals(r.studentName()))
                .findFirst().orElseThrow();

        assertThat(rep2.isOnTime()).isFalse();
        assertThat(rep2.contentDrafts().get(0).isDuplicate()).isTrue();
        assertThat(rep2.contentDrafts().get(0).duplicateDate()).isEqualTo("04 ноября");
    }

    @Test
    @DisplayName("approveReport updates status to APPROVED and persists replyText")
    void testApproveReport() {
        List<DailyReport> pending = dailyReportRepository.findByStatus("PENDING");
        assertThat(pending).isNotEmpty();
        DailyReport target = pending.get(0);

        String replyText = "Отличная работа по рассылкам! С рассрочкой лучше заключить договор.";
        ReportActionResponse response = reportService.approveReport(
                target.getId(),
                new ApproveReportRequest(replyText)
        );

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reportId()).isEqualTo(String.valueOf(target.getId()));
        assertThat(response.approvedAt()).isNotNull();

        DailyReport updated = dailyReportRepository.findById(target.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("APPROVED");
        assertThat(updated.getMentorReply()).isEqualTo(replyText);
        assertThat(updated.getReplySentAt()).isNotNull();
    }

    @Test
    @DisplayName("approveReport on already approved report throws IllegalStateException")
    void testApproveAlreadyApprovedReport() {
        List<DailyReport> pending = dailyReportRepository.findByStatus("PENDING");
        DailyReport target = pending.get(0);

        reportService.approveReport(target.getId(), new ApproveReportRequest("OK"));

        assertThatThrownBy(() -> reportService.approveReport(target.getId(), new ApproveReportRequest("OK again")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already approved");
    }

    @Test
    @DisplayName("rejectReport updates status to REJECTED and persists rejection reason")
    void testRejectReport() {
        List<DailyReport> pending = dailyReportRepository.findByStatus("PENDING");
        assertThat(pending).isNotEmpty();
        DailyReport target = pending.get(0);

        String reason = "Ссылка на Reels ведет на закрытый аккаунт. Открой профиль и пришли ссылку заново.";
        ReportActionResponse response = reportService.rejectReport(
                target.getId(),
                new RejectReportRequest(reason)
        );

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.feedbackSent()).isTrue();
        assertThat(response.rejectedAt()).isNotNull();

        DailyReport updated = dailyReportRepository.findById(target.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("REJECTED");
        assertThat(updated.getMentorReply()).isEqualTo(reason);
        assertThat(updated.getReplySentAt()).isNotNull();
    }

    @Test
    @DisplayName("getReportById throws ResourceNotFoundException for unknown ID")
    void testGetReportByIdNotFound() {
        assertThatThrownBy(() -> reportService.getReportById(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
