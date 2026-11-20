package com.boxue.api.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "status", "TODO",
            "message", "注册接口骨架已建立，待实现邮箱验证、限流和 RBAC 默认角色"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "status", "TODO",
            "message", "登录接口骨架已建立，待实现 JWT 和设备会话"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "status", "TODO",
            "message", "刷新令牌接口骨架已建立"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}