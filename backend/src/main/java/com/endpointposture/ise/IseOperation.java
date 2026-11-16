package com.endpointposture.ise;

import java.util.UUID;

public record IseOperation(UUID id, UUID endpointId, String macAddress, String actionType,
                           String operator, String policy, String postureStatus, String postureDetails) {}
