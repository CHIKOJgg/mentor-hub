package com.mentorhub.ai;

import com.mentorhub.ai.dto.ExtractedReportDto;
import com.mentorhub.ai.service.DeterministicAiFallbackService;
import com.mentorhub.ai.service.SpringAiReportExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiReportExtractorTest {

    @Test
    @DisplayName("When AI API key is mock-key, automatically falls back to DeterministicAiFallbackService")
    void extractFacts_mockApiKeyFallback() {
        DeterministicAiFallbackService fallbackService = new DeterministicAiFallbackService();

        SpringAiReportExtractor extractor = new SpringAiReportExtractor(
                fallbackService,
                null,
                null,
                "mock-key"
        );

        assertThat(extractor.isAiConfigured()).isFalse();

        ExtractedReportDto dto = extractor.extractFacts("#рассылки 15 #продажи 80к", "Аня Петрова");

        assertThat(dto.mailingsCount()).isEqualTo(15);
        assertThat(dto.salesAmount()).isEqualByComparingTo("80000.00");
    }

    @Test
    @DisplayName("Whisper STT in offline mode returns safe placeholder without throwing exception")
    void transcribeAudio_offlineMode() {
        DeterministicAiFallbackService fallbackService = new DeterministicAiFallbackService();
        SpringAiReportExtractor extractor = new SpringAiReportExtractor(
                fallbackService,
                null,
                null,
                "mock-key"
        );

        String result = extractor.transcribeAudio(new byte[]{1, 2, 3}, "voice.oga");
        assertThat(result).contains("офлайн-режиме");
    }
}
