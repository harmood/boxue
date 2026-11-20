package com.boxue.api.content;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.boxue.api.auth.AuditService;
import com.boxue.api.sync.BankSyncSnapshot;
import com.boxue.api.sync.SyncRepository;
import com.boxue.api.content.BankDtos.AdminBankPage;
import com.boxue.api.content.BankDtos.AdminBankView;
import com.boxue.api.content.BankDtos.BankPage;
import com.boxue.api.content.BankDtos.BankView;
import com.boxue.api.content.BankDtos.CreateBankRequest;
import com.boxue.api.content.BankDtos.UpdateBankRequest;
import com.boxue.api.content.BankRepository.AdminBankRow;
import com.boxue.api.content.BankRepository.BankRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class BankService {

    private final BankRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SyncRepository syncRepository;
    private final BankSyncSnapshot bankSyncSnapshot;

    public BankService(BankRepository repository, AuditService auditService, ObjectMapper objectMapper,
                       SyncRepository syncRepository, BankSyncSnapshot bankSyncSnapshot) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.syncRepository = syncRepository;
        this.bankSyncSnapshot = bankSyncSnapshot;
    }

    public BankPage list(UUID ownerId, String status, String q, String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor decoded = decodeCursor(cursor);
        List<BankRow> rows = repository.findBanks(ownerId, status, q,
            decoded == null ? null : decoded.time(),
            decoded == null ? null : decoded.id(),
            pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<BankRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<BankView> items = page.stream().map(this::toView).toList();
        String nextCursor = hasMore && !page.isEmpty()
            ? encodeCursor(page.get(page.size() - 1).updatedAt(), page.get(page.size() - 1).id())
            : null;
        return new BankPage(items, nextCursor, hasMore);
    }

    @Transactional
    public BankView create(UUID ownerId, CreateBankRequest request, HttpServletRequest httpRequest) {
        String name = request.name().trim();
        String visibility = StringUtils.hasText(request.visibility()) ? request.visibility() : "PRIVATE";
        if (repository.existsName(ownerId, name, null)) {
            throw new ContentException(HttpStatus.CONFLICT, "BANK_NAME_EXISTS", "题库名称已存在");
        }
        UUID bankId = UUID.randomUUID();
        repository.insertBank(bankId, ownerId, name, trimToNull(request.description()), visibility);
        auditService.log(ownerId, "BANK_CREATED", "BANK", bankId.toString(),
            Map.of("name", name, "visibility", visibility), httpRequest);
        BankRow created = requireBank(ownerId, bankId);
        syncRepository.appendChange(ownerId, "BANK", bankId, "UPSERT", created.version(),
            toJson(bankSyncSnapshot.snapshot(created)), null);
        return toView(created);
    }

    public BankView get(UUID ownerId, UUID bankId) {
        return toView(requireBank(ownerId, bankId));
    }

    @Transactional
    public BankView update(UUID ownerId, UUID bankId, UpdateBankRequest request,
                           HttpServletRequest httpRequest) {
        BankRow existing = requireBank(ownerId, bankId);
        if (request.version() == null || request.version() != existing.version()) {
            throw new ContentException(HttpStatus.CONFLICT, "BANK_VERSION_CONFLICT",
                "题库已被其他设备修改", Map.of("currentVersion", existing.version()));
        }

        String name = StringUtils.hasText(request.name()) ? request.name().trim() : existing.name();
        if (!StringUtils.hasText(name)) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "BANK_NAME_REQUIRED", "题库名称不能为空");
        }
        String description = request.description() != null
            ? trimToNull(request.description()) : existing.description();
        String status = StringUtils.hasText(request.status()) ? request.status() : existing.status();
        if (!List.of("ACTIVE", "ARCHIVED").contains(status)) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "BANK_INVALID_STATUS", "题库状态不合法");
        }
        if (!name.equalsIgnoreCase(existing.name()) && repository.existsName(ownerId, name, bankId)) {
            throw new ContentException(HttpStatus.CONFLICT, "BANK_NAME_EXISTS", "题库名称已存在");
        }

        int updated = repository.updateBank(bankId, ownerId, name, description, status, request.version());
        if (updated == 0) {
            throw new ContentException(HttpStatus.CONFLICT, "BANK_VERSION_CONFLICT", "题库已被其他设备修改");
        }
        auditService.log(ownerId, "BANK_UPDATED", "BANK", bankId.toString(),
            Map.of("name", name, "status", status), httpRequest);
        BankRow saved = requireBank(ownerId, bankId);
        syncRepository.appendChange(ownerId, "BANK", bankId, "UPSERT", saved.version(),
            toJson(bankSyncSnapshot.snapshot(saved)), null);
        return toView(saved);
    }

    @Transactional
    public void delete(UUID ownerId, UUID bankId, boolean cascade, HttpServletRequest httpRequest) {
        requireBank(ownerId, bankId);
        long questionCount = repository.countQuestions(bankId);
        if (questionCount > 0 && !cascade) {
            throw new ContentException(HttpStatus.CONFLICT, "BANK_NOT_EMPTY",
                "题库中仍有题目，请使用 cascade=true 确认删除",
                Map.of("questionCount", questionCount));
        }
        if (cascade && questionCount > 0) {
            for (UUID questionId : repository.findQuestionIds(bankId)) {
                repository.insertQuestionTombstone(ownerId, questionId, "{\"deletedBy\":\"BANK_CASCADE\"}");
            }
            repository.softDeleteQuestions(bankId);
        }
        repository.softDeleteBank(bankId, ownerId);
        syncRepository.appendChange(ownerId, "BANK", bankId, "DELETE", null, tombstonePayload(), null);
        auditService.log(ownerId, "BANK_DELETED", "BANK", bankId.toString(),
            Map.of("cascade", cascade, "questionCount", questionCount), httpRequest);
    }

    public AdminBankPage adminList(String q, UUID ownerId, String status, String visibility,
                                   String cursor, Integer limit) {
        int pageSize = clampLimit(limit);
        Cursor decoded = decodeCursor(cursor);
        List<AdminBankRow> rows = repository.findAdminBanks(q, ownerId, status, visibility,
            decoded == null ? null : decoded.time(),
            decoded == null ? null : decoded.id(),
            pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<AdminBankRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<AdminBankView> items = page.stream().map(this::toAdminView).toList();
        String nextCursor = hasMore && !page.isEmpty()
            ? encodeCursor(page.get(page.size() - 1).updatedAt(), page.get(page.size() - 1).id())
            : null;
        return new AdminBankPage(items, nextCursor, hasMore);
    }

    public AdminBankView adminGet(UUID bankId) {
        AdminBankRow row = repository.findAdminBank(bankId)
            .orElseThrow(() -> new ContentException(HttpStatus.NOT_FOUND, "BANK_NOT_FOUND", "题库不存在"));
        return toAdminView(row);
    }

    private BankRow requireBank(UUID ownerId, UUID bankId) {
        return repository.findBank(ownerId, bankId)
            .orElseThrow(() -> new ContentException(HttpStatus.NOT_FOUND, "BANK_NOT_FOUND", "题库不存在"));
    }

    private BankView toView(BankRow row) {
        return new BankView(
            row.id(), row.name(), row.description(), row.visibility(), row.status(),
            repository.countQuestions(row.id()), 0, 0.0, row.version(),
            row.createdAt(), row.updatedAt());
    }

    private AdminBankView toAdminView(AdminBankRow row) {
        return new AdminBankView(
            row.id(), row.ownerId(), row.ownerEmail(), row.name(), row.description(),
            row.visibility(), row.status(), repository.countQuestions(row.id()), row.version(),
            row.createdAt(), row.updatedAt());
    }

    private String toJson(com.fasterxml.jackson.databind.JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String tombstonePayload() {
        try {
            return objectMapper.writeValueAsString(Map.of("deletedBy", "USER"));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
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
        if (cursor == null || cursor.isBlank()) {
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
