package com.endpointposture.ise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record IseOperationResolutionRequest(
        @NotNull @Pattern(regexp = "SUCCEEDED|FAILED") String resolution,
        @NotBlank String supportingDetails) {}
