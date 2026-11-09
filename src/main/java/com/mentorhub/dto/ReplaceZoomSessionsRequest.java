package com.mentorhub.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceZoomSessionsRequest(
        @NotNull List<@Valid ZoomSessionInput> sessions
) {}
