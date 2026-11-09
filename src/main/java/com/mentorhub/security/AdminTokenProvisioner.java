package com.mentorhub.security;

import com.mentorhub.entity.AdminToken;
import com.mentorhub.repository.AdminTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AdminTokenProvisioner implements ApplicationRunner {
    private final AdminTokenRepository repository;

    @Value("${app.admin.mentor-token:}")
    private String mentorToken;

    @Value("${app.admin.curator-token:}")
    private String curatorToken;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        provision("Ксения", "MENTOR", mentorToken);
        provision("Катя", "CURATOR", curatorToken);
    }

    private void provision(String name, String role, String token) {
        if (token == null || token.isBlank() || token.startsWith("change-me")) {
            log.warn("No production admin token configured for role {}", role);
            return;
        }

        List<AdminToken> candidates = repository.findAll().stream()
                .filter(existing -> name.equals(existing.getAdminName()))
                .toList();
        AdminToken record = candidates.stream().findFirst().orElseGet(() -> AdminToken.builder()
                .adminName(name)
                .role(role)
                .token(token)
                .isActive(true)
                .build());
        record.setRole(role);
        record.setToken(token.trim());
        record.setIsActive(true);
        repository.save(record);
    }
}
