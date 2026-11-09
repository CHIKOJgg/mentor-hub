package com.mentorhub.controller;

import com.mentorhub.dto.ApproveReportRequest;
import com.mentorhub.dto.RejectReportRequest;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.repository.DailyReportRepository;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InboxControllerTest {

    private static final String ADMIN_TOKEN = "magic-ksenia-mentor-token-998877";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/inbox without token returns 401 Unauthorized")
    void testGetInboxUnauthorized() throws Exception {
        mockMvc.perform(get("/api/inbox"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/inbox with admin token returns pending reports queue matching camelCase schema")
    void testGetInboxSuccess() throws Exception {
        mockMvc.perform(get("/api/inbox")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].studentName", notNullValue()))
                .andExpect(jsonPath("$[0].avatarText", notNullValue()))
                .andExpect(jsonPath("$[0].status", is("PENDING")))
                .andExpect(jsonPath("$[0].contentDrafts", notNullValue()))
                .andExpect(jsonPath("$[0].salesAmount", notNullValue()))
                .andExpect(jsonPath("$[0].aiSummary", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/inbox/{reportId} returns specific report detail")
    void testGetReportById() throws Exception {
        List<DailyReport> reports = dailyReportRepository.findAll();
        DailyReport target = reports.get(0);

        mockMvc.perform(get("/api/inbox/" + target.getId())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(String.valueOf(target.getId()))))
                .andExpect(jsonPath("$.studentName", is(target.getStudent().getFullName())));
    }

    @Test
    @DisplayName("POST /api/inbox/{reportId}/approve approves report and returns ReportActionResponse")
    void testApproveReport() throws Exception {
        DailyReport target = dailyReportRepository.findByStatus("PENDING").get(0);
        ApproveReportRequest request = new ApproveReportRequest("Отличная работа по рассылкам!");

        mockMvc.perform(post("/api/inbox/" + target.getId() + "/approve")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.reportId", is(String.valueOf(target.getId()))));
    }

    @Test
    @DisplayName("POST /api/inbox/{reportId}/reject rejects report with feedback reason")
    void testRejectReport() throws Exception {
        DailyReport target = dailyReportRepository.findByStatus("PENDING").get(0);
        RejectReportRequest request = new RejectReportRequest("Пересдай черновик Reels без водяных знаков");

        mockMvc.perform(post("/api/inbox/" + target.getId() + "/reject")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.feedbackSent", is(false)));
    }
}
