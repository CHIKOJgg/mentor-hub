package com.mentorhub.dto;

import jakarta.validation.constraints.NotBlank;

public record TelegramMessageRequest(@NotBlank String text) {}
