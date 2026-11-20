package com.boxue.api.sync;

import org.springframework.stereotype.Component;

import com.boxue.api.content.BankRepository.BankRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class BankSyncSnapshot {

    private final ObjectMapper objectMapper;

    public BankSyncSnapshot(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode snapshot(BankRow row) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", row.id().toString());
        node.put("name", row.name());
        node.put("description", row.description());
        node.put("visibility", row.visibility());
        node.put("status", row.status());
        node.put("version", row.version());
        node.put("createdAt", row.createdAt().toString());
        node.put("updatedAt", row.updatedAt().toString());
        return node;
    }
}
