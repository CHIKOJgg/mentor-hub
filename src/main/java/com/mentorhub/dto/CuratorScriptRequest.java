package com.mentorhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CuratorScriptRequest(
        @NotBlank String title,
        String description,
        @NotNull String text,
        String category,
        Integer sortOrder
) {}
