package com.mentorhub.repository;

import com.mentorhub.entity.AdminToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminTokenRepository extends JpaRepository<AdminToken, Long> {

    Optional<AdminToken> findByTokenAndIsActiveTrue(String token);

    Optional<AdminToken> findByToken(String token);

    Optional<AdminToken> findByTgUserId(Long tgUserId);
}
