package com.mentorhub.repository;

import com.mentorhub.entity.DailyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    Optional<DailyReport> findByStudentIdAndReportDate(Long studentId, LocalDate reportDate);

    List<DailyReport> findByReportDateAndStatus(LocalDate reportDate, String status);

    List<DailyReport> findByStatus(String status);

    List<DailyReport> findByStudentIdOrderByReportDateDesc(Long studentId);

    List<DailyReport> findByReportDateBetweenOrderByReportDateAsc(LocalDate startDate, LocalDate endDate);

    long countByStatus(String status);

    @Query("SELECT MAX(r.reportDate) FROM DailyReport r")
    LocalDate findMaxReportDate();

    boolean existsByReportDateBetween(LocalDate startDate, LocalDate endDate);
}
