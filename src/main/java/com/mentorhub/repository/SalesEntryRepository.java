package com.mentorhub.repository;

import com.mentorhub.entity.SalesEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesEntryRepository extends JpaRepository<SalesEntry, Long> {

    List<SalesEntry> findByStudentId(Long studentId);

    List<SalesEntry> findByReportId(Long reportId);
}
