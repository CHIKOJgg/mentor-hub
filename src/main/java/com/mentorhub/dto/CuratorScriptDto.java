package com.mentorhub.dto;

public record CuratorScriptDto(
        String id,
        String title,
        String description,
        String text,
        String category,
        Integer sortOrder
) {}
