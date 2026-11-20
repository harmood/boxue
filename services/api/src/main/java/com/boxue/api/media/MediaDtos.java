package com.boxue.api.media;

import java.time.Instant;
import java.util.UUID;

public final class MediaDtos {

    private MediaDtos() {
    }

    public record MediaView(
        UUID id,
        String sha256,
        String mimeType,
        long sizeBytes,
        Integer width,
        Integer height,
        String url,
        boolean deduped,
        Instant createdAt
    ) {
    }

    public record MediaContent(byte[] bytes, String mimeType, String filename) {
    }
}
