package com.mentorhub.dto;

public record ReelDto(
    String id,
    String url,
    String shortcode,
    Boolean isValid,
    Boolean isDuplicate,
    String duplicateDate
) {}
