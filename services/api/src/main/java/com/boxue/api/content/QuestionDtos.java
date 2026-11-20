package com.boxue.api.content;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record ContentDoc(
        @NotBlank @Size(max = 20) String format,
        @NotBlank @Size(max = 20000) String content
    ) {
    }

    public record UpdateContentDoc(
        @Size(max = 20) String format,
        @Size(max = 20000) String content
    ) {
    }

    public record QuestionView(
        UUID id,
        UUID bankId,
        String type,
        String status,
        ContentDoc stem,
        ContentDoc explanation,
        int difficulty,
        List<UUID> mediaIds,
        List<UUID> tagIds,
        List<UUID> knowledgePointIds,
        String source,
        JsonNode payload,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record QuestionPage(
        List<QuestionView> items,
        String nextCursor,
        boolean hasMore
    ) {
    }

    public record CreateQuestionRequest(
        @NotBlank String type,
        String status,
        @NotNull @Valid ContentDoc stem,
        @Valid ContentDoc explanation,
        @Min(1) @Max(5) Integer difficulty,
        List<UUID> mediaIds,
        List<UUID> tagIds,
        List<UUID> knowledgePointIds,
        @Size(max = 200) String source,
        @NotNull JsonNode payload
    ) {
    }

    public record UpdateQuestionRequest(
        @Valid ContentDoc stem,
        @Valid UpdateContentDoc explanation,
        @Min(1) @Max(5) Integer difficulty,
        List<UUID> mediaIds,
        List<UUID> tagIds,
        List<UUID> knowledgePointIds,
        @Size(max = 200) String source,
        JsonNode payload,
        String status,
        @NotNull Integer version
    ) {
    }

    public record DuplicateQuestionRequest(
        @NotNull UUID targetBankId,
        String status
    ) {
    }

    public record QuestionVersionItem(
        int versionNo,
        UUID createdBy,
        Instant createdAt
    ) {
    }

    public record QuestionVersionPage(
        List<QuestionVersionItem> items
    ) {
    }

    public record AdminQuestionView(
        UUID id,
        UUID ownerId,
        String ownerEmail,
        UUID bankId,
        String bankName,
        String type,
        String status,
        ContentDoc stem,
        ContentDoc explanation,
        int difficulty,
        List<UUID> mediaIds,
        List<UUID> tagIds,
        List<UUID> knowledgePointIds,
        String source,
        JsonNode payload,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record AdminQuestionPage(
        List<AdminQuestionView> items,
        String nextCursor,
        boolean hasMore
    ) {
    }
}
