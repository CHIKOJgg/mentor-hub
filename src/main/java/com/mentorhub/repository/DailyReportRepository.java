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

    List<DailyReport> findByStudentIdInAndReportDateBetween(List<Long> studentIds, LocalDate startDate, LocalDate endDate);

    List<DailyReport> findByReportDate(LocalDate reportDate);

    long countByStatus(String status);

    @Query("SELECT COUNT(r) FROM DailyReport r WHERE r.reflectionEmotions IS NOT NULL OR r.reflectionActions IS NOT NULL OR r.reflectionQuestion IS NOT NULL")
    long countByAnyReflectionNotNull();

    @Query("SELECT MAX(r.reportDate) FROM DailyReport r")
    LocalDate findMaxReportDate();

    boolean existsByReportDateBetween(LocalDate startDate, LocalDate endDate);
}
