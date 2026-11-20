package com.boxue.api.sync;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "status", "TODO",
            "message", "同步接口骨架已建立，待实现 cursor、push、pull 和冲突处理"
        );
    }
}