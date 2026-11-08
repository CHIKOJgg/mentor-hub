package com.mentorhub.ai.service;

import com.mentorhub.ai.dto.ExtractedReportDto;
import com.mentorhub.dto.ReflectionSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DeterministicAiFallbackService {

    private static final Pattern MAILINGS_PATTERN = Pattern.compile(
            "#(?i)(?:рассылки|рассылка)[:\\s]*(\\d+)"
    );

    private static final Pattern SALES_PATTERN = Pattern.compile(
            "#(?i)(?:продажи|продажа|оплата)[:\\s]*([0-9\\s.,kк]+)"
    );

    private static final Pattern HASHTAG_QUESTION_PATTERN = Pattern.compile(
            "#(?i)(?:вопрос|затык|проблема)[:\\s]*(.+)"
    );

    private static final Pattern DIRECT_QUESTION_PATTERN = Pattern.compile(
            "(?ui)(?:^|\\s)(?:Ксения|Ксюша|Ксюш|@ksenia)(?:,\\s*|\\s+)(?<q>[^\\n#]+)"
    );

    private static final Pattern CLIENTS_PATTERN = Pattern.compile(
            "#(?i)(?:клиенты|клиент)[:\\s]*(\\d+)"
    );

    private static final List<String> BURNOUT_KEYWORDS = List.of(
            "выгорела", "выгорание", "выгореть", "устала", "руки опустились",
            "нет сил", "рыдала", "плакала", "все бросить", "всё бросить",
            "не могу встать", "апатия", "депрессия", "тяжело держаться"
    );

    public ExtractedReportDto extractFacts(String rawMessage, String studentName) {
        if (!StringUtils.hasText(rawMessage)) {
            return new ExtractedReportDto(
                    List.of(),
                    BigDecimal.ZERO,
                    0,
                    0,
                    null,
                    "NORMAL",
                    "Нет данных в сообщении",
                    new ReflectionSummaryDto("Состояние не указано", "Нет действий", null),
                    null
            );
        }

        Integer mailings = extractMailingsCount(rawMessage);
        BigDecimal sales = extractSalesAmount(rawMessage);
        Integer clients = extractNewClientsCount(rawMessage);
        String question = extractQuestion(rawMessage);
        String emotionalStatus = detectBurnout(rawMessage);

        String emotionsBullet;
        if ("BURNOUT_ALERT".equals(emotionalStatus)) {
            emotionsBullet = "Обнаружены маркеры выгорания / апатии (детерминированный фолбэк)";
        } else {
            emotionsBullet = "Состояние рабочее (детерминированный фолбэк)";
        }

        String actionsBullet = String.format(Locale.ROOT,
                "Обработано рассылок: %d, сумма продаж: %s ₽",
                mailings != null ? mailings : 0,
                sales != null ? sales.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00"
        );

        ReflectionSummaryDto reflectionSummary = new ReflectionSummaryDto(
                emotionsBullet,
                actionsBullet,
                question
        );

        String aiSummary = String.format(Locale.ROOT,
                "Режим офлайн/мок AI: обработано по хэштегам %d рассылок, продаж %s ₽",
                mailings != null ? mailings : 0,
                sales != null ? sales.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00"
        );

        log.debug("Deterministic AI fallback extracted facts for {}: mailings={}, sales={}, status={}",
                studentName, mailings, sales, emotionalStatus);

        return new ExtractedReportDto(
                new ArrayList<>(),
                sales,
                mailings,
                clients,
                question,
                emotionalStatus,
                aiSummary,
                reflectionSummary,
                null
        );
    }

    public Integer extractMailingsCount(String text) {
        if (!StringUtils.hasText(text)) return 0;
        Matcher m = MAILINGS_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public Integer extractNewClientsCount(String text) {
        if (!StringUtils.hasText(text)) return 0;
        Matcher m = CLIENTS_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public BigDecimal extractSalesAmount(String text) {
        if (!StringUtils.hasText(text)) return BigDecimal.ZERO;
        Matcher m = SALES_PATTERN.matcher(text);
        if (m.find()) {
            return normalizeSalesAmount(m.group(1));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Normalizes sales amounts with thousands separators:
     * - "35.500 руб" -> 35500.00
     * - "45 000 руб" -> 45000.00
     * - "80к" / "80k" -> 80000.00
     * - "120.000" -> 120000.00
     * - "25000" -> 25000.00
     */
    public BigDecimal normalizeSalesAmount(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO;
        }

        String cleaned = raw.trim().toLowerCase(Locale.ROOT);

        // Check for "k" or "к" notation (e.g. "80к", "80 k", "2.5k")
        if (cleaned.contains("к") || cleaned.contains("k")) {
            String numPart = cleaned.replaceAll("[^0-9.,]", "").replace(',', '.');
            if (StringUtils.hasText(numPart)) {
                try {
                    BigDecimal base = new BigDecimal(numPart);
                    return base.multiply(BigDecimal.valueOf(1000)).setScale(2, RoundingMode.HALF_UP);
                } catch (Exception ignored) {
                }
            }
        }

        // Remove words like руб, р, etc. Keep digits, spaces, dots, commas
        cleaned = cleaned.replaceAll("[^0-9.,\\s]", "").trim();

        // Check for thousands grouping: e.g. "35.500" or "45 000" or "120.000"
        // Pattern: (\d+)[.\s](\d{3})(?:[.,](\d{1,2}))?
        Pattern thousandsPattern = Pattern.compile("^(\\d+)[.\\s](\\d{3})(?:[.,](\\d{1,2}))?$");
        Matcher tm = thousandsPattern.matcher(cleaned);
        if (tm.matches()) {
            String whole = tm.group(1) + tm.group(2);
            String fraction = tm.group(3);
            String totalStr = fraction != null ? whole + "." + fraction : whole;
            try {
                return new BigDecimal(totalStr).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {
            }
        }

        // Remove all spaces
        cleaned = cleaned.replace(" ", "");

        // If format is standard "12345.67" or "12345,67"
        if (cleaned.contains(",")) {
            cleaned = cleaned.replace(',', '.');
        }

        try {
            return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.debug("Could not parse sales amount from '{}', defaulting to 0", raw);
            return BigDecimal.ZERO;
        }
    }

    public String extractQuestion(String text) {
        if (!StringUtils.hasText(text)) return null;

        Matcher hm = HASHTAG_QUESTION_PATTERN.matcher(text);
        if (hm.find()) {
            String q = hm.group(1).trim();
            if (StringUtils.hasText(q)) {
                return q;
            }
        }

        Matcher dm = DIRECT_QUESTION_PATTERN.matcher(text);
        if (dm.find()) {
            String q = dm.group("q").trim();
            if (StringUtils.hasText(q)) {
                return q;
            }
        }

        return null;
    }

    public String detectBurnout(String text) {
        if (!StringUtils.hasText(text)) return "NORMAL";
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : BURNOUT_KEYWORDS) {
            if (lower.contains(kw)) {
                return "BURNOUT_ALERT";
            }
        }
        return "NORMAL";
    }
}
