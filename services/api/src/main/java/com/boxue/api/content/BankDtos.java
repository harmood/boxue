package com.boxue.api.content;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BankDtos {

    private BankDtos() {
    }

    public record BankView(
        UUID id,
        String name,
        String description,
        String visibility,
        String status,
        long questionCount,
        long practicedCount,
        double accuracy,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record BankPage(
        List<BankView> items,
        String nextCursor,
        boolean hasMore
    ) {
    }

    public record CreateBankRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @Pattern(regexp = "PRIVATE|UNLISTED|PUBLIC", message = "visibility 不合法")
        String visibility
    ) {
    }

    public record UpdateBankRequest(
        @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @Pattern(regexp = "ACTIVE|ARCHIVED", message = "status 不合法")
        String status,
        @NotNull Integer version
    ) {
    }

    public record AdminBankView(
        UUID id,
        UUID ownerId,
        String ownerEmail,
        String name,
        String description,
        String visibility,
        String status,
        long questionCount,
        int version,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record AdminBankPage(
        List<AdminBankView> items,
        String nextCursor,
        boolean hasMore
    ) {
    }
}
