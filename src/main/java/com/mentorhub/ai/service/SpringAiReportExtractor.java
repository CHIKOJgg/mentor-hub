package com.mentorhub.ai.service;

import com.mentorhub.ai.dto.ExtractedReportDto;
import com.mentorhub.dto.ReflectionSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.*;

@Slf4j
@Service
public class SpringAiReportExtractor {

    private static final long AI_CHAT_TIMEOUT_MS = 15000L;
    private static final long AI_WHISPER_TIMEOUT_MS = 15000L;

    private final DeterministicAiFallbackService fallbackService;
    private final ChatClient chatClient;
    private final OpenAiAudioTranscriptionModel transcriptionModel;

    @Value("${spring.ai.openai.api-key:mock-key}")
    private String apiKey;

    private final ExecutorService aiExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public SpringAiReportExtractor(
            DeterministicAiFallbackService fallbackService,
            @Autowired(required = false) ChatClient.Builder chatClientBuilder,
            @Autowired(required = false) OpenAiAudioTranscriptionModel transcriptionModel
    ) {
        this.fallbackService = fallbackService;
        this.chatClient = (chatClientBuilder != null) ? chatClientBuilder.build() : null;
        this.transcriptionModel = transcriptionModel;
    }

    // Direct constructor for unit testing
    public SpringAiReportExtractor(
            DeterministicAiFallbackService fallbackService,
            ChatClient chatClient,
            OpenAiAudioTranscriptionModel transcriptionModel,
            String apiKey
    ) {
        this.fallbackService = fallbackService;
        this.chatClient = chatClient;
        this.transcriptionModel = transcriptionModel;
        this.apiKey = apiKey;
    }

    public boolean isAiConfigured() {
        return StringUtils.hasText(apiKey) &&
                !apiKey.equalsIgnoreCase("mock-key") &&
                !apiKey.equalsIgnoreCase("test") &&
                chatClient != null;
    }

    /**
     * Transcribes audio (.oga) or video note (.mp4) into text using Whisper STT.
     */
    public String transcribeAudio(byte[] audioBytes, String filename) {
        if (audioBytes == null || audioBytes.length == 0) {
            return "";
        }

        if (!isAiConfigured() || transcriptionModel == null) {
            log.info("Whisper STT skipped (offline/mock AI key). Filename: {}", filename);
            return "[Голосовое сообщение / кружочек: расшифровка в офлайн-режиме]";
        }

        Future<String> future = null;
        try {
            Resource resource = new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return filename != null ? filename : "audio.oga";
                }
            };

            OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                    .language("ru")
                    .temperature(0.2f)
                    .build();

            AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);

            future = aiExecutor.submit(() -> {
                AudioTranscriptionResponse response = transcriptionModel.call(prompt);
                if (response != null && response.getResult() != null) {
                    return response.getResult().getOutput();
                }
                return "";
            });

            return future.get(AI_WHISPER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            if (future != null) future.cancel(true);
            log.warn("Whisper STT timed out (>{} ms) for {}", AI_WHISPER_TIMEOUT_MS, filename);
            return "[Таймаут транскрибации Whisper: отчёт сохранён]";
        } catch (Exception ex) {
            log.warn("Whisper STT failed for {}: {}", filename, ex.getMessage());
            return "[Сбой транскрибации: " + ex.getMessage() + "]";
        }
    }

    /**
     * Extracts structured 3-bullet reflection and facts using Spring AI ChatClient.
     * Guaranteed 0 report loss via automatic fallback to DeterministicAiFallbackService.
     */
    public ExtractedReportDto extractFacts(String rawMessage, String studentName) {
        if (!isAiConfigured()) {
            log.debug("Using deterministic AI fallback for student '{}' (API key is mock/disabled)", studentName);
            return fallbackService.extractFacts(rawMessage, studentName);
        }

        Future<ExtractedReportDto> future = null;
        try {
            future = aiExecutor.submit(() -> callChatClient(rawMessage, studentName));
            return future.get(AI_CHAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            if (future != null) future.cancel(true);
            log.warn("Spring AI ChatClient timed out (>{} ms) for student '{}'. Falling back to deterministic parser.",
                    AI_CHAT_TIMEOUT_MS, studentName);
            return fallbackService.extractFacts(rawMessage, studentName);
        } catch (Exception ex) {
            log.warn("Spring AI ChatClient error for student '{}': {}. Falling back to deterministic parser.",
                    studentName, ex.getMessage());
            return fallbackService.extractFacts(rawMessage, studentName);
        }
    }

    private ExtractedReportDto callChatClient(String rawMessage, String studentName) {
        String systemPrompt = """
            Ты — высококвалифицированный бизнес-ассистент наставника предпринимателей Ксении.
            Твоя задача — строго и объективно разобрать входящее сообщение ученицы (%s).

            ИНСТРУКЦИЯ ПО ОБРАБОТКЕ:
            1. СТРУКТУРИРОВАННАЯ РЕФЛЕКСИЯ (СТРОГО 3 БУЛЛЕТА):
               - emotions: опиши текущее состояние ученицы (уверенность, стресс, апатия, страх продаж, воодушевление).
               - actions: конкретные действия и результаты за сегодня (рассылки, черновики Reels/каруселей, созвоны).
               - questionToMentor: точный вопрос или затык, адресованный Ксении (или null, если вопроса нет).
            2. ДЕТЕКЦИЯ ВЫГОРАНИЯ:
               Если в тексте присутствуют фразы апатии, бессилия или слез ("рыдала", "не могу встать", "все бросить", "нет сил"),
               выстави emotionalStatus = "BURNOUT_ALERT". В обычном состоянии — "NORMAL".
            3. ИЗВЛЕЧЕНИЕ ЦИФР И ЧЕРНОВИКОВ:
               - Сумма продаж (в рублях). При суммах "45 000" или "80к" нормализуй в число.
               - Число отправленных рассылок Direct.
               - Черновики контента (#контент, #рилс, #карусель, #сторителл).
            4. ОТСЕЧЕНИЕ ФЛУДА:
               Удали все бытовые подробности (погода, ремонт, кошки, пробки).
            5. ИТОГОВАЯ ВЫЖИМКА (aiSummary):
               Сформируй 1-2 емких предложения для быстрого просмотра Ксенией на iPad.

            Верни результат строго в формате JSON, соответствующем схеме ExtractedReportDto.
            """.formatted(studentName);

        try {
            ExtractedReportDto result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(rawMessage)
                    .call()
                    .entity(ExtractedReportDto.class);

            if (result != null) {
                return result;
            }
        } catch (Exception ex) {
            log.warn("Primary AI chat model call failed: {}. Trying active Groq model fallbacks...", ex.getMessage());
            java.util.List<String> fallbacks = java.util.List.of("openai/gpt-oss-20b", "llama-3.1-8b-instant", "qwen/qwen3.8-27b");
            for (String fallbackModel : fallbacks) {
                try {
                    log.info("Attempting AI model fallback '{}' for student '{}'...", fallbackModel, studentName);
                    ExtractedReportDto fallbackResult = chatClient.prompt()
                            .system(systemPrompt)
                            .user(rawMessage)
                            .options(OpenAiChatOptions.builder().model(fallbackModel).temperature(0.2).build())
                            .call()
                            .entity(ExtractedReportDto.class);
                    if (fallbackResult != null) {
                        log.info("AI model fallback '{}' succeeded for student '{}'", fallbackModel, studentName);
                        return fallbackResult;
                    }
                } catch (Exception fallbackEx) {
                    log.warn("AI fallback model '{}' failed: {}", fallbackModel, fallbackEx.getMessage());
                }
            }
        }

        return fallbackService.extractFacts(rawMessage, studentName);
    }
}
