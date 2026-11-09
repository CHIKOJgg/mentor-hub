package com.mentorhub.service;

import com.mentorhub.dto.TaskRuleResponse;
import com.mentorhub.dto.CreateTaskRuleRequest;
import com.mentorhub.dto.UpdateTaskRuleRequest;
import com.mentorhub.entity.TaskRule;
import com.mentorhub.repository.TaskRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRuleService {

    private final TaskRuleRepository taskRuleRepository;

    @Transactional(readOnly = true)
    public List<TaskRuleResponse> getAllRules(boolean includeInactive) {
        return taskRuleRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .filter(r -> includeInactive || Boolean.TRUE.equals(r.getIsActive()))
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskRuleResponse> getAllRules() {
        return getAllRules(false);
    }

    @Transactional
    public TaskRuleResponse createRule(CreateTaskRuleRequest request) {
        String hashtag = normalizeHashtag(request.hashtag());
        if (taskRuleRepository.findByHashtagIgnoreCase(hashtag).isPresent()) {
            throw new IllegalArgumentException("Хэштег уже существует: " + hashtag);
        }
        TaskRule rule = TaskRule.builder()
                .hashtag(hashtag)
                .title(request.title().trim())
                .description(request.description().trim())
                .isRequired(request.required())
                .example(request.example().trim())
                .isActive(true)
                .build();
        return mapToResponse(taskRuleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public TaskRuleResponse getRuleById(Long id) {
        TaskRule rule = taskRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task rule not found with id: " + id));
        return mapToResponse(rule);
    }

    @Transactional
    public TaskRuleResponse updateRule(Long id, UpdateTaskRuleRequest request) {
        TaskRule rule = taskRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task rule not found with id: " + id));

        if (request.hashtag() != null) {
            String hashtag = normalizeHashtag(request.hashtag());
            taskRuleRepository.findByHashtagIgnoreCase(hashtag)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> { throw new IllegalArgumentException("Хэштег уже существует: " + hashtag); });
            rule.setHashtag(hashtag);
        }
        if (request.title() != null) rule.setTitle(request.title());
        if (request.description() != null) rule.setDescription(request.description());
        if (request.required() != null) rule.setIsRequired(request.required());
        if (request.example() != null) rule.setExample(request.example());
        if (request.isActive() != null) rule.setIsActive(request.isActive());

        TaskRule saved = taskRuleRepository.save(rule);
        log.info("Updated task rule #{} ({})", saved.getId(), saved.getHashtag());
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteRule(Long id) {
        TaskRule rule = taskRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task rule not found with id: " + id));
        rule.setIsActive(false);
        taskRuleRepository.save(rule);
    }

    private String normalizeHashtag(String hashtag) {
        String normalized = hashtag.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("#") ? normalized : "#" + normalized;
    }

    private TaskRuleResponse mapToResponse(TaskRule rule) {
        return new TaskRuleResponse(
                "rule-" + rule.getId(),
                rule.getHashtag(),
                rule.getTitle(),
                rule.getDescription(),
                Boolean.TRUE.equals(rule.getIsRequired()),
                rule.getExample(),
                Boolean.TRUE.equals(rule.getIsActive())
        );
    }
}
