package com.mentorhub.controller;

import com.mentorhub.dto.CreateStudentRequest;
import com.mentorhub.dto.TogglePauseRequest;
import com.mentorhub.dto.UpdateStudentRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentControllerTest {

    private static final String ADMIN_TOKEN = "magic-ksenia-mentor-token-998877";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/students returns full student list with weekDays and Sunday rule")
    void testGetStudents() throws Exception {
        mockMvc.perform(get("/api/students")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].fullName", notNullValue()))
                .andExpect(jsonPath("$[0].disciplineRate", notNullValue()))
                .andExpect(jsonPath("$[0].weekDays", hasSize(7)))
                .andExpect(jsonPath("$[0].weekDays[6].day", is("Вс")))
                .andExpect(jsonPath("$[0].weekDays[6].status", is("WEEKEND")))
                .andExpect(jsonPath("$[0].weekDays[6].missedDetails", is("Воскресенье — общий выходной потока ☕")));
    }

    @Test
    @DisplayName("GET /api/students rejects credentials in query parameters")
    void testGetStudentsRejectsQueryToken() throws Exception {
        mockMvc.perform(get("/api/students")
                        .param("token", ADMIN_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/students/{id} returns single student profile")
    void testGetStudentById() throws Exception {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();

        mockMvc.perform(get("/api/students/" + milana.getId())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Милана Горохова")))
                .andExpect(jsonPath("$.niche", is("Стилист-имиджмейкер")))
                .andExpect(jsonPath("$.tariff", is("VIP")));
    }

    @Test
    @DisplayName("POST /api/students creates a new student")
    void testCreateStudent() throws Exception {
        CreateStudentRequest request = new CreateStudentRequest(
                "Ольга Новикова",
                "ОН",
                "olga_novikova",
                55667788L,
                "Юрист для онлайн-школ",
                "VIP",
                new BigDecimal("50000.00"),
                new BigDecimal("15000.00"),
                LocalDate.now()
        );

        mockMvc.perform(post("/api/students")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName", is("Ольга Новикова")))
                .andExpect(jsonPath("$.niche", is("Юрист для онлайн-школ")));
    }

    @Test
    @DisplayName("PUT /api/students/{id} updates student profile")
    void testUpdateStudent() throws Exception {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();

        UpdateStudentRequest request = new UpdateStudentRequest(
                "Милана Горохова-Премиум",
                "МГ",
                "milana_gorohova",
                milana.getTgChatId(),
                "VIP Стилист-имиджмейкер",
                "VIP",
                milana.getInitialIncome(),
                new BigDecimal("250000.00"),
                milana.getInitialAvgTicket(),
                new BigDecimal("55000.00"),
                milana.getStartDate(),
                true,
                false,
                null
        );

        mockMvc.perform(put("/api/students/" + milana.getId())
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Милана Горохова-Премиум")))
                .andExpect(jsonPath("$.currentIncome", is(250000.00)));
    }

    @Test
    @DisplayName("DELETE /api/students/{id} soft deletes student")
    void testDeleteStudent() throws Exception {
        Student anya = studentRepository.findByTgUsername("anya_petrova_style").orElseThrow();

        mockMvc.perform(delete("/api/students/" + anya.getId())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());

        Student deleted = studentRepository.findById(anya.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(deleted.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("POST /api/students/{id}/toggle-pause freezes streak and marks paused days")
    void testTogglePause() throws Exception {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();
        TogglePauseRequest request = new TogglePauseRequest(true, "ОРВИ до понедельника");

        mockMvc.perform(post("/api/students/" + milana.getId() + "/toggle-pause")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOnPause", is(true)))
                .andExpect(jsonPath("$.pauseReason", is("ОРВИ до понедельника")))
                .andExpect(jsonPath("$.streakFrozen", is(true)));
    }

    @Test
    @DisplayName("GET /api/students/{id}/discipline-calendar returns 7 days of discipline breakdown")
    void testGetDisciplineCalendar() throws Exception {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();

        mockMvc.perform(get("/api/students/" + milana.getId() + "/discipline-calendar")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[6].day", is("Вс")))
                .andExpect(jsonPath("$[6].status", is("WEEKEND")));
    }
}
