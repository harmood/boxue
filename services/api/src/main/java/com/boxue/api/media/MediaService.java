package com.boxue.api.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.boxue.api.auth.AuditService;
import com.boxue.api.media.MediaDtos.MediaContent;
import com.boxue.api.media.MediaDtos.MediaView;
import com.boxue.api.media.MediaRepository.MediaRow;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class MediaService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
        "image/png", "png",
        "image/jpeg", "jpg",
        "image/webp", "webp",
        "image/gif", "gif"
    );

    private final MediaRepository repository;
    private final StorageService storageService;
    private final MediaProperties properties;
    private final AuditService auditService;

    public MediaService(MediaRepository repository, StorageService storageService,
                        MediaProperties properties, AuditService auditService) {
        this.repository = repository;
        this.storageService = storageService;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional
    public MediaView upload(UUID ownerId, MultipartFile file, HttpServletRequest httpRequest) {
        if (file == null || file.isEmpty()) {
            throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_FILE_REQUIRED", "请选择要上传的文件");
        }
        int maxSizeMb = Math.max(1, Math.min(properties.getMaxSizeMb(), 100));
        long maxBytes = maxSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new MediaException(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE",
                "图片不能超过 " + maxSizeMb + " MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_READ_FAILED", "读取上传文件失败");
        }
        String mimeType = detectMime(bytes);
        if (mimeType == null) {
            throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_TYPE_NOT_ALLOWED",
                "仅支持 PNG、JPEG、WebP、GIF 图片");
        }
        String sha256 = sha256(bytes);
        String extension = ALLOWED_TYPES.get(mimeType);
        String storageKey = ownerId + "/" + sha256 + "." + extension;
        Integer[] size = imageSize(bytes);

        MediaRow existing = repository.findByOwnerAndSha(ownerId, sha256).orElse(null);
        if (existing != null && existing.deletedAt() == null) {
            return toView(existing, true);
        }
        if (existing != null) {
            storageService.put(storageKey, bytes, mimeType);
            repository.revive(existing.id(), mimeType, bytes.length, size[0], size[1], storageKey);
            MediaRow revived = repository.findByIdAndOwner(existing.id(), ownerId).orElseThrow();
            auditService.log(ownerId, "MEDIA_RESTORED", "MEDIA", existing.id().toString(),
                Map.of("mimeType", mimeType, "sizeBytes", bytes.length), httpRequest);
            return toView(revived, false);
        }

        storageService.put(storageKey, bytes, mimeType);
        UUID mediaId = UUID.randomUUID();
        repository.insert(mediaId, ownerId, sha256, mimeType, bytes.length,
            size[0], size[1], storageKey);
        auditService.log(ownerId, "MEDIA_UPLOADED", "MEDIA", mediaId.toString(),
            Map.of("mimeType", mimeType, "sizeBytes", bytes.length), httpRequest);
        return toView(repository.findByIdAndOwner(mediaId, ownerId).orElseThrow(), false);
    }

    public MediaView get(UUID ownerId, UUID mediaId) {
        return toView(requireMedia(ownerId, mediaId), false);
    }

    public MediaContent content(UUID ownerId, UUID mediaId) {
        MediaRow row = requireMedia(ownerId, mediaId);
        byte[] bytes = storageService.get(row.storageKey());
        String extension = ALLOWED_TYPES.getOrDefault(row.mimeType(), "bin");
        return new MediaContent(bytes, row.mimeType(), mediaId + "." + extension);
    }

    @Transactional
    public void delete(UUID ownerId, UUID mediaId, HttpServletRequest httpRequest) {
        MediaRow row = requireMedia(ownerId, mediaId);
        long questionReferences = repository.countQuestionReferences(mediaId);
        long profileReferences = repository.countProfileReferences(mediaId);
        if (questionReferences > 0 || profileReferences > 0) {
            throw new MediaException(HttpStatus.CONFLICT, "MEDIA_IN_USE",
                "图片仍被题目或头像引用，请先解除引用",
                Map.of("questionCount", questionReferences, "profileCount", profileReferences));
        }
        storageService.delete(row.storageKey());
        repository.softDelete(mediaId, ownerId);
        auditService.log(ownerId, "MEDIA_DELETED", "MEDIA", mediaId.toString(),
            Map.of("mimeType", row.mimeType()), httpRequest);
    }

    public boolean allOwned(UUID ownerId, List<UUID> mediaIds) {
        for (UUID mediaId : mediaIds) {
            if (repository.findByIdAndOwner(mediaId, ownerId).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private MediaRow requireMedia(UUID ownerId, UUID mediaId) {
        return repository.findByIdAndOwner(mediaId, ownerId)
            .orElseThrow(() -> new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "图片不存在"));
    }

    private MediaView toView(MediaRow row, boolean deduped) {
        return new MediaView(row.id(), row.sha256(), row.mimeType(), row.sizeBytes(),
            row.width(), row.height(), "/api/v1/media/" + row.id() + "/content", deduped,
            row.createdAt());
    }

    private String detectMime(byte[] bytes) {
        if (bytes.length >= 8
            && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 3
            && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 6
            && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return "image/gif";
        }
        if (bytes.length >= 12
            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private Integer[] imageSize(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return new Integer[] {null, null};
            }
            return new Integer[] {image.getWidth(), image.getHeight()};
        } catch (Exception ex) {
            return new Integer[] {null, null};
        }
    }
}

