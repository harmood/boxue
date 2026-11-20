package com.boxue.api.content;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.boxue.api.content.QuestionDtos.AdminQuestionPage;
import com.boxue.api.content.QuestionDtos.AdminQuestionView;

@RestController
@RequestMapping("/api/v1/admin/questions")
public class AdminQuestionController {

    private final QuestionService questionService;

    public AdminQuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public AdminQuestionPage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID bankId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return questionService.adminList(q, bankId, ownerId, type, status, cursor, limit);
    }

    @GetMapping("/{questionId}")
    public AdminQuestionView get(@PathVariable UUID questionId) {
        return questionService.adminGet(questionId);
    }
}
