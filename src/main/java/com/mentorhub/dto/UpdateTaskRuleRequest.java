package com.mentorhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateTaskRuleRequest(
    String title,
    String description,
    @JsonProperty("required")
    Boolean required,
    String example,
    Boolean isActive,
    String hashtag
) {
    public UpdateTaskRuleRequest(String title, String description, Boolean required, String example, Boolean isActive) {
        this(title, description, required, example, isActive, null);
    }
}
