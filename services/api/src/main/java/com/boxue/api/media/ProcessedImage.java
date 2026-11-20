package com.boxue.api.media;

public record ProcessedImage(
    byte[] bytes,
    String mimeType,
    Integer width,
    Integer height
) {
}
