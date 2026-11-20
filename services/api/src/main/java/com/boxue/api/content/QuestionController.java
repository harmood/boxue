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
import com.boxue.api.content.QuestionDtos.CreateQuestionRequest;
import com.boxue.api.content.QuestionDtos.DuplicateQuestionRequest;
import com.boxue.api.content.QuestionDtos.QuestionPage;
import com.boxue.api.content.QuestionDtos.QuestionVersionPage;
import com.boxue.api.content.QuestionDtos.QuestionView;
import com.boxue.api.content.QuestionDtos.UpdateQuestionRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/banks/{bankId}/questions")
    public QuestionPage list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID bankId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) UUID knowledgePointId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return questionService.list(principal.userId(), bankId, type, status, q, tagId,
            knowledgePointId, cursor, limit);
    }

    @PostMapping("/banks/{bankId}/questions")
    public ResponseEntity<QuestionView> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID bankId,
            @Valid @RequestBody CreateQuestionRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(questionService.create(principal.userId(), bankId, request, httpRequest));
    }

    @GetMapping("/questions/search")
    public QuestionPage search(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID bankId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) UUID knowledgePointId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return questionService.search(principal.userId(), q, bankId, type, status, tagId,
            knowledgePointId, cursor, limit);
    }

    @GetMapping("/questions/{questionId}")
    public QuestionView get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID questionId) {
        return questionService.get(principal.userId(), questionId);
    }

    @PatchMapping("/questions/{questionId}")
    public QuestionView update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request,
            HttpServletRequest httpRequest) {
        return questionService.update(principal.userId(), questionId, request, httpRequest);
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID questionId,
            HttpServletRequest httpRequest) {
        questionService.delete(principal.userId(), questionId, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/questions/{questionId}/versions")
    public QuestionVersionPage versions(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID questionId) {
        return questionService.versions(principal.userId(), questionId);
    }

    @PostMapping("/questions/{questionId}/duplicate")
    public ResponseEntity<QuestionView> duplicate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID questionId,
            @Valid @RequestBody DuplicateQuestionRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(questionService.duplicate(principal.userId(), questionId, request, httpRequest));
    }

    @PostMapping("/questions/{questionId}/publish")
    public QuestionView publish(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID questionId,
            HttpServletRequest httpRequest) {
        return questionService.publish(principal.userId(), questionId, httpRequest);
    }

    @PostMapping("/questions/{questionId}/archive")
    public QuestionView archive(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID questionId,
            HttpServletRequest httpRequest) {
        return questionService.archive(principal.userId(), questionId, httpRequest);
    }
}
