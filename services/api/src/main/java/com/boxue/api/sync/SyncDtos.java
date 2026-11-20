package com.boxue.api.sync;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SyncDtos {

    private SyncDtos() {
    }

    public record SyncPushRequest(
        UUID deviceId,
        @NotEmpty @Size(max = 500) List<@Valid SyncChangeRequest> changes
    ) {
    }

    public record SyncChangeRequest(
        @NotNull UUID clientChangeId,
        @NotBlank String entityType,
        @NotNull UUID entityId,
        @NotBlank String operation,
        Integer baseVersion,
        Instant clientTimestamp,
        JsonNode payload
    ) {
    }

    public record SyncAccepted(
        UUID clientChangeId,
        long serverSeq,
        Integer entityVersion
    ) {
    }

    public record SyncRejected(
        UUID clientChangeId,
        String code,
        String message,
        JsonNode conflict
    ) {
    }

    public record SyncPushResponse(
        List<SyncAccepted> accepted,
        List<SyncRejected> rejected,
        Instant serverTime
    ) {
    }

    public record SyncChangeItem(
        long seq,
        String entityType,
        UUID entityId,
        String operation,
        Integer entityVersion,
        JsonNode payload,
        Instant serverTime
    ) {
    }

    public record SyncPullResponse(
        List<SyncChangeItem> changes,
        long nextCursor,
        boolean hasMore,
        Instant serverTime
    ) {
    }

    public record SyncStatusResponse(
        UUID deviceId,
        long serverCursor,
        Instant lastPulledAt,
        Instant lastPushedAt,
        long pendingConflictCount,
        Instant serverTime
    ) {
    }
}
