package com.mentorhub;

import com.mentorhub.repository.AdminTokenRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("test")
@RequiredArgsConstructor
public class TestAdminTokenSeeder {
    private final AdminTokenRepository repository;

    @PostConstruct
    @Transactional
    public void enableTestOnlyTokens() {
        repository.findByToken("magic-ksenia-mentor-token-998877").ifPresent(token -> {
            token.setIsActive(true);
            repository.save(token);
        });
        repository.findByToken("magic-katya-curator-token-112233").ifPresent(token -> {
            token.setIsActive(true);
            repository.save(token);
        });
    }
}
