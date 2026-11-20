package com.boxue.api.admin;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.admin.AdminDtos.CreateRoleRequest;
import com.boxue.api.admin.AdminDtos.PermissionListResponse;
import com.boxue.api.admin.AdminDtos.Role;
import com.boxue.api.admin.AdminDtos.RoleListResponse;
import com.boxue.api.admin.AdminDtos.UpdateRoleRequest;
import com.boxue.api.auth.AuthPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoleController {

    private final AdminService adminService;

    public AdminRoleController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/roles")
    public RoleListResponse listRoles() {
        return adminService.listRoles();
    }

    @PostMapping("/roles")
    public ResponseEntity<Role> createRole(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateRoleRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(adminService.createRole(principal, request, httpRequest));
    }

    @PutMapping("/roles/{roleId}")
    public Role updateRole(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            HttpServletRequest httpRequest) {
        return adminService.updateRole(principal, roleId, request, httpRequest);
    }

    @DeleteMapping("/roles/{roleId}")
    public ResponseEntity<Void> deleteRole(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID roleId,
            HttpServletRequest httpRequest) {
        adminService.deleteRole(principal, roleId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    public PermissionListResponse listPermissions() {
        return adminService.listPermissions();
    }
}
