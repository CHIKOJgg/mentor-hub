package com.mentorhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskRuleResponse(
    String id,
    String hashtag,
    String title,
    String description,
    @JsonProperty("required")
    boolean required,
    String example
) {}
