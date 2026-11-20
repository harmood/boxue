package com.boxue.api.admin;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.admin.AdminDtos.AdminMessageResponse;
import com.boxue.api.admin.AdminDtos.AdminUserDetail;
import com.boxue.api.admin.AdminDtos.AdminUserPage;
import com.boxue.api.admin.AdminDtos.SetUserRolesRequest;
import com.boxue.api.admin.AdminDtos.UpdateUserStatusRequest;
import com.boxue.api.auth.AuthPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminService adminService;

    public AdminUserController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public AdminUserPage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return adminService.listUsers(q, status, role, createdFrom, createdTo, cursor, limit);
    }

    @GetMapping("/{userId}")
    public AdminUserDetail get(@PathVariable UUID userId) {
        return adminService.getUser(userId);
    }

    @PatchMapping("/{userId}")
    public AdminUserDetail updateStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest httpRequest) {
        return adminService.updateStatus(principal, userId, request, httpRequest);
    }

    @PutMapping("/{userId}/roles")
    public AdminUserDetail setRoles(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody SetUserRolesRequest request,
            HttpServletRequest httpRequest) {
        return adminService.setRoles(principal, userId, request, httpRequest);
    }

    @PostMapping("/{userId}/password-reset")
    public ResponseEntity<AdminMessageResponse> triggerPasswordReset(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.accepted()
            .body(adminService.triggerPasswordReset(principal, userId, httpRequest));
    }

    @PostMapping("/{userId}/force-logout")
    public ResponseEntity<Void> forceLogout(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID userId,
            HttpServletRequest httpRequest) {
        adminService.forceLogout(principal, userId, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
