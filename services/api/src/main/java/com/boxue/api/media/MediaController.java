package com.boxue.api.media;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.boxue.api.auth.AuthPrincipal;
import com.boxue.api.media.MediaDtos.MediaContent;
import com.boxue.api.media.MediaDtos.MediaView;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaView> upload(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mediaService.upload(principal.userId(), file, httpRequest));
    }

    @GetMapping("/{mediaId}")
    public MediaView get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID mediaId) {
        return mediaService.get(principal.userId(), mediaId);
    }

    @GetMapping("/{mediaId}/content")
    public ResponseEntity<byte[]> content(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID mediaId) {
        MediaContent content = mediaService.content(principal.userId(), mediaId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.mimeType()))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + content.filename() + "\"")
            .body(content.bytes());
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID mediaId,
            HttpServletRequest httpRequest) {
        mediaService.delete(principal.userId(), mediaId, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
