package com.mentorhub.dto;

public record ContentDraftDto(
    String id,
    String type,
    String label,
    Boolean isDuplicate,
    String duplicateDate,
    String url
) {}
