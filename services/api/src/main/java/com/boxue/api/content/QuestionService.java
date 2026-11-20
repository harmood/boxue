package com.boxue.api.content;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.boxue.api.auth.AuditService;
import com.boxue.api.content.QuestionDtos.AdminQuestionPage;
import com.boxue.api.content.QuestionDtos.AdminQuestionView;
import com.boxue.api.content.QuestionDtos.ContentDoc;
import com.boxue.api.content.QuestionDtos.CreateQuestionRequest;
import com.boxue.api.content.QuestionDtos.DuplicateQuestionRequest;
import com.boxue.api.content.QuestionDtos.QuestionPage;
import com.boxue.api.content.QuestionDtos.QuestionVersionItem;
import com.boxue.api.content.QuestionDtos.QuestionVersionPage;
import com.boxue.api.content.QuestionDtos.QuestionView;
import com.boxue.api.content.QuestionDtos.UpdateContentDoc;
import com.boxue.api.content.QuestionDtos.UpdateQuestionRequest;
import com.boxue.api.content.QuestionRepository.AdminQuestionRow;
import com.boxue.api.content.QuestionRepository.QuestionRow;
import com.boxue.api.content.QuestionRepository.VersionRow;
import com.boxue.api.media.MediaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class QuestionService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

    private final QuestionRepository repository;
    private final QuestionPayloadValidator validator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MediaService mediaService;

    public QuestionService(QuestionRepository repository, QuestionPayloadValidator validator,
                           AuditService auditService, ObjectMapper objectMapper,
                           MediaService mediaService) {
        this.repository = repository;
        this.validator = validator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.mediaService = mediaService;
    }

    public QuestionPage list(UUID ownerId, UUID bankId, String type, String status, String q,
                             UUID tagId, UUID knowledgePointId, String cursor, Integer limit) {
        requireBank(ownerId, bankId);
        int pageSize = clampLimit(limit);
        Cursor decoded = decodeCursor(cursor);
        List<QuestionRow> rows = repository.findQuestions(ownerId, bankId, type, status, q, tagId,
            knowledgePointId, decoded == null ? null : decoded.time(), decoded == null ? null : decoded.id(),
            pageSize + 1);
        return toPage(rows, pageSize);
    }

    public QuestionPage search(UUID ownerId, String q, UUID bankId, String type, String status,
                               UUID tagId, UUID knowledgePointId, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor decoded = decodeCursor(cursor);
        List<QuestionRow> rows = repository.findQuestions(ownerId, bankId, type, status, q, tagId,
            knowledgePointId, decoded == null ? null : decoded.time(), decoded == null ? null : decoded.id(),
            pageSize + 1);
        return toPage(rows, pageSize);
    }

    public QuestionView get(UUID ownerId, UUID questionId) {
        return toView(requireQuestion(ownerId, questionId));
    }

    @Transactional
    public QuestionView create(UUID ownerId, UUID bankId, CreateQuestionRequest request,
                               HttpServletRequest httpRequest) {
        requireBank(ownerId, bankId);
        String type = request.type().trim().toUpperCase(Locale.ROOT);
        validator.validateType(type);
        String status = normalizeStatus(request.status(), "DRAFT");
        validateContentDoc(request.stem(), "stem");
        validateContentDoc(request.explanation(), "explanation");
        int difficulty = request.difficulty() == null ? 3 : request.difficulty();
        JsonNode payload = validator.normalizeAndValidate(type, request.payload());
        List<UUID> mediaIds = distinct(request.mediaIds());
        List<UUID> tagIds = distinct(request.tagIds());
        List<UUID> knowledgePointIds = distinct(request.knowledgePointIds());
        validateMedia(ownerId, mediaIds);
        validateRelations(ownerId, tagIds, knowledgePointIds);

        UUID questionId = UUID.randomUUID();
        String payloadJson = toJson(payload);
        String searchText = buildSearchText(request.stem().content(), request.explanation(), payload);
        repository.insertQuestion(questionId, ownerId, bankId, type, status,
            request.stem().content(), explanationText(request.explanation()), difficulty,
            payloadJson, searchText, trimToNull(request.source()));
        repository.replaceMedia(questionId, mediaIds);
        repository.replaceTags(questionId, tagIds);
        repository.replaceKnowledgePoints(questionId, knowledgePointIds);
        String snapshotJson = snapshot(type, request.stem(), request.explanation(),
            difficulty, payload, mediaIds, tagIds, knowledgePointIds, request.source(), status);
        repository.insertVersion(questionId, 1, snapshotJson, ownerId);
        repository.insertSyncChange(ownerId, questionId, 1, snapshotJson);
        auditService.log(ownerId, "QUESTION_CREATED", "QUESTION", questionId.toString(),
            Map.of("bankId", bankId.toString(), "type", type, "status", status), httpRequest);
        return toView(requireQuestion(ownerId, questionId));
    }

    @Transactional
    public QuestionView update(UUID ownerId, UUID questionId, UpdateQuestionRequest request,
                               HttpServletRequest httpRequest) {
        QuestionRow existing = requireQuestion(ownerId, questionId);
        if (request.version() == null || request.version() != existing.version()) {
            throw new ContentException(HttpStatus.CONFLICT, "QUESTION_VERSION_CONFLICT",
                "题目已被其他设备修改", Map.of("currentVersion", existing.version()));
        }
        String type = existing.type();
        ContentDoc stem = request.stem() != null ? request.stem() : toDoc(existing.stemMd());
        ContentDoc explanation = request.explanation() != null
            ? toDocNullable(request.explanation()) : toDoc(existing.explanationMd());
        validateContentDoc(stem, "stem");
        validateContentDoc(explanation, "explanation");
        int difficulty = request.difficulty() != null ? request.difficulty() : existing.difficulty();
        String status = request.status() != null ? normalizeStatus(request.status(), existing.status())
            : existing.status();
        JsonNode payload = request.payload() != null
            ? validator.normalizeAndValidate(type, request.payload())
            : validator.normalizeAndValidate(type, parseJson(existing.payloadJson()));
        String source = request.source() != null ? trimToNull(request.source()) : existing.source();

        List<UUID> mediaIds = request.mediaIds() != null ? distinct(request.mediaIds()) : null;
        List<UUID> tagIds = request.tagIds() != null ? distinct(request.tagIds()) : null;
        List<UUID> knowledgePointIds = request.knowledgePointIds() != null
            ? distinct(request.knowledgePointIds()) : null;
        validateMedia(ownerId, mediaIds);
        validateRelations(ownerId, tagIds, knowledgePointIds);

        int updated = repository.updateQuestion(questionId, ownerId, status, stem.content(),
            explanationText(explanation), difficulty, toJson(payload),
            buildSearchText(stem.content(), explanation, payload), source, request.version());
        if (updated == 0) {
            throw new ContentException(HttpStatus.CONFLICT, "QUESTION_VERSION_CONFLICT", "题目已被其他设备修改");
        }
        if (mediaIds != null) {
            repository.replaceMedia(questionId, mediaIds);
        }
        if (tagIds != null) {
            repository.replaceTags(questionId, tagIds);
        }
        if (knowledgePointIds != null) {
            repository.replaceKnowledgePoints(questionId, knowledgePointIds);
        }
        QuestionRow saved = requireQuestion(ownerId, questionId);
        List<UUID> finalMediaIds = repository.findMediaIds(questionId);
        List<UUID> finalTagIds = repository.findTagIds(questionId);
        List<UUID> finalKnowledgePointIds = repository.findKnowledgePointIds(questionId);
        String snapshotJson = snapshot(saved.type(), stem, explanation,
            difficulty, payload, finalMediaIds, finalTagIds, finalKnowledgePointIds, source, status);
        repository.insertVersion(questionId, saved.version(), snapshotJson, ownerId);
        repository.insertSyncChange(ownerId, questionId, saved.version(), snapshotJson);
        auditService.log(ownerId, "QUESTION_UPDATED", "QUESTION", questionId.toString(),
            Map.of("version", saved.version(), "status", status), httpRequest);
        return toView(saved);
    }

    @Transactional
    public void delete(UUID ownerId, UUID questionId, HttpServletRequest httpRequest) {
        QuestionRow existing = requireQuestion(ownerId, questionId);
        repository.softDeleteQuestion(questionId, ownerId);
        repository.insertSyncTombstone(ownerId, questionId, tombstonePayload());
        auditService.log(ownerId, "QUESTION_DELETED", "QUESTION", questionId.toString(),
            Map.of("bankId", existing.bankId().toString()), httpRequest);
    }

    @Transactional
    public QuestionView publish(UUID ownerId, UUID questionId, HttpServletRequest httpRequest) {
        return changeStatus(ownerId, questionId, "PUBLISHED", "QUESTION_PUBLISHED", httpRequest);
    }

    @Transactional
    public QuestionView archive(UUID ownerId, UUID questionId, HttpServletRequest httpRequest) {
        return changeStatus(ownerId, questionId, "ARCHIVED", "QUESTION_ARCHIVED", httpRequest);
    }

    @Transactional
    public QuestionView duplicate(UUID ownerId, UUID questionId, DuplicateQuestionRequest request,
                                  HttpServletRequest httpRequest) {
        QuestionRow source = requireQuestion(ownerId, questionId);
        requireBank(ownerId, request.targetBankId());
        String status = normalizeStatus(request.status(), "DRAFT");
        JsonNode payload = validator.normalizeAndValidate(source.type(), parseJson(source.payloadJson()));
        List<UUID> mediaIds = repository.findMediaIds(questionId);
        List<UUID> tagIds = repository.findTagIds(questionId);
        List<UUID> knowledgePointIds = repository.findKnowledgePointIds(questionId);

        UUID newId = UUID.randomUUID();
        ContentDoc stem = toDoc(source.stemMd());
        ContentDoc explanation = toDoc(source.explanationMd());
        repository.insertQuestion(newId, ownerId, request.targetBankId(), source.type(), status,
            source.stemMd(), source.explanationMd(), source.difficulty(), toJson(payload),
            buildSearchText(source.stemMd(), explanation, payload), source.source());
        repository.replaceMedia(newId, mediaIds);
        repository.replaceTags(newId, tagIds);
        repository.replaceKnowledgePoints(newId, knowledgePointIds);
        String snapshotJson = snapshot(source.type(), stem, explanation, source.difficulty(),
            payload, mediaIds, tagIds, knowledgePointIds, source.source(), status);
        repository.insertVersion(newId, 1, snapshotJson, ownerId);
        repository.insertSyncChange(ownerId, newId, 1, snapshotJson);
        auditService.log(ownerId, "QUESTION_DUPLICATED", "QUESTION", newId.toString(),
            Map.of("sourceId", questionId.toString(), "targetBankId", request.targetBankId().toString()),
            httpRequest);
        return toView(requireQuestion(ownerId, newId));
    }

    public QuestionVersionPage versions(UUID ownerId, UUID questionId) {
        requireQuestion(ownerId, questionId);
        List<QuestionVersionItem> items = repository.findVersions(questionId).stream()
            .map(row -> new QuestionVersionItem(row.versionNo(), row.createdBy(), row.createdAt()))
            .toList();
        return new QuestionVersionPage(items);
    }

    public AdminQuestionPage adminList(String q, UUID bankId, UUID ownerId, String type, String status,
                                       String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor decoded = decodeCursor(cursor);
        List<AdminQuestionRow> rows = repository.findAdminQuestions(q, bankId, ownerId, type, status,
            decoded == null ? null : decoded.time(), decoded == null ? null : decoded.id(), pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<AdminQuestionRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<AdminQuestionView> items = page.stream().map(this::toAdminView).toList();
        String nextCursor = hasMore && !page.isEmpty()
            ? encodeCursor(page.get(page.size() - 1).updatedAt(), page.get(page.size() - 1).id())
            : null;
        return new AdminQuestionPage(items, nextCursor, hasMore);
    }

    public AdminQuestionView adminGet(UUID questionId) {
        AdminQuestionRow row = repository.findAdminQuestion(questionId)
            .orElseThrow(() -> new ContentException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "题目不存在"));
        return toAdminView(row);
    }

    private QuestionView changeStatus(UUID ownerId, UUID questionId, String status, String action,
                                      HttpServletRequest httpRequest) {
        QuestionRow existing = requireQuestion(ownerId, questionId);
        JsonNode payload = validator.normalizeAndValidate(existing.type(), parseJson(existing.payloadJson()));
        ContentDoc stem = toDoc(existing.stemMd());
        ContentDoc explanation = toDoc(existing.explanationMd());
        int updated = repository.updateQuestion(questionId, ownerId, status, existing.stemMd(),
            existing.explanationMd(), existing.difficulty(), toJson(payload),
            buildSearchText(existing.stemMd(), explanation, payload), existing.source(), existing.version());
        if (updated == 0) {
            throw new ContentException(HttpStatus.CONFLICT, "QUESTION_VERSION_CONFLICT", "题目已被其他设备修改");
        }
        QuestionRow saved = requireQuestion(ownerId, questionId);
        List<UUID> mediaIds = repository.findMediaIds(questionId);
        List<UUID> tagIds = repository.findTagIds(questionId);
        List<UUID> knowledgePointIds = repository.findKnowledgePointIds(questionId);
        String snapshotJson = snapshot(saved.type(), stem, explanation,
            saved.difficulty(), payload, mediaIds, tagIds, knowledgePointIds, saved.source(), status);
        repository.insertVersion(questionId, saved.version(), snapshotJson, ownerId);
        repository.insertSyncChange(ownerId, questionId, saved.version(), snapshotJson);
        auditService.log(ownerId, action, "QUESTION", questionId.toString(),
            Map.of("status", status, "version", saved.version()), httpRequest);
        return toView(saved);
    }

    private QuestionPage toPage(List<QuestionRow> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<QuestionRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<QuestionView> items = page.stream().map(this::toView).toList();
        String nextCursor = hasMore && !page.isEmpty()
            ? encodeCursor(page.get(page.size() - 1).updatedAt(), page.get(page.size() - 1).id())
            : null;
        return new QuestionPage(items, nextCursor, hasMore);
    }

    private void requireBank(UUID ownerId, UUID bankId) {
        if (!repository.bankExists(ownerId, bankId)) {
            throw new ContentException(HttpStatus.NOT_FOUND, "BANK_NOT_FOUND", "题库不存在");
        }
    }

    private QuestionRow requireQuestion(UUID ownerId, UUID questionId) {
        return repository.findQuestion(ownerId, questionId)
            .orElseThrow(() -> new ContentException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "题目不存在"));
    }

    private void validateMedia(UUID ownerId, List<UUID> mediaIds) {
        if (mediaIds != null && !mediaIds.isEmpty() && !mediaService.allOwned(ownerId, mediaIds)) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "MEDIA_NOT_FOUND",
                "图片不存在或不属于当前用户");
        }
    }

    private void validateRelations(UUID ownerId, List<UUID> tagIds, List<UUID> knowledgePointIds) {
        if (tagIds != null && !repository.tagsExist(ownerId, tagIds)) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "TAG_NOT_FOUND", "标签不存在或不属于当前用户");
        }
        if (knowledgePointIds != null && !repository.knowledgePointsExist(ownerId, knowledgePointIds)) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "KNOWLEDGE_POINT_NOT_FOUND",
                "知识点不存在或不属于当前用户");
        }
    }

    private void validateContentDoc(ContentDoc doc, String field) {
        if (doc == null) {
            return;
        }
        if (!"MARKDOWN_LATEX".equals(doc.format())) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "QUESTION_CONTENT_FORMAT_INVALID",
                field + ".format 仅支持 MARKDOWN_LATEX");
        }
    }

    private String normalizeStatus(String status, String fallback) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : fallback;
        if (!STATUSES.contains(value)) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "QUESTION_INVALID_STATUS", "题目状态不合法");
        }
        return value;
    }

    private List<UUID> distinct(List<UUID> ids) {
        return ids == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private QuestionView toView(QuestionRow row) {
        return new QuestionView(row.id(), row.bankId(), row.type(), row.status(),
            toDoc(row.stemMd()), toDoc(row.explanationMd()), row.difficulty(),
            repository.findMediaIds(row.id()),
            repository.findTagIds(row.id()), repository.findKnowledgePointIds(row.id()),
            row.source(), parseJson(row.payloadJson()), row.version(), row.createdAt(), row.updatedAt());
    }

    private AdminQuestionView toAdminView(AdminQuestionRow row) {
        return new AdminQuestionView(row.id(), row.ownerId(), row.ownerEmail(), row.bankId(),
            row.bankName(), row.type(), row.status(), toDoc(row.stemMd()), toDoc(row.explanationMd()),
            row.difficulty(), repository.findMediaIds(row.id()),
            repository.findTagIds(row.id()), repository.findKnowledgePointIds(row.id()),
            row.source(), parseJson(row.payloadJson()), row.version(), row.createdAt(), row.updatedAt());
    }

    private ContentDoc toDocNullable(UpdateContentDoc doc) {
        if (doc.format() != null && !"MARKDOWN_LATEX".equals(doc.format())) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "QUESTION_CONTENT_FORMAT_INVALID",
                "explanation.format 仅支持 MARKDOWN_LATEX");
        }
        return doc.content() == null || doc.content().isBlank() ? null : toDoc(doc.content());
    }

    private ContentDoc toDoc(String content) {
        return content == null ? null : new ContentDoc("MARKDOWN_LATEX", content);
    }

    private String explanationText(ContentDoc doc) {
        return doc == null ? null : doc.content();
    }

    private String buildSearchText(String stem, ContentDoc explanation, JsonNode payload) {
        StringBuilder text = new StringBuilder(stem);
        if (explanation != null) {
            text.append(' ').append(explanation.content());
        }
        JsonNode options = payload.get("options");
        if (options != null && options.isArray()) {
            for (JsonNode option : options) {
                text.append(' ').append(option.path("content").asText(""));
            }
        }
        JsonNode blanks = payload.get("blanks");
        if (blanks != null && blanks.isArray()) {
            for (JsonNode blank : blanks) {
                JsonNode accepted = blank.get("acceptedAnswers");
                if (accepted != null && accepted.isArray()) {
                    for (JsonNode answer : accepted) {
                        text.append(' ').append(answer.asText(""));
                    }
                }
            }
        }
        return text.toString();
    }

    private String snapshot(String type, ContentDoc stem, ContentDoc explanation, int difficulty,
                            JsonNode payload, List<UUID> mediaIds, List<UUID> tagIds,
                            List<UUID> knowledgePointIds, String source, String status) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("status", status);
        node.put("stemMd", stem.content());
        node.put("explanationMd", explanation == null ? null : explanation.content());
        node.put("difficulty", difficulty);
        node.set("payload", payload);
        node.set("mediaIds", objectMapper.valueToTree(mediaIds));
        node.set("tagIds", objectMapper.valueToTree(tagIds));
        node.set("knowledgePointIds", objectMapper.valueToTree(knowledgePointIds));
        node.put("source", source);
        return toJson(node);
    }

    private String tombstonePayload() {
        return "{\"deletedBy\":\"USER\"}";
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException ex) {
            throw new ContentException(HttpStatus.INTERNAL_SERVER_ERROR, "QUESTION_PAYLOAD_CORRUPTED",
                "题目 payload 无法解析");
        }
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new ContentException(HttpStatus.INTERNAL_SERVER_ERROR, "QUESTION_SERIALIZATION_FAILED",
                "题目序列化失败");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private String encodeCursor(Instant time, UUID id) {
        String raw = time.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "分页游标无效");
        }
    }

    private record Cursor(Instant time, UUID id) {
    }
}
