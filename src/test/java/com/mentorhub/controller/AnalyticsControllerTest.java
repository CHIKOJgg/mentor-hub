package com.mentorhub.controller;

import com.mentorhub.entity.Student;
import com.mentorhub.repository.StudentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnalyticsControllerTest {

    private static final String ADMIN_TOKEN = "magic-ksenia-mentor-token-998877";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Test
    @DisplayName("GET /api/analytics/summary returns all 10 cross-cutting metrics")
    void testGetAnalyticsSummary() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalEarned", is(895000.0)))
                .andExpect(jsonPath("$.revenueGrowthFactor", is("+397%")))
                .andExpect(jsonPath("$.totalReels", is(66)))
                .andExpect(jsonPath("$.totalCarousels", is(38)))
                .andExpect(jsonPath("$.totalStorytells", is(47)))
                .andExpect(jsonPath("$.totalMailings", is(702)))
                .andExpect(jsonPath("$.totalNewClients", is(33)))
                .andExpect(jsonPath("$.currentAvgTicket", is(40600.0)))
                .andExpect(jsonPath("$.initialAvgTicket", is(4800.0)))
                .andExpect(jsonPath("$.avgTicketGrowthFactor", is("x8.4")))
                .andExpect(jsonPath("$.averageDisciplineRate", is(88.0)))
                .andExpect(jsonPath("$.activeBurnoutAlertsCount", is(1)));
    }

    @Test
    @DisplayName("GET /api/analytics/case/{studentId} returns case with multiplier and generated story copy")
    void testGetStoryCase() throws Exception {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();

        mockMvc.perform(get("/api/analytics/case/" + milana.getId())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.fullName", is("Милана Горохова")))
                .andExpect(jsonPath("$.niche", is("Стилист-имиджмейкер")))
                .andExpect(jsonPath("$.growthMultiplier", is("x6.0")))
                .andExpect(jsonPath("$.growthPercent", is("+500%")))
                .andExpect(jsonPath("$.disciplineRate", is(95)))
                .andExpect(jsonPath("$.generatedStoryCopy", containsString("🔥 КЕЙС ПОТОКА: Милана Горохова")))
                .andExpect(jsonPath("$.generatedStoryCopy", containsString("Точка А: 30 000 ₽ ➡️ Точка Б: 180 000 ₽ (РОСТ x6.0!)")))
                .andExpect(jsonPath("$.generatedStoryCopy", containsString("Средний чек: вырос с 3 000 ₽ до 45 000 ₽")));
    }
}
