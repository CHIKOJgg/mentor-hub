package com.mentorhub.ai;

import com.mentorhub.ai.dto.ExtractedReportDto;
import com.mentorhub.ai.service.DeterministicAiFallbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAiFallbackServiceTest {

    private final DeterministicAiFallbackService fallbackService = new DeterministicAiFallbackService();

    @Test
    @DisplayName("Normalizes sales amounts with thousands separators and 'k' notation")
    void normalizeSalesAmount_variousFormats() {
        // Grouping separator with dot
        assertThat(fallbackService.normalizeSalesAmount("35.500 руб"))
                .isEqualByComparingTo(new BigDecimal("35500.00"));

        // Grouping separator with space
        assertThat(fallbackService.normalizeSalesAmount("45 000 руб"))
                .isEqualByComparingTo(new BigDecimal("45000.00"));

        // Russian 'к' notation
        assertThat(fallbackService.normalizeSalesAmount("80к"))
                .isEqualByComparingTo(new BigDecimal("80000.00"));

        // English 'k' notation
        assertThat(fallbackService.normalizeSalesAmount("150k (предоплата)"))
                .isEqualByComparingTo(new BigDecimal("150000.00"));

        // Plain integer
        assertThat(fallbackService.normalizeSalesAmount("25000"))
                .isEqualByComparingTo(new BigDecimal("25000.00"));
    }

    @Test
    @DisplayName("Extracts mailings count correctly from hashtag and natural report text")
    void extractMailingsCount() {
        assertThat(fallbackService.extractMailingsCount("Сегодня сделала #рассылки 25 по теплой базе"))
                .isEqualTo(25);
        assertThat(fallbackService.extractMailingsCount("#рассылка 40"))
                .isEqualTo(40);
        assertThat(fallbackService.extractMailingsCount("#отчет 10 рассылок 3 рилса 5 публикаций"))
                .isEqualTo(10);
        assertThat(fallbackService.extractMailingsCount("просто текст без рассылок"))
                .isEqualTo(0);
    }

    @Test
    @DisplayName("Extracts reels and posts count from summary report #отчет")
    void extractCountsFromSummaryReport() {
        String report = "#отчет 10 рассылок 3 рилса 5 публикаций";
        assertThat(fallbackService.extractMailingsCount(report)).isEqualTo(10);
        assertThat(fallbackService.extractReelsCount(report)).isEqualTo(3);
        assertThat(fallbackService.extractPostsCount(report)).isEqualTo(5);

        String saleMsg = "#продажа 50BYN";
        assertThat(fallbackService.extractSalesAmount(saleMsg)).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Recognizes questions via #вопрос and direct address 'Ксения, ...'")
    void extractQuestion() {
        assertThat(fallbackService.extractQuestion("Отчет за день. #вопрос Клиентка просит скидку 30%, как отработать?"))
                .contains("Клиентка просит скидку 30%");

        assertThat(fallbackService.extractQuestion("Ксения, подскажи пожалуйста как лучше составить оффер?"))
                .contains("подскажи пожалуйста как лучше составить оффер?");

        assertThat(fallbackService.extractQuestion("Ксюша, проверь пожалуйста оферту"))
                .contains("проверь пожалуйста оферту");
    }

    @Test
    @DisplayName("Detects burnout keywords and flags emotional status as BURNOUT_ALERT")
    void detectBurnout_keywords() {
        assertThat(fallbackService.detectBurnout("Очень устала, руки опустились, ничего не получается"))
                .isEqualTo("BURNOUT_ALERT");

        assertThat(fallbackService.detectBurnout("Сегодня выгорела, рыдала полдня, нет сил"))
                .isEqualTo("BURNOUT_ALERT");

        assertThat(fallbackService.detectBurnout("Хороший рабочий день, закрыла продажу, полна энергии"))
                .isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("Formats structured 3-bullet reflection and guarantees 0 report loss")
    void extractFacts_structuredReflection() {
        String message = """
            #рассылки 30
            #продажи 45 000 руб
            #вопрос Стоит ли брать клиентку на рассрочку от себя?
            Сегодня очень устала и нет сил, но доделала план.
            """;

        ExtractedReportDto dto = fallbackService.extractFacts(message, "Милана Горохова");

        assertThat(dto.mailingsCount()).isEqualTo(30);
        assertThat(dto.salesAmount()).isEqualByComparingTo(new BigDecimal("45000.00"));
        assertThat(dto.mentorQuestion()).contains("Стоит ли брать клиентку");
        assertThat(dto.emotionalStatus()).isEqualTo("BURNOUT_ALERT");

        assertThat(dto.reflectionSummary()).isNotNull();
        assertThat(dto.reflectionSummary().emotions()).contains("выгорания");
        assertThat(dto.reflectionSummary().actions()).contains("30");
        assertThat(dto.reflectionSummary().questionToMentor()).contains("рассрочку");

        assertThat(dto.aiSummary()).contains("30").contains("45000");
    }

    @Test
    @DisplayName("Extracts sales amount from #предоплата, #чек, #оплата, #продажа with various currency formats")
    void extractSalesAmount_variousHashtagsAndCurrencies() {
        // #предоплата
        assertThat(fallbackService.extractSalesAmount("#предоплата 50BYN"))
                .isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(fallbackService.extractSalesAmount("#предоплата 15000"))
                .isEqualByComparingTo(new BigDecimal("15000.00"));

        // #чек
        assertThat(fallbackService.extractSalesAmount("#чек 100 BYN"))
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(fallbackService.extractSalesAmount("#чек: 25000"))
                .isEqualByComparingTo(new BigDecimal("25000.00"));

        // Preceding currency symbol ($50, €100, ₽20000)
        assertThat(fallbackService.extractSalesAmount("#продажа $50"))
                .isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(fallbackService.extractSalesAmount("#оплата €100"))
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(fallbackService.extractSalesAmount("#продажа ₽20000"))
                .isEqualByComparingTo(new BigDecimal("20000.00"));

        // Hyphenated format and preposition "на"
        assertThat(fallbackService.extractSalesAmount("#продажа - 50 BYN"))
                .isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(fallbackService.extractSalesAmount("1 продажа на 50 BYN"))
                .isEqualByComparingTo(new BigDecimal("50.00"));

        // Decimals with comma
        assertThat(fallbackService.extractSalesAmount("#продажа 50,00 BYN"))
                .isEqualByComparingTo(new BigDecimal("50.00"));

        // Prompt audited natural and hashtag variations: 70 бун, 270BYN, 45, 60000, 50000 руб
        assertThat(fallbackService.extractSalesAmount("#продажа 70 бун"))
                .isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(fallbackService.extractSalesAmount("#продажа 270BYN"))
                .isEqualByComparingTo(new BigDecimal("270.00"));
        assertThat(fallbackService.extractSalesAmount("#продажа 45"))
                .isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(fallbackService.extractSalesAmount("#продажи 60000"))
                .isEqualByComparingTo(new BigDecimal("60000.00"));
        assertThat(fallbackService.extractSalesAmount("70 бун"))
                .isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(fallbackService.extractSalesAmount("50000 руб"))
                .isEqualByComparingTo(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("Extracts mailings count correctly from #трафик, #диалоги, and natural phrases")
    void extractMailingsCount_trafficAndDialogs() {
        // #трафик
        assertThat(fallbackService.extractMailingsCount("#трафик 25"))
                .isEqualTo(25);
        assertThat(fallbackService.extractMailingsCount("сегодня 30 трафик по теплой базе"))
                .isEqualTo(30);

        // #диалоги
        assertThat(fallbackService.extractMailingsCount("#диалоги 10"))
                .isEqualTo(10);
        assertThat(fallbackService.extractMailingsCount("отработано 15 диалогов"))
                .isEqualTo(15);
    }

    @Test
    @DisplayName("Extract facts respects student currency parameter")
    void extractFacts_respectsCurrency() {
        String msg = "#продажа 100\n#рассылки 20";
        ExtractedReportDto dtoUsd = fallbackService.extractFacts(msg, "Даша", "USD");
        assertThat(dtoUsd.aiSummary()).contains("USD");

        ExtractedReportDto dtoByn = fallbackService.extractFacts(msg, "Даша", "BYN");
        assertThat(dtoByn.aiSummary()).contains("BYN");
    }
}
