package com.mentorhub.controller;

import com.mentorhub.dto.CuratorScriptDto;
import com.mentorhub.dto.CuratorScriptRequest;
import com.mentorhub.service.CuratorScriptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class CuratorScriptController {
    private final CuratorScriptService service;

    @GetMapping
    public ResponseEntity<List<CuratorScriptDto>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<CuratorScriptDto> create(@Valid @RequestBody CuratorScriptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CuratorScriptDto> update(@PathVariable String id, @Valid @RequestBody CuratorScriptRequest request) {
        return ResponseEntity.ok(service.update(parseId(id), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    private Long parseId(String value) {
        String clean = value.trim();
        if (clean.startsWith("script-")) clean = clean.substring(7);
        return Long.parseLong(clean);
    }
}
