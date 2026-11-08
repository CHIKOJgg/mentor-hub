package com.mentorhub.controller;

import com.mentorhub.dto.ApproveReportRequest;
import com.mentorhub.dto.DailyReportResponse;
import com.mentorhub.dto.RejectReportRequest;
import com.mentorhub.dto.ReportActionResponse;
import com.mentorhub.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<List<DailyReportResponse>> getInboxQueue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "PENDING") String status
    ) {
        List<DailyReportResponse> reports = reportService.getInboxQueue(date, status);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<DailyReportResponse> getReportById(@PathVariable String reportId) {
        Long id = parseId(reportId);
        DailyReportResponse report = reportService.getReportById(id);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/{reportId}/approve")
    public ResponseEntity<ReportActionResponse> approveReport(
            @PathVariable String reportId,
            @RequestBody(required = false) ApproveReportRequest request
    ) {
        Long id = parseId(reportId);
        ReportActionResponse response = reportService.approveReport(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reportId}/reject")
    public ResponseEntity<ReportActionResponse> rejectReport(
            @PathVariable String reportId,
            @Valid @RequestBody RejectReportRequest request
    ) {
        Long id = parseId(reportId);
        ReportActionResponse response = reportService.rejectReport(id, request);
        return ResponseEntity.ok(response);
    }

    private Long parseId(String idStr) {
        if (idStr == null) return null;
        String clean = idStr.trim();
        if (clean.startsWith("rep-") || clean.startsWith("st-")) {
            clean = clean.substring(clean.indexOf('-') + 1);
        }
        return Long.parseLong(clean);
    }
}
