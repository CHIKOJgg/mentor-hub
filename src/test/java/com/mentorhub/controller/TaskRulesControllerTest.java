package com.mentorhub.controller;

import com.mentorhub.dto.UpdateTaskRuleRequest;
import com.mentorhub.entity.TaskRule;
import com.mentorhub.repository.TaskRuleRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskRulesControllerTest {

    private static final String ADMIN_TOKEN = "magic-ksenia-mentor-token-998877";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRuleRepository taskRuleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/tasks/rules returns task rules with required/optional flags and examples")
    void testGetAllRules() throws Exception {
        mockMvc.perform(get("/api/tasks/rules")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0].hashtag", is("#рилс")))
                .andExpect(jsonPath("$[0].required", is(true)))
                .andExpect(jsonPath("$[0].example", containsString("#рилс")))
                .andExpect(jsonPath("$[1].hashtag", is("#карусель")))
                .andExpect(jsonPath("$[1].required", is(false)));
    }

    @Test
    @DisplayName("PUT /api/tasks/rules/{id} updates rule")
    void testUpdateRule() throws Exception {
        TaskRule carousel = taskRuleRepository.findByHashtag("#карусель").orElseThrow();

        UpdateTaskRuleRequest request = new UpdateTaskRuleRequest(
                "Карусель (обновлено)",
                "Новое описание черновиков каруселей",
                true,
                "#карусель 7 слайдов",
                true
        );

        mockMvc.perform(put("/api/tasks/rules/" + carousel.getId())
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Карусель (обновлено)")))
                .andExpect(jsonPath("$.required", is(true)))
                .andExpect(jsonPath("$.example", is("#карусель 7 слайдов")));
    }
}
