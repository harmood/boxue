package com.boxue.api.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.boxue.api.content.BankRepository;
import com.boxue.api.content.BankRepository.BankRow;
import com.boxue.api.content.ContentException;
import com.boxue.api.sync.SyncDtos.SyncAccepted;
import com.boxue.api.sync.SyncDtos.SyncChangeItem;
import com.boxue.api.sync.SyncDtos.SyncChangeRequest;
import com.boxue.api.sync.SyncDtos.SyncPullResponse;
import com.boxue.api.sync.SyncDtos.SyncPushRequest;
import com.boxue.api.sync.SyncDtos.SyncPushResponse;
import com.boxue.api.sync.SyncDtos.SyncRejected;
import com.boxue.api.sync.SyncDtos.SyncStatusResponse;
import com.boxue.api.sync.SyncRepository.ChangeRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class SyncService {

    private final SyncRepository repository;
    private final BankRepository bankRepository;
    private final BankSyncSnapshot bankSyncSnapshot;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public SyncService(SyncRepository repository, BankRepository bankRepository,
                       BankSyncSnapshot bankSyncSnapshot, ObjectMapper objectMapper,
                       TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.bankRepository = bankRepository;
        this.bankSyncSnapshot = bankSyncSnapshot;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public SyncPullResponse pull(UUID ownerId, UUID deviceId, Long cursor, Integer limit) {
        long after = cursor == null || cursor < 0 ? 0 : cursor;
        int pageSize = limit == null ? 500 : Math.max(1, Math.min(limit, 500));
        List<ChangeRow> rows = repository.findChanges(ownerId, after, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<ChangeRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        List<SyncChangeItem> changes = page.stream()
            .map(row -> new SyncChangeItem(row.seq(), row.entityType(), row.entityId(), row.operation(),
                row.entityVersion(), parsePayload(row.payloadJson()), row.createdAt()))
            .toList();
        long nextCursor = page.isEmpty() ? after : page.get(page.size() - 1).seq();
        repository.touchPull(ownerId, deviceId);
        return new SyncPullResponse(changes, nextCursor, hasMore, Instant.now());
    }

    public SyncPushResponse push(UUID ownerId, UUID principalDeviceId, SyncPushRequest request) {
        UUID deviceId = request.deviceId() == null ? principalDeviceId : request.deviceId();
        if (!repository.deviceOwned(ownerId, deviceId)) {
            throw new ContentException(HttpStatus.BAD_REQUEST, "SYNC_DEVICE_INVALID",
                "设备不存在或不属于当前用户");
        }
        List<SyncAccepted> accepted = new ArrayList<>();
        List<SyncRejected> rejected = new ArrayList<>();
        for (SyncChangeRequest change : request.changes()) {
            try {
                SyncAccepted result = transactionTemplate.execute(status -> apply(ownerId, deviceId, change));
                if (result != null) {
                    accepted.add(result);
                }
            } catch (SyncRejectedException ex) {
                rejected.add(new SyncRejected(change.clientChangeId(), ex.code(), ex.getMessage(), ex.conflict()));
            } catch (ContentException ex) {
                rejected.add(new SyncRejected(change.clientChangeId(), ex.code(), ex.getMessage(),
                    objectMapper.valueToTree(ex.details() == null ? java.util.Map.of() : ex.details())));
            } catch (RuntimeException ex) {
                rejected.add(new SyncRejected(change.clientChangeId(), "SYNC_INTERNAL_ERROR",
                    "同步变更处理失败", null));
            }
        }
        repository.touchPush(ownerId, deviceId);
        return new SyncPushResponse(accepted, rejected, Instant.now());
    }

    public SyncStatusResponse status(UUID ownerId, UUID deviceId) {
        SyncRepository.DeviceSyncRow device = repository.findDeviceSync(ownerId, deviceId).orElse(null);
        return new SyncStatusResponse(
            deviceId,
            repository.maxSeq(ownerId),
            device == null ? null : device.lastPulledAt(),
            device == null ? null : device.lastPushedAt(),
            0,
            Instant.now()
        );
    }

    private SyncAccepted apply(UUID ownerId, UUID deviceId, SyncChangeRequest change) {
        var idempotent = repository.findIdempotency(ownerId, change.clientChangeId());
        if (idempotent.isPresent()) {
            return parseStored(idempotent.get().responseJson());
        }
        String entityType = change.entityType().trim().toUpperCase(Locale.ROOT);
        if (!"BANK".equals(entityType)) {
            throw new SyncRejectedException("SYNC_ENTITY_UNSUPPORTED", "当前同步链路仅支持 BANK", null);
        }
        String operation = change.operation().trim().toUpperCase(Locale.ROOT);
        BankInput input = "DELETE".equals(operation) ? null : bankInput(change.payload());

        SyncAccepted result = switch (operation) {
            case "CREATE" -> createBank(ownerId, deviceId, change, input);
            case "UPSERT" -> upsertBank(ownerId, deviceId, change, input);
            case "DELETE" -> deleteBank(ownerId, deviceId, change);
            default -> throw new SyncRejectedException("SYNC_OPERATION_INVALID", "不支持的同步操作", null);
        };
        repository.insertIdempotency(ownerId, change.clientChangeId(), deviceId,
            storedResponse(result));
        return result;
    }

    private SyncAccepted createBank(UUID ownerId, UUID deviceId, SyncChangeRequest change, BankInput input) {
        if (bankRepository.findBank(ownerId, change.entityId()).isPresent()) {
            throw new SyncRejectedException("BANK_ALREADY_EXISTS", "题库已存在", null);
        }
        if (bankRepository.existsName(ownerId, input.name(), null)) {
            throw new SyncRejectedException("BANK_NAME_EXISTS", "题库名称已存在", null);
        }
        bankRepository.insertBankWithId(change.entityId(), ownerId, input.name(), input.description(),
            input.visibility(), input.status());
        BankRow row = requireBank(ownerId, change.entityId());
        long seq = repository.appendChange(ownerId, "BANK", row.id(), "UPSERT", row.version(),
            toJson(bankSyncSnapshot.snapshot(row)), deviceId);
        return new SyncAccepted(change.clientChangeId(), seq, row.version());
    }

    private SyncAccepted upsertBank(UUID ownerId, UUID deviceId, SyncChangeRequest change, BankInput input) {
        BankRow existing = requireBank(ownerId, change.entityId());
        if (change.baseVersion() == null) {
            throw new SyncRejectedException("BANK_VERSION_REQUIRED", "题库更新必须携带 baseVersion",
                conflict(existing));
        }
        if (!change.baseVersion().equals(existing.version())) {
            throw new SyncRejectedException("BANK_VERSION_CONFLICT", "题库已被其他设备修改",
                conflict(existing));
        }
        if (!input.name().equalsIgnoreCase(existing.name())
            && bankRepository.existsName(ownerId, input.name(), change.entityId())) {
            throw new SyncRejectedException("BANK_NAME_EXISTS", "题库名称已存在",
                conflict(existing));
        }
        int updated = bankRepository.updateBankFull(change.entityId(), ownerId, input.name(),
            input.description(), input.visibility(), input.status(), change.baseVersion());
        if (updated == 0) {
            BankRow current = requireBank(ownerId, change.entityId());
            throw new SyncRejectedException("BANK_VERSION_CONFLICT", "题库已被其他设备修改",
                conflict(current));
        }
        BankRow row = requireBank(ownerId, change.entityId());
        long seq = repository.appendChange(ownerId, "BANK", row.id(), "UPSERT", row.version(),
            toJson(bankSyncSnapshot.snapshot(row)), deviceId);
        return new SyncAccepted(change.clientChangeId(), seq, row.version());
    }

    private SyncAccepted deleteBank(UUID ownerId, UUID deviceId, SyncChangeRequest change) {
        BankRow existing = requireBank(ownerId, change.entityId());
        if (change.baseVersion() != null && !change.baseVersion().equals(existing.version())) {
            throw new SyncRejectedException("BANK_VERSION_CONFLICT", "题库已被其他设备修改",
                conflict(existing));
        }
        long questionCount = bankRepository.countQuestions(existing.id());
        if (questionCount > 0) {
            throw new SyncRejectedException("BANK_NOT_EMPTY", "题库中仍有题目，不能通过同步删除",
                objectMapper.valueToTree(java.util.Map.of("questionCount", questionCount)));
        }
        bankRepository.softDeleteBank(existing.id(), ownerId);
        long seq = repository.appendChange(ownerId, "BANK", existing.id(), "DELETE", null,
            "{\"deletedBy\":\"SYNC\"}", deviceId);
        return new SyncAccepted(change.clientChangeId(), seq, null);
    }

    private BankInput bankInput(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new SyncRejectedException("SYNC_PAYLOAD_INVALID", "payload 必须是 JSON 对象", null);
        }
        String name = text(payload, "name");
        if (!StringUtils.hasText(name)) {
            throw new SyncRejectedException("SYNC_PAYLOAD_INVALID", "题库名称不能为空", null);
        }
        name = name.trim();
        if (name.length() > 200) {
            throw new SyncRejectedException("SYNC_PAYLOAD_INVALID", "题库名称过长", null);
        }
        String description = text(payload, "description");
        if (description != null && description.length() > 5000) {
            throw new SyncRejectedException("SYNC_PAYLOAD_INVALID", "题库描述过长", null);
        }
        String visibility = text(payload, "visibility");
        visibility = StringUtils.hasText(visibility) ? visibility.toUpperCase(Locale.ROOT) : "PRIVATE";
        if (!List.of("PRIVATE", "UNLISTED", "PUBLIC").contains(visibility)) {
            throw new SyncRejectedException("SYNC_PAYLOAD_INVALID", "题库可见性不合法", null);
        }
        String status = text(payload, "status");
        status = StringUtils.hasText(status) ? status.toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!List.of("ACTIVE", "ARCHIVED").contains(status)) {
            throw new SyncRejectedException("SYNC_PAYLOAD_INVALID", "题库状态不合法", null);
        }
        return new BankInput(name, description, visibility, status);
    }

    private JsonNode conflict(BankRow row) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("currentVersion", row.version());
        node.set("currentPayload", bankSyncSnapshot.snapshot(row));
        return node;
    }

    private BankRow requireBank(UUID ownerId, UUID bankId) {
        return bankRepository.findBank(ownerId, bankId)
            .orElseThrow(() -> new SyncRejectedException("BANK_NOT_FOUND", "题库不存在", null));
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception ex) {
            return NullNode.getInstance();
        }
    }

    private SyncAccepted parseStored(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new SyncAccepted(
                UUID.fromString(node.path("clientChangeId").asText()),
                node.path("serverSeq").asLong(),
                node.hasNonNull("entityVersion") ? node.get("entityVersion").asInt() : null
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Stored sync result is corrupt", ex);
        }
    }

    private String storedResponse(SyncAccepted result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("clientChangeId", result.clientChangeId().toString());
        node.put("serverSeq", result.serverSeq());
        if (result.entityVersion() == null) {
            node.putNull("entityVersion");
        } else {
            node.put("entityVersion", result.entityVersion());
        }
        return toJson(node);
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Sync serialization failed", ex);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private record BankInput(String name, String description, String visibility, String status) {
    }

    private static final class SyncRejectedException extends RuntimeException {
        private final String code;
        private final JsonNode conflict;

        private SyncRejectedException(String code, String message, JsonNode conflict) {
            super(message);
            this.code = code;
            this.conflict = conflict;
        }

        private String code() {
            return code;
        }

        private JsonNode conflict() {
            return conflict;
        }
    }
}
