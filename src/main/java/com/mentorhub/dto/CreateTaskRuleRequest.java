package com.mentorhub.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskRuleRequest(
        @NotBlank String hashtag,
        @NotBlank String title,
        @NotBlank String description,
        boolean required,
        @NotBlank String example
) {}
