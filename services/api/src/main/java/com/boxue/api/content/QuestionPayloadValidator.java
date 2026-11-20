package com.boxue.api.content;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class QuestionPayloadValidator {

    public static final String SINGLE_CHOICE = "SINGLE_CHOICE";
    public static final String MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    public static final String TRUE_FALSE = "TRUE_FALSE";
    public static final String FILL_BLANK = "FILL_BLANK";

    public void validateType(String type) {
        if (!Set.of(SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, FILL_BLANK).contains(type)) {
            throw invalid("不支持的题型：" + type);
        }
    }

    public JsonNode normalizeAndValidate(String type, JsonNode payload) {
        validateType(type);
        if (payload == null || !payload.isObject()) {
            throw invalid("payload 必须是 JSON 对象");
        }
        if (payload.toString().length() > 200_000) {
            throw invalid("payload 过大");
        }
        ObjectNode node = payload.deepCopy();
        switch (type) {
            case SINGLE_CHOICE -> validateChoice(node, true);
            case MULTIPLE_CHOICE -> validateChoice(node, false);
            case TRUE_FALSE -> validateTrueFalse(node);
            case FILL_BLANK -> validateFillBlank(node);
            default -> throw invalid("不支持的题型：" + type);
        }
        return node;
    }

    private void validateChoice(ObjectNode payload, boolean single) {
        JsonNode options = payload.get("options");
        if (options == null || !options.isArray() || options.size() < 2 || options.size() > 26) {
            throw invalid("选择题必须提供 2-26 个选项");
        }
        Set<String> keys = new HashSet<>();
        for (JsonNode option : options) {
            if (!option.isObject()) {
                throw invalid("选项必须是对象");
            }
            String key = text(option, "key");
            if (key == null || key.isBlank() || key.length() > 10) {
                throw invalid("选项 key 不能为空且长度不超过 10");
            }
            String normalizedKey = key.toUpperCase(Locale.ROOT);
            if (!keys.add(normalizedKey)) {
                throw invalid("选项 key 不能重复：" + key);
            }
            if (option instanceof ObjectNode optionNode) {
                optionNode.put("key", normalizedKey);
            }
            String content = text(option, "content");
            if (content == null || content.isBlank()) {
                throw invalid("选项 " + key + " 的内容不能为空");
            }
        }

        JsonNode answer = payload.get("answer");
        if (answer == null || !answer.isObject()) {
            throw invalid("选择题必须提供 answer.correctOptionKeys");
        }
        JsonNode correct = answer.get("correctOptionKeys");
        if (correct == null || !correct.isArray() || correct.isEmpty()) {
            throw invalid("correctOptionKeys 不能为空");
        }
        if (single && correct.size() != 1) {
            throw invalid("单选题只能有一个正确选项");
        }
        Set<String> correctKeys = new HashSet<>();
        ArrayNode normalizedCorrect = JsonNodeFactory.instance.arrayNode();
        for (JsonNode keyNode : correct) {
            if (!keyNode.isTextual() || keyNode.asText().isBlank()) {
                throw invalid("correctOptionKeys 必须是字符串数组");
            }
            String key = keyNode.asText().toUpperCase(Locale.ROOT);
            if (!keys.contains(key)) {
                throw invalid("正确选项不存在：" + keyNode.asText());
            }
            if (!correctKeys.add(key)) {
                throw invalid("正确选项不能重复：" + keyNode.asText());
            }
            normalizedCorrect.add(key);
        }
        if (answer instanceof ObjectNode answerNode) {
            answerNode.set("correctOptionKeys", normalizedCorrect);
        }
        payload.put("scoring", "ALL_OR_NOTHING");
    }

    private void validateTrueFalse(ObjectNode payload) {
        JsonNode answer = payload.get("answer");
        if (answer == null || !answer.isObject() || !answer.has("value") || !answer.get("value").isBoolean()) {
            throw invalid("判断题必须提供 answer.value 布尔值");
        }
    }

    private void validateFillBlank(ObjectNode payload) {
        JsonNode blanks = payload.get("blanks");
        if (blanks == null || !blanks.isArray() || blanks.isEmpty() || blanks.size() > 50) {
            throw invalid("填空题必须提供 1-50 个空");
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode blank : blanks) {
            if (!blank.isObject()) {
                throw invalid("填空项必须是对象");
            }
            String id = text(blank, "id");
            if (id == null || id.isBlank() || id.length() > 50) {
                throw invalid("填空 id 不能为空且长度不超过 50");
            }
            if (!ids.add(id)) {
                throw invalid("填空 id 不能重复：" + id);
            }
            JsonNode accepted = blank.get("acceptedAnswers");
            if (accepted == null || !accepted.isArray() || accepted.isEmpty() || accepted.size() > 20) {
                throw invalid("填空 " + id + " 必须提供 1-20 个可接受答案");
            }
            for (JsonNode item : accepted) {
                if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 500) {
                    throw invalid("填空 " + id + " 的答案不能为空且长度不超过 500");
                }
            }
            if (blank.has("caseSensitive") && !blank.get("caseSensitive").isBoolean()) {
                throw invalid("caseSensitive 必须是布尔值");
            }
            if (blank.has("trimWhitespace") && !blank.get("trimWhitespace").isBoolean()) {
                throw invalid("trimWhitespace 必须是布尔值");
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private ContentException invalid(String message) {
        return new ContentException(HttpStatus.BAD_REQUEST, "QUESTION_PAYLOAD_INVALID", message);
    }
}
