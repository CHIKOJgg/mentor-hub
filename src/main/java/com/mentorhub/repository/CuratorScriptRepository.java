package com.mentorhub.repository;

import com.mentorhub.entity.CuratorScript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuratorScriptRepository extends JpaRepository<CuratorScript, Long> {
    List<CuratorScript> findAllByOrderBySortOrderAscIdAsc();
}
