package com.mentorhub.repository;

import com.mentorhub.entity.TaskRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRuleRepository extends JpaRepository<TaskRule, Long> {

    Optional<TaskRule> findByHashtag(String hashtag);

    List<TaskRule> findByIsActiveTrue();
}
