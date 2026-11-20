package com.boxue.api.content;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.auth.AuthPrincipal;
import com.boxue.api.content.BankDtos.BankPage;
import com.boxue.api.content.BankDtos.BankView;
import com.boxue.api.content.BankDtos.CreateBankRequest;
import com.boxue.api.content.BankDtos.UpdateBankRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/banks")
public class BankController {

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    @GetMapping
    public BankPage list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return bankService.list(principal.userId(), status, q, cursor, limit);
    }

    @PostMapping
    public ResponseEntity<BankView> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateBankRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(bankService.create(principal.userId(), request, httpRequest));
    }

    @GetMapping("/{bankId}")
    public BankView get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID bankId) {
        return bankService.get(principal.userId(), bankId);
    }

    @PatchMapping("/{bankId}")
    public BankView update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID bankId,
            @Valid @RequestBody UpdateBankRequest request,
            HttpServletRequest httpRequest) {
        return bankService.update(principal.userId(), bankId, request, httpRequest);
    }

    @DeleteMapping("/{bankId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID bankId,
            @RequestParam(defaultValue = "false") boolean cascade,
            HttpServletRequest httpRequest) {
        bankService.delete(principal.userId(), bankId, cascade, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
