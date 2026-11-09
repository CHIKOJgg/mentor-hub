package com.mentorhub.telegram.antifraud;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.ReelSubmission;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.ReelSubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReelsAntiFraudEngineTest {

    private final ReelsAccessibilityProbe stubProbe = new ReelsAccessibilityProbe() {
        @Override
        public ProbeResult verifyAccessibility(String canonicalUrl) {
            return new ProbeResult(true, 200, null);
        }
    };

    @Test
    @DisplayName("Canonical shortcode regex extracts shortcodes from diverse URL formats and strips query params")
    void extractReels_variousUrlFormats() {
        ReelSubmissionRepository repo = mock(ReelSubmissionRepository.class);
        ReelsAntiFraudEngine engine = new ReelsAntiFraudEngine(repo, stubProbe);

        String sampleText = """
            Сдаю отчет за сегодня:
            1. Основной ролик: https://www.instagram.com/reel/DC194kaP91m/?igsh=MWQ1ZGUxMzBkMA==
            2. Короткий домен: https://instagr.am/p/C-123456789/?utm_source=ig_web_copy_link
            3. Ссылка из шеринга: https://instagram.com/share/reel/ABC_xyz-99/
            """;

        List<ReelsAntiFraudEngine.ExtractedReel> extracted = engine.extractReels(sampleText);

        assertThat(extracted).hasSize(3);

        assertThat(extracted.get(0).shortcode()).isEqualTo("DC194kaP91m");
        assertThat(extracted.get(0).canonicalUrl()).isEqualTo("https://www.instagram.com/reel/DC194kaP91m/");

        assertThat(extracted.get(1).shortcode()).isEqualTo("C-123456789");
        assertThat(extracted.get(1).canonicalUrl()).isEqualTo("https://www.instagram.com/reel/C-123456789/");

        assertThat(extracted.get(2).shortcode()).isEqualTo("ABC_xyz-99");
        assertThat(extracted.get(2).canonicalUrl()).isEqualTo("https://www.instagram.com/reel/ABC_xyz-99/");
    }

    @Test
    @DisplayName("SHA-256 calculation produces 64-character hex hash")
    void calculateHash() {
        ReelsAntiFraudEngine engine = new ReelsAntiFraudEngine(mock(ReelSubmissionRepository.class), stubProbe);

        String hash = engine.calculateHash("DC194kaP91m");
        assertThat(hash).isNotNull().hasSize(64);
        assertThat(hash).isEqualTo(engine.calculateHash("DC194kaP91m"));
    }

    @Test
    @DisplayName("Same student duplicate marks isDuplicate=true without fraud alert (yellow warning)")
    void evaluateReel_sameStudentDuplicate() {
        ReelSubmissionRepository repo = mock(ReelSubmissionRepository.class);

        Student student = Student.builder().id(1L).fullName("Милана Горохова").build();
        DailyReport prevReport = DailyReport.builder()
                .id(10L)
                .student(student)
                .reportDate(LocalDate.of(2026, 11, 4))
                .build();

        ReelSubmission existing = ReelSubmission.builder()
                .id(100L)
                .report(prevReport)
                .shortcode("DC194kaP91m")
                .reelHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .build();

        when(repo.findByReelHash(anyString())).thenReturn(Optional.of(existing));

        ReelsAntiFraudEngine engine = new ReelsAntiFraudEngine(repo, stubProbe);
        ReelsAntiFraudEngine.AntiFraudEvaluation eval = engine.evaluateReel(
                "https://www.instagram.com/reel/DC194kaP91m/",
                "DC194kaP91m",
                "https://www.instagram.com/reel/DC194kaP91m/",
                student
        );

        assertThat(eval.isDuplicate()).isTrue();
        assertThat(eval.isCrossStudentFraud()).isFalse();
        assertThat(eval.fraudAlertMessage()).isNull();
        assertThat(eval.duplicateDate()).contains("04");
    }

    @Test
    @DisplayName("Cross-student submission triggers critical plagiarism alert (red alert)")
    void evaluateReel_crossStudentPlagiarism() {
        ReelSubmissionRepository repo = mock(ReelSubmissionRepository.class);

        Student originalStudent = Student.builder().id(1L).fullName("Милана Горохова").build();
        Student plagiarist = Student.builder().id(2L).fullName("Аня Петрова").build();

        DailyReport originalReport = DailyReport.builder()
                .id(10L)
                .student(originalStudent)
                .reportDate(LocalDate.of(2026, 11, 4))
                .build();

        ReelSubmission existing = ReelSubmission.builder()
                .id(100L)
                .report(originalReport)
                .shortcode("DC194kaP91m")
                .reelHash("somehash")
                .build();

        when(repo.findByReelHash(anyString())).thenReturn(Optional.of(existing));

        ReelsAntiFraudEngine engine = new ReelsAntiFraudEngine(repo, stubProbe);
        ReelsAntiFraudEngine.AntiFraudEvaluation eval = engine.evaluateReel(
                "https://www.instagram.com/reel/DC194kaP91m/",
                "DC194kaP91m",
                "https://www.instagram.com/reel/DC194kaP91m/",
                plagiarist
        );

        assertThat(eval.isDuplicate()).isTrue();
        assertThat(eval.isCrossStudentFraud()).isTrue();
        assertThat(eval.fraudAlertMessage())
                .contains("ФРОД: ролик сдан ученицей Милана Горохова")
                .contains("04");
    }

    @Test
    @DisplayName("Multiple duplicates in database safely resolve earliest submission without IncorrectResultSizeDataAccessException")
    void evaluateReel_multipleDuplicatesInDbSafelyHandled() {
        ReelSubmissionRepository repo = mock(ReelSubmissionRepository.class);

        Student originalStudent = Student.builder().id(1L).fullName("Милана Горохова").build();
        Student student3 = Student.builder().id(3L).fullName("Карина Волкова").build();

        DailyReport originalReport = DailyReport.builder()
                .id(10L)
                .student(originalStudent)
                .reportDate(LocalDate.of(2026, 11, 4))
                .build();

        ReelSubmission earliestSubmission = ReelSubmission.builder()
                .id(100L)
                .report(originalReport)
                .shortcode("DC194kaP91m")
                .reelHash("somehash")
                .build();

        when(repo.findFirstByReelHashOrderByCheckedAtAsc(anyString())).thenReturn(Optional.of(earliestSubmission));

        ReelsAntiFraudEngine engine = new ReelsAntiFraudEngine(repo, stubProbe);
        ReelsAntiFraudEngine.AntiFraudEvaluation eval = engine.evaluateReel(
                "https://www.instagram.com/reel/DC194kaP91m/",
                "DC194kaP91m",
                "https://www.instagram.com/reel/DC194kaP91m/",
                student3
        );

        assertThat(eval.isDuplicate()).isTrue();
        assertThat(eval.isCrossStudentFraud()).isTrue();
        assertThat(eval.duplicateOf()).isEqualTo(earliestSubmission);
    }
}
