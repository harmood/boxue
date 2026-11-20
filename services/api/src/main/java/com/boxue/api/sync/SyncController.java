package com.boxue.api.sync;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.auth.AuthPrincipal;
import com.boxue.api.sync.SyncDtos.SyncPullResponse;
import com.boxue.api.sync.SyncDtos.SyncPushRequest;
import com.boxue.api.sync.SyncDtos.SyncPushResponse;
import com.boxue.api.sync.SyncDtos.SyncStatusResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    public SyncPushResponse push(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SyncPushRequest request) {
        return syncService.push(principal.userId(), principal.deviceId(), request);
    }

    @GetMapping("/pull")
    public SyncPullResponse pull(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return syncService.pull(principal.userId(), principal.deviceId(), cursor, limit);
    }

    @GetMapping("/status")
    public SyncStatusResponse status(@AuthenticationPrincipal AuthPrincipal principal) {
        return syncService.status(principal.userId(), principal.deviceId());
    }
}
