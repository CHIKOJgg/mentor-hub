package com.mentorhub.service;

import com.mentorhub.dto.TaskRuleResponse;
import com.mentorhub.dto.UpdateTaskRuleRequest;
import com.mentorhub.entity.TaskRule;
import com.mentorhub.repository.TaskRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskRuleServiceTest {

    @Autowired
    private TaskRuleService taskRuleService;

    @Autowired
    private TaskRuleRepository taskRuleRepository;

    @Test
    @DisplayName("getAllRules returns all active cohort task rules with hashtag specs")
    void testGetAllRules() {
        List<TaskRuleResponse> rules = taskRuleService.getAllRules();
        assertThat(rules).hasSize(7);

        TaskRuleResponse reels = rules.stream().filter(r -> "#рилс".equals(r.hashtag())).findFirst().orElseThrow();
        assertThat(reels.required()).isTrue();
        assertThat(reels.title()).isEqualTo("Черновик видео Reels");
        assertThat(reels.example()).startsWith("#рилс");

        TaskRuleResponse sales = rules.stream().filter(r -> "#продажи".equals(r.hashtag())).findFirst().orElseThrow();
        assertThat(sales.required()).isFalse();
    }

    @Test
    @DisplayName("updateRule modifies rule fields and persists changes")
    void testUpdateRule() {
        TaskRule rule = taskRuleRepository.findByHashtag("#карусель").orElseThrow();

        UpdateTaskRuleRequest request = new UpdateTaskRuleRequest(
                "Карусель 2.0 (новые правила)",
                "Обновленное описание правил карусели",
                true,
                "#карусель 10 слайдов оферты",
                true
        );

        TaskRuleResponse updated = taskRuleService.updateRule(rule.getId(), request);
        assertThat(updated.title()).isEqualTo("Карусель 2.0 (новые правила)");
        assertThat(updated.required()).isTrue();

        TaskRule inDb = taskRuleRepository.findById(rule.getId()).orElseThrow();
        assertThat(inDb.getTitle()).isEqualTo("Карусель 2.0 (новые правила)");
        assertThat(inDb.getIsRequired()).isTrue();
    }

    @Test
    @DisplayName("getRuleById throws ResourceNotFoundException for unknown ID")
    void testGetRuleByIdNotFound() {
        assertThatThrownBy(() -> taskRuleService.getRuleById(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
