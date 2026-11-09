package com.mentorhub.repository;

import com.mentorhub.entity.StudentDayOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface StudentDayOverrideRepository extends JpaRepository<StudentDayOverride, Long> {
    Optional<StudentDayOverride> findByStudentIdAndDayDate(Long studentId, LocalDate dayDate);
}
