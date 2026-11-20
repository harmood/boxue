package com.boxue.api.auth;

import java.util.List;
import java.util.UUID;

public record AuthPrincipal(
    UUID userId,
    UUID sessionId,
    UUID deviceId,
    String audience,
    List<String> roles,
    List<String> permissions
) {
}
