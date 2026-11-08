package com.mentorhub.controller;

import com.mentorhub.dto.AnalyticsSummaryResponse;
import com.mentorhub.dto.StoryCaseResponse;
import com.mentorhub.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getAnalyticsSummary() {
        AnalyticsSummaryResponse summary = analyticsService.getAnalyticsSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/case/{studentId}")
    public ResponseEntity<StoryCaseResponse> getStoryCase(@PathVariable String studentId) {
        Long id = parseId(studentId);
        StoryCaseResponse storyCase = analyticsService.getStoryCase(id);
        return ResponseEntity.ok(storyCase);
    }

    private Long parseId(String idStr) {
        if (idStr == null) return null;
        String clean = idStr.trim();
        if (clean.startsWith("st-")) {
            clean = clean.substring(3);
        }
        return Long.parseLong(clean);
    }
}
