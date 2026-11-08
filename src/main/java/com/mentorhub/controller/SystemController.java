package com.mentorhub.controller;

import com.mentorhub.dto.ScheduleZoomRequest;
import com.mentorhub.dto.SyncTelegramResponse;
import com.mentorhub.dto.SystemStatusResponse;
import com.mentorhub.dto.ZoomMeetingDto;
import com.mentorhub.dto.ZoomTrackerResponse;
import com.mentorhub.service.SystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
