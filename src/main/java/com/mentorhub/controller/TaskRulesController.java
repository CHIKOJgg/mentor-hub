package com.mentorhub.controller;

import com.mentorhub.dto.TaskRuleResponse;
import com.mentorhub.dto.CreateTaskRuleRequest;
import com.mentorhub.dto.UpdateTaskRuleRequest;
import com.mentorhub.service.TaskRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskRulesController {

    private final TaskRuleService taskRuleService;

    @GetMapping("/rules")
    public ResponseEntity<List<TaskRuleResponse>> getAllRules(@RequestParam(defaultValue = "false") boolean includeInactive) {
        List<TaskRuleResponse> rules = taskRuleService.getAllRules(includeInactive);
        return ResponseEntity.ok(rules);
    }

    @PreAuthorize("hasAnyRole('MENTOR', 'CURATOR')")
    @PostMapping("/rules")
    public ResponseEntity<TaskRuleResponse> createRule(@RequestBody @jakarta.validation.Valid CreateTaskRuleRequest request) {
        return ResponseEntity.status(201).body(taskRuleService.createRule(request));
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<TaskRuleResponse> getRuleById(@PathVariable String id) {
        Long ruleId = parseId(id);
        TaskRuleResponse rule = taskRuleService.getRuleById(ruleId);
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<TaskRuleResponse> updateRule(
            @PathVariable String id,
            @RequestBody UpdateTaskRuleRequest request
    ) {
        Long ruleId = parseId(id);
        TaskRuleResponse updated = taskRuleService.updateRule(ruleId, request);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasAnyRole('MENTOR', 'CURATOR')")
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        taskRuleService.deleteRule(parseId(id));
        return ResponseEntity.noContent().build();
    }

    private Long parseId(String idStr) {
        if (idStr == null) return null;
        String clean = idStr.trim();
        if (clean.startsWith("rule-")) {
            clean = clean.substring(5);
        }
        return Long.parseLong(clean);
    }
}
