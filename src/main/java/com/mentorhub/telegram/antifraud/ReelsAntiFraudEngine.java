package com.mentorhub.telegram.antifraud;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.ReelSubmission;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.ReelSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReelsAntiFraudEngine {

    public static final Pattern REELS_PATTERN = Pattern.compile(
            "(?i)(?<![a-zA-Z0-9.-])(?:https?://)?(?:www\\.)?(?:instagram\\.com|instagr\\.am)/(?:reel|reels|p|share/reel)/([A-Za-z0-9_-]+)"
    );

    private static final DateTimeFormatter RUSSIAN_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMMM", Locale.forLanguageTag("ru"));

    private final ReelSubmissionRepository reelSubmissionRepository;
    private final ReelsAccessibilityProbe reelsAccessibilityProbe;

    /**
     * Extracts all canonical Instagram Reel URLs from text.
     */
    public List<ExtractedReel> extractReels(String text) {
        List<ExtractedReel> reels = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return reels;
        }

        Matcher matcher = REELS_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawUrl = matcher.group(0);
            String shortcode = matcher.group(1);
            String canonicalUrl = "https://www.instagram.com/reel/" + shortcode + "/";
            reels.add(new ExtractedReel(rawUrl, shortcode, canonicalUrl));
        }
        return reels;
    }

    /**
     * Computes standard SHA-256 hash of a string.
     */
    public String calculateHash(String input) {
        if (input == null) {
            return "";
        }
        return DigestUtils.sha256Hex(input);
    }

    /**
     * Computes SHA-256 hash of raw byte payload.
     */
    public String calculateHash(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return DigestUtils.sha256Hex(bytes);
    }

    /**
     * Analyzes shortcode against known submissions and verifies accessibility.
     */
    @Transactional(readOnly = true)
    public AntiFraudEvaluation evaluateReel(String rawUrl, String shortcode, String canonicalUrl, Student currentStudent) {
        String reelHash = calculateHash(shortcode);

        Optional<ReelSubmission> existingOpt = reelSubmissionRepository.findByReelHash(reelHash);

        boolean isDuplicate = false;
        String duplicateDate = null;
        ReelSubmission duplicateOf = null;
        boolean isCrossStudentFraud = false;
        String fraudAlertMessage = null;

        if (existingOpt.isPresent()) {
            ReelSubmission existing = existingOpt.get();
            isDuplicate = true;
            duplicateOf = existing;

            DailyReport prevReport = existing.getReport();
            LocalDate prevDate = prevReport != null ? prevReport.getReportDate() : null;
            duplicateDate = prevDate != null ? prevDate.format(RUSSIAN_DATE_FORMAT) : "ранее";

            Student prevStudent = prevReport != null ? prevReport.getStudent() : null;
            if (prevStudent != null && currentStudent != null && !prevStudent.getId().equals(currentStudent.getId())) {
                isCrossStudentFraud = true;
                fraudAlertMessage = "ФРОД: ролик сдан ученицей " + prevStudent.getFullName() +
                        (prevDate != null ? " (" + duplicateDate + ")" : "");
                log.warn("CRITICAL ANTI-FRAUD ALERT: cross-student duplicate detected! Current: student #{}, Existing: student #{}. Alert: {}",
                        currentStudent.getId(), prevStudent.getId(), fraudAlertMessage);
            } else {
                log.info("Duplicate Reel submission detected for student #{}: shortcode {}, original date {}",
                        currentStudent != null ? currentStudent.getId() : null, shortcode, duplicateDate);
            }
        }

        ReelsAccessibilityProbe.ProbeResult probeResult = reelsAccessibilityProbe.verifyAccessibility(canonicalUrl);

        return new AntiFraudEvaluation(
                rawUrl,
                shortcode,
                canonicalUrl,
                reelHash,
                probeResult.isValid(),
                probeResult.httpStatus(),
                probeResult.note(),
                isDuplicate,
                duplicateDate,
                duplicateOf,
                isCrossStudentFraud,
                fraudAlertMessage
        );
    }

    public record ExtractedReel(String rawUrl, String shortcode, String canonicalUrl) {}

    public record AntiFraudEvaluation(
            String rawUrl,
            String shortcode,
            String canonicalUrl,
            String reelHash,
            boolean isValid,
            int httpStatusCode,
            String accessibilityNote,
            boolean isDuplicate,
            String duplicateDate,
            ReelSubmission duplicateOf,
            boolean isCrossStudentFraud,
            String fraudAlertMessage
    ) {}
}
