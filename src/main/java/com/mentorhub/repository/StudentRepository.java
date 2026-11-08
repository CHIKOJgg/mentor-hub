package com.mentorhub.repository;

import com.mentorhub.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByTgChatId(Long tgChatId);

    Optional<Student> findByTgUsername(String tgUsername);

    List<Student> findByIsActiveTrue();

    List<Student> findByFullNameContainingIgnoreCaseOrNicheContainingIgnoreCase(String fullName, String niche);
}
