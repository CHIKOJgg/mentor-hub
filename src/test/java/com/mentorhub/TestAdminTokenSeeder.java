package com.mentorhub;

import com.mentorhub.security.AdminTokenHasher;
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
        setTestToken("Ксения", "magic-ksenia-mentor-token-998877");
        setTestToken("Катя", "magic-katya-curator-token-112233");
    }

    private void setTestToken(String adminName, String rawToken) {
        repository.findAll().stream()
                .filter(token -> adminName.equals(token.getAdminName()))
                .findFirst()
                .ifPresent(token -> {
                    token.setToken(AdminTokenHasher.hash(rawToken));
                    token.setIsActive(true);
                    repository.save(token);
                });
    }
}
