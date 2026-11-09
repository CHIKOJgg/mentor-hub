package com.mentorhub.repository;

import com.mentorhub.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByTgChatId(Long tgChatId);

    Optional<Student> findByTgUsername(String tgUsername);

    @Query("select distinct s from Student s left join fetch s.zoomMeetings where s.isActive = true")
    List<Student> findByIsActiveTrue();

    @Query("select distinct s from Student s left join fetch s.zoomMeetings " +
            "where lower(s.fullName) like lower(concat('%', :fullName, '%')) " +
            "or lower(s.niche) like lower(concat('%', :niche, '%'))")
    List<Student> findByFullNameContainingIgnoreCaseOrNicheContainingIgnoreCase(String fullName, String niche);

    @Query("select distinct s from Student s left join fetch s.zoomMeetings where s.id = :id")
    Optional<Student> findWithZoomMeetingsById(Long id);

    @Query("select distinct s from Student s left join fetch s.zoomMeetings")
    List<Student> findAllWithZoomMeetings();
}
