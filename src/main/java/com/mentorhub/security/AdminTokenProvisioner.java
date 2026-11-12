package com.mentorhub.security;

import com.mentorhub.entity.AdminToken;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.repository.AdminTokenRepository;
import com.mentorhub.repository.MentorSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AdminTokenProvisioner implements ApplicationRunner {
    private static final Set<String> PUBLIC_DEVELOPMENT_TOKENS = Set.of(
            "magic-ksenia-mentor-token-998877",
            "magic-katya-curator-token-112233"
    );

    private final AdminTokenRepository repository;
    private final MentorSettingRepository mentorSettingRepository;

    @Value("${app.admin.mentor-token:}")
    private String mentorToken;

    @Value("${app.admin.curator-token:}")
    private String curatorToken;

    @Value("${app.admin.mentor-telegram-user-id:}")
    private String mentorTelegramUserId;

    @Value("${app.admin.curator-telegram-user-id:}")
    private String curatorTelegramUserId;

    @Value("${telegram.bot.enabled:false}")
    private boolean telegramEnabled;

    @Value("${telegram.bot.token:}")
    private String telegramBotToken;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        requireStrongToken(mentorToken, "ADMIN_MENTOR_TOKEN");
        requireStrongToken(curatorToken, "ADMIN_CURATOR_TOKEN");
        if (mentorToken.trim().equals(curatorToken.trim())) {
            throw new IllegalStateException("ADMIN_MENTOR_TOKEN and ADMIN_CURATOR_TOKEN must be different");
        }

        Long mentorTgId = parseOptionalTelegramUserId(mentorTelegramUserId, "ADMIN_MENTOR_TELEGRAM_USER_ID");
        Long curatorTgId = parseOptionalTelegramUserId(curatorTelegramUserId, "ADMIN_CURATOR_TELEGRAM_USER_ID");

        if (mentorTgId == null) {
            mentorTgId = mentorSettingRepository.findFirstByOrderByIdAsc()
                    .map(MentorSetting::getMentorTgId)
                    .orElse(11223344L);
        }

        if (telegramEnabled) {
            if (mentorTelegramUserId == null || mentorTelegramUserId.isBlank()) {
                log.warn("ADMIN_MENTOR_TELEGRAM_USER_ID is not configured. Defaulting to {} for echo filtering.", mentorTgId);
            }
            if (curatorTelegramUserId == null || curatorTelegramUserId.isBlank()) {
                log.warn("ADMIN_CURATOR_TELEGRAM_USER_ID is not configured. Curator echo filtering will use configured admin tokens.");
            }
        }
        if (telegramEnabled && (!org.springframework.util.StringUtils.hasText(telegramBotToken)
                || telegramBotToken.startsWith("mock"))) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN must be configured when TELEGRAM_BOT_ENABLED=true");
        }

        provision("Ксения", "MENTOR", mentorToken, mentorTgId);
        provision("Катя", "CURATOR", curatorToken, curatorTgId);
        if (mentorTgId != null) provisionMentorTelegramIdentity(mentorTgId);

        Set<String> configuredHashes = Set.of(AdminTokenHasher.hash(mentorToken), AdminTokenHasher.hash(curatorToken));
        repository.findAll().stream()
                .filter(token -> Boolean.TRUE.equals(token.getIsActive()))
                .filter(token -> !configuredHashes.contains(token.getToken()))
                .forEach(token -> {
                    token.setIsActive(false);
                    repository.save(token);
                });
    }

    private void requireStrongToken(String token, String propertyName) {
        if (token == null || token.isBlank() || token.trim().length() < 32
                || token.startsWith("change-me") || token.startsWith("replace-with")
                || PUBLIC_DEVELOPMENT_TOKENS.contains(token.trim())) {
            throw new IllegalStateException(propertyName + " must be set to a unique random value of at least 32 characters");
        }
    }

    private Long requireTelegramUserId(String value, String propertyName) {
        Long id = parseOptionalTelegramUserId(value, propertyName);
        if (id == null) throw new IllegalStateException(propertyName + " is required when Telegram is enabled");
        return id;
    }

    private Long parseOptionalTelegramUserId(String value, String propertyName) {
        if (value == null || value.isBlank()) return null;
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) throw new NumberFormatException("must be positive");
            return id;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(propertyName + " must be a positive Telegram user ID", ex);
        }
    }

    private void provision(String name, String role, String token, Long telegramUserId) {

        List<AdminToken> candidates = repository.findAll().stream()
                .filter(existing -> name.equals(existing.getAdminName()))
                .toList();
        AdminToken record = candidates.stream().findFirst().orElseGet(() -> AdminToken.builder()
                .adminName(name)
                .role(role)
                .token(AdminTokenHasher.hash(token))
                .isActive(true)
                .build());
        record.setRole(role);
        record.setToken(AdminTokenHasher.hash(token));
        record.setIsActive(true);
        if (telegramUserId != null) record.setTgUserId(telegramUserId);
        AdminToken savedRecord = repository.save(record);
        candidates.stream()
                .filter(existing -> !Objects.equals(existing.getId(), savedRecord.getId()))
                .filter(existing -> Boolean.TRUE.equals(existing.getIsActive()))
                .forEach(existing -> {
                    existing.setIsActive(false);
                    repository.save(existing);
                });
    }

    private void provisionMentorTelegramIdentity(Long mentorTgId) {
        MentorSetting setting = mentorSettingRepository.findByMentorName("Ксения")
                .orElseGet(() -> MentorSetting.builder()
                        .mentorTgId(mentorTgId)
                        .mentorName("Ксения")
                        .build());
        setting.setMentorTgId(mentorTgId);
        mentorSettingRepository.save(setting);
    }
}
