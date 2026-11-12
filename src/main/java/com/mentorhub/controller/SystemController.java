package com.mentorhub.controller;

import com.mentorhub.dto.AdminSessionDto;
import com.mentorhub.dto.ScheduleZoomRequest;
import com.mentorhub.dto.SyncTelegramResponse;
import com.mentorhub.dto.SystemSettingsDto;
import com.mentorhub.dto.SystemStatusResponse;
import com.mentorhub.dto.UpdateZoomMeetingRequest;
import com.mentorhub.dto.ZoomMeetingDto;
import com.mentorhub.dto.ZoomTrackerResponse;
import com.mentorhub.service.SystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> getSystemStatus() {
        SystemStatusResponse status = systemService.getSystemStatus();
        return ResponseEntity.ok(status);
    }

    @PostMapping("/sync-telegram")
    public ResponseEntity<SyncTelegramResponse> syncTelegram() {
        SyncTelegramResponse response = systemService.syncTelegram();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/session")
    public ResponseEntity<AdminSessionDto> getCurrentSession(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse("ADMIN");
        return ResponseEntity.ok(new AdminSessionDto(authentication.getName(), role, true));
    }

    @GetMapping("/settings")
    public ResponseEntity<SystemSettingsDto> getSettings() {
        return ResponseEntity.ok(systemService.getSettings());
    }

    @PreAuthorize("hasAnyRole('MENTOR', 'CURATOR')")
    @PutMapping("/settings")
    public ResponseEntity<SystemSettingsDto> updateSettings(@Valid @RequestBody SystemSettingsDto request) {
        return ResponseEntity.ok(systemService.updateSettings(request));
    }

    @GetMapping("/zoom-tracker")
    public ResponseEntity<ZoomTrackerResponse> getZoomTracker() {
        ZoomTrackerResponse tracker = systemService.getZoomTracker();
        return ResponseEntity.ok(tracker);
    }

    @PostMapping("/zoom-tracker")
    public ResponseEntity<ZoomMeetingDto> scheduleZoom(@Valid @RequestBody ScheduleZoomRequest request) {
        ZoomMeetingDto meeting = systemService.scheduleZoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(meeting);
    }

    @PutMapping("/zoom-tracker/{meetingId}")
    public ResponseEntity<ZoomMeetingDto> updateZoomMeeting(
            @PathVariable Long meetingId,
            @RequestBody UpdateZoomMeetingRequest request
    ) {
        return ResponseEntity.ok(systemService.updateZoomMeeting(meetingId, request));
    }

    @PreAuthorize("hasAnyRole('MENTOR', 'CURATOR')")
    @DeleteMapping("/zoom-tracker/{meetingId}")
    public ResponseEntity<Void> deleteZoomMeeting(@PathVariable Long meetingId) {
        systemService.deleteZoomMeeting(meetingId);
        return ResponseEntity.noContent().build();
    }
}
