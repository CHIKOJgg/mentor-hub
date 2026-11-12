package com.mentorhub;

import com.mentorhub.entity.AdminToken;
import com.mentorhub.entity.Student;
import com.mentorhub.entity.TaskRule;
import com.mentorhub.repository.AdminTokenRepository;
import com.mentorhub.security.AdminTokenHasher;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.repository.TaskRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MentorHubApplicationTests {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AdminTokenRepository adminTokenRepository;

    @Autowired
    private TaskRuleRepository taskRuleRepository;

    @Autowired
    private MentorSettingRepository mentorSettingRepository;

    @Test
    @DisplayName("Context loads successfully and Flyway applies initial schema")
    void contextLoads() {
        assertThat(studentRepository).isNotNull();
        assertThat(adminTokenRepository).isNotNull();
        assertThat(taskRuleRepository).isNotNull();
        assertThat(mentorSettingRepository).isNotNull();
    }

    @Test
    @DisplayName("Flyway seed data verifies students seeded properly")
    void verifySeedStudents() {
        List<Student> students = studentRepository.findAll();
        assertThat(students).hasSize(5);

        Optional<Student> milana = studentRepository.findByTgUsername("milana_gorohova");
        assertThat(milana).isPresent();
        assertThat(milana.get().getFullName()).isEqualTo("Милана Горохова");
        assertThat(milana.get().getTariff()).isEqualTo("VIP");
        assertThat(milana.get().getInitialIncome()).isEqualByComparingTo("30000.00");
        assertThat(milana.get().getCurrentIncome()).isEqualByComparingTo("180000.00");

        Optional<Student> victoria = studentRepository.findByTgUsername("v_lebedeva_fit");
        assertThat(victoria).isPresent();
        assertThat(victoria.get().getIsPaused()).isTrue();
        assertThat(victoria.get().getPauseReason()).contains("Заболела");
    }

    @Test
    @DisplayName("Test admin credentials are stored as hashes")
    void verifySeedAdminTokens() {
        Optional<AdminToken> kseniaToken = adminTokenRepository.findByToken(AdminTokenHasher.hash("magic-ksenia-mentor-token-998877"));
        assertThat(kseniaToken).isPresent();
        assertThat(kseniaToken.get().getAdminName()).isEqualTo("Ксения");
        assertThat(kseniaToken.get().getRole()).isEqualTo("MENTOR");
        assertThat(kseniaToken.get().getIsActive()).isTrue();

        Optional<AdminToken> katyaToken = adminTokenRepository.findByToken(AdminTokenHasher.hash("magic-katya-curator-token-112233"));
        assertThat(katyaToken).isPresent();
        assertThat(katyaToken.get().getAdminName()).isEqualTo("Катя");
        assertThat(katyaToken.get().getRole()).isEqualTo("CURATOR");
        assertThat(katyaToken.get().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Flyway seed data verifies task rules")
    void verifySeedTaskRules() {
        List<TaskRule> rules = taskRuleRepository.findAll();
        assertThat(rules).hasSize(7);

        Optional<TaskRule> reelsRule = taskRuleRepository.findByHashtag("#рилс");
        assertThat(reelsRule).isPresent();
        assertThat(reelsRule.get().getIsRequired()).isTrue();

        Optional<TaskRule> carouselRule = taskRuleRepository.findByHashtag("#карусель");
        assertThat(carouselRule).isPresent();
        assertThat(carouselRule.get().getIsRequired()).isFalse();
    }

    @Test
    @DisplayName("Flyway seed data verifies mentor settings")
    void verifyMentorSettings() {
        assertThat(mentorSettingRepository.findAll()).hasSize(1);
        var settings = mentorSettingRepository.findAll().get(0);
        assertThat(settings.getMentorName()).isEqualTo("Ксения");
        assertThat(settings.getIsBusinessConnected()).isTrue();
    }
}
