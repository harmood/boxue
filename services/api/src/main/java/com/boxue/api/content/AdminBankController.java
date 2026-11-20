package com.boxue.api.content;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.content.BankDtos.AdminBankPage;
import com.boxue.api.content.BankDtos.AdminBankView;

@RestController
@RequestMapping("/api/v1/admin/banks")
public class AdminBankController {

    private final BankService bankService;

    public AdminBankController(BankService bankService) {
        this.bankService = bankService;
    }

    @GetMapping
    public AdminBankPage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return bankService.adminList(q, ownerId, status, visibility, cursor, limit);
    }

    @GetMapping("/{bankId}")
    public AdminBankView get(@PathVariable UUID bankId) {
        return bankService.adminGet(bankId);
    }
}
