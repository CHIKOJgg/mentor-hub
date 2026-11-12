package com.mentorhub.repository;

import com.mentorhub.entity.MentorSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MentorSettingRepository extends JpaRepository<MentorSetting, Long> {

    Optional<MentorSetting> findByMentorTgId(Long mentorTgId);

    Optional<MentorSetting> findByMentorName(String mentorName);

    Optional<MentorSetting> findFirstByOrderByIdAsc();
}
