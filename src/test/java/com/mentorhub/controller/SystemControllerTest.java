package com.mentorhub.controller;

import com.mentorhub.dto.ScheduleZoomRequest;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SystemControllerTest {

    private static final String ADMIN_TOKEN = "magic-ksenia-mentor-token-998877";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/system/status is permitAll and returns system diagnostics")
    void testGetSystemStatusPublic() throws Exception {
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.springBootVersion", is("3.4.2")))
                .andExpect(jsonPath("$.virtualThreadsEnabled", is(true)))
                .andExpect(jsonPath("$.springAiStatus", is("NOT_CONFIGURED")))
                .andExpect(jsonPath("$.zoomTracker.completedCallsTotal", is(19)))
                .andExpect(jsonPath("$.zoomTracker.nextCallAt", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/system/sync-telegram triggers manual sync")
    void testSyncTelegram() throws Exception {
        mockMvc.perform(post("/api/system/sync-telegram")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("недоступен")));
    }

    @Test
    @DisplayName("GET /api/system/zoom-tracker returns meetings overview")
    void testGetZoomTracker() throws Exception {
        mockMvc.perform(get("/api/system/zoom-tracker")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCallsTotal", is(19)))
                .andExpect(jsonPath("$.upcomingMeetings", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/system/zoom-tracker schedules a new Zoom call")
    void testScheduleZoom() throws Exception {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();
        ScheduleZoomRequest request = new ScheduleZoomRequest(
                milana.getId(),
                "https://us02web.zoom.us/j/111222333",
                Instant.parse("2026-11-18T14:00:00Z"),
                "Разбор скрипта продаж"
        );

        mockMvc.perform(post("/api/system/zoom-tracker")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId", is(milana.getId().intValue())))
                .andExpect(jsonPath("$.zoomUrl", is("https://us02web.zoom.us/j/111222333")));
    }
}
