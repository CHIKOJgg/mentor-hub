package com.mentorhub.service;

import com.mentorhub.dto.CuratorScriptDto;
import com.mentorhub.dto.CuratorScriptRequest;
import com.mentorhub.entity.CuratorScript;
import com.mentorhub.repository.CuratorScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CuratorScriptService {
    private final CuratorScriptRepository repository;

    @Transactional(readOnly = true)
    public List<CuratorScriptDto> list() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(script -> Boolean.TRUE.equals(script.getIsActive()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CuratorScriptDto create(CuratorScriptRequest request) {
        CuratorScript script = CuratorScript.builder()
                .title(request.title().trim())
                .description(request.description() == null ? "" : request.description().trim())
                .text(request.text())
                .category(request.category() == null || request.category().isBlank() ? "Кураторские" : request.category().trim())
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .isActive(true)
                .build();
        return toDto(repository.save(script));
    }

    @Transactional
    public CuratorScriptDto update(Long id, CuratorScriptRequest request) {
        CuratorScript script = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curator script not found with id: " + id));
        if (request.title() != null) script.setTitle(request.title().trim());
        if (request.description() != null) script.setDescription(request.description());
        if (request.text() != null) script.setText(request.text());
        if (request.category() != null) script.setCategory(request.category().trim());
        if (request.sortOrder() != null) script.setSortOrder(request.sortOrder());
        return toDto(repository.save(script));
    }

    @Transactional
    public void delete(Long id) {
        CuratorScript script = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curator script not found with id: " + id));
        script.setIsActive(false);
        repository.save(script);
    }

    private CuratorScriptDto toDto(CuratorScript script) {
        return new CuratorScriptDto("script-" + script.getId(), script.getTitle(), script.getDescription(),
                script.getText(), script.getCategory(), script.getSortOrder());
    }
}
