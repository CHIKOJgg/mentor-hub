package com.mentorhub.service;

import com.mentorhub.dto.AnalyticsSummaryResponse;
import com.mentorhub.dto.DayInfoResponse;
import com.mentorhub.dto.StoryCaseResponse;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final StudentRepository studentRepository;
    private final DailyReportRepository dailyReportRepository;
    private final StudentService studentService;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getAnalyticsSummary() {
        List<Student> students = studentRepository.findByIsActiveTrue();
        if (students.isEmpty()) {
            students = studentRepository.findAll();
        }

        BigDecimal totalEarned = BigDecimal.ZERO;
        BigDecimal totalInitial = BigDecimal.ZERO;
        int totalReels = 0;
        int totalCarousels = 0;
        int totalStorytells = 0;
        int totalMailings = 0;
        int totalNewClients = 0;
        BigDecimal currentTicketSum = BigDecimal.ZERO;
        BigDecimal initialTicketSum = BigDecimal.ZERO;
        double disciplineSum = 0;

        for (Student s : students) {
            if (s.getCurrentIncome() != null) totalEarned = totalEarned.add(s.getCurrentIncome());
            if (s.getInitialIncome() != null) totalInitial = totalInitial.add(s.getInitialIncome());
            if (s.getTotalReels() != null) totalReels += s.getTotalReels();
            if (s.getTotalCarousels() != null) totalCarousels += s.getTotalCarousels();
            if (s.getTotalStorytells() != null) totalStorytells += s.getTotalStorytells();
            if (s.getTotalMailings() != null) totalMailings += s.getTotalMailings();
            if (s.getTotalNewClients() != null) totalNewClients += s.getTotalNewClients();
            if (s.getCurrentAvgTicket() != null) currentTicketSum = currentTicketSum.add(s.getCurrentAvgTicket());
            if (s.getInitialAvgTicket() != null) initialTicketSum = initialTicketSum.add(s.getInitialAvgTicket());

            List<DayInfoResponse> weekDays = studentService.getDisciplineCalendar(s.getId());
            disciplineSum += studentService.calculateDisciplineRate(s, weekDays);
        }

        int count = students.isEmpty() ? 1 : students.size();
        BigDecimal currentAvgTicket = currentTicketSum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        BigDecimal initialAvgTicket = initialTicketSum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        String avgTicketGrowthFactor;
        if (initialAvgTicket.compareTo(BigDecimal.ZERO) <= 0) {
            avgTicketGrowthFactor = "x1.0";
        } else {
            double ticketRatio = currentAvgTicket.doubleValue() / initialAvgTicket.doubleValue();
            BigDecimal ratioBd = BigDecimal.valueOf(ticketRatio).setScale(1, RoundingMode.FLOOR);
            avgTicketGrowthFactor = "x" + ratioBd;
        }

        String revenueGrowthFactor;
        if (totalInitial.compareTo(BigDecimal.ZERO) <= 0) {
            revenueGrowthFactor = "+0%";
        } else {
            long pct = Math.round((totalEarned.doubleValue() - totalInitial.doubleValue()) / totalInitial.doubleValue() * 100);
            revenueGrowthFactor = "+" + pct + "%";
        }

        double averageDisciplineRate = Math.round((disciplineSum / count) * 10.0) / 10.0;

        // Active burnout alerts count
        long burnoutCount = dailyReportRepository.findByStatus("PENDING").stream()
                .filter(report -> report.getStudent() != null && Boolean.TRUE.equals(report.getStudent().getIsActive()))
                .filter(r -> "BURNOUT_ALERT".equalsIgnoreCase(r.getEmotionalStatus()))
                .count();

        // AI reflection processed count (single indexed DB query instead of loading whole table)
        long aiReflectionCount = dailyReportRepository.countByAnyReflectionNotNull();

        return new AnalyticsSummaryResponse(
                totalEarned,
                revenueGrowthFactor,
                totalReels,
                totalCarousels,
                totalStorytells,
                totalMailings,
                totalNewClients,
                currentAvgTicket,
                initialAvgTicket,
                avgTicketGrowthFactor,
                averageDisciplineRate,
                burnoutCount,
                aiReflectionCount
        );
    }

    @Transactional(readOnly = true)
    public StoryCaseResponse getStoryCase(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + studentId));

        String growthMultiplierVal = calculateMultiplier(student.getCurrentIncome(), student.getInitialIncome());
        String growthMultiplier = "x" + growthMultiplierVal;

        String growthPercent;
        if (student.getInitialIncome() == null || student.getInitialIncome().compareTo(BigDecimal.ZERO) <= 0) {
            growthPercent = "+0%";
        } else {
            double current = student.getCurrentIncome() != null ? student.getCurrentIncome().doubleValue() : 0.0;
            double initial = student.getInitialIncome().doubleValue();
            long pct = Math.round(((current - initial) / initial) * 100);
            growthPercent = "+" + pct + "%";
        }

        List<DayInfoResponse> weekDays = studentService.getDisciplineCalendar(student.getId());
        int disciplineRate = studentService.calculateDisciplineRate(student, weekDays);

        String storyCopy = generateStoryCaseText(student, growthMultiplierVal, disciplineRate);

        return new StoryCaseResponse(
                "st-" + student.getId(),
                student.getFullName(),
                student.getNiche(),
                student.getInitialIncome(),
                student.getCurrentIncome(),
                growthMultiplier,
                growthPercent,
                student.getInitialAvgTicket(),
                student.getCurrentAvgTicket(),
                student.getTotalReels() != null ? student.getTotalReels() : 0,
                student.getTotalCarousels() != null ? student.getTotalCarousels() : 0,
                student.getTotalStorytells() != null ? student.getTotalStorytells() : 0,
                student.getTotalMailings() != null ? student.getTotalMailings() : 0,
                student.getTotalNewClients() != null ? student.getTotalNewClients() : 0,
                disciplineRate,
                storyCopy
        );
    }

    public static String calculateMultiplier(BigDecimal currentIncome, BigDecimal initialIncome) {
        if (initialIncome == null || initialIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return "1.0";
        }
        if (currentIncome == null || currentIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return "1.0";
        }
        double mult = currentIncome.doubleValue() / initialIncome.doubleValue();
        return String.format(Locale.US, "%.1f", mult);
    }

    private String generateStoryCaseText(Student student, String growth, int disciplineRate) {
        return "🔥 КЕЙС ПОТОКА: " + student.getFullName() + "\n" +
                "Ниша: " + student.getNiche() + "\n\n" +
                "🚀 Точка А: " + formatRuCurrency(student.getInitialIncome()) + " ₽ ➡️ Точка Б: " + formatRuCurrency(student.getCurrentIncome()) + " ₽ (РОСТ x" + growth + "!)\n" +
                "📈 Средний чек: вырос с " + formatRuCurrency(student.getInitialAvgTicket()) + " ₽ до " + formatRuCurrency(student.getCurrentAvgTicket()) + " ₽\n" +
                "🎬 Контент: " + (student.getTotalReels() != null ? student.getTotalReels() : 0) + " проверенных Reels за 3 недели\n" +
                "✉️ Исходящий трафик: " + (student.getTotalMailings() != null ? student.getTotalMailings() : 0) + " рассылок по скрипту\n" +
                "💎 Дисциплина сдачи отчетов: " + disciplineRate + "%\n\n" +
                "«Она просто перестала сливать дедлайны и внедрила ежедневный трекинг действий»";
    }

    public static String formatRuCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        long val = Math.round(amount.doubleValue());
        return String.format(Locale.US, "%,d", val).replace(',', ' ');
    }
}
