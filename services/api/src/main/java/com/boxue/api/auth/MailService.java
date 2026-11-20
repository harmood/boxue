package com.boxue.api.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;
    private final boolean logDevLinks;

    public MailService(
            JavaMailSender mailSender,
            @Value("${boxue.mail.from:no-reply@boxue.local}") String from,
            @Value("${boxue.web.base-url:http://localhost:5173}") String baseUrl,
            @Value("${boxue.auth.log-dev-links:false}") boolean logDevLinks) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl;
        this.logDevLinks = logDevLinks;
    }

    public void sendVerificationEmail(String email, String token) {
        String link = baseUrl + "/verify-email?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        send(email, "博学 - 验证邮箱",
            "请打开以下链接完成邮箱验证：\n\n" + link + "\n\n如果不是你本人操作，请忽略此邮件。");
    }

    public void sendPasswordResetEmail(String email, String token) {
        String link = baseUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        send(email, "博学 - 重置密码",
            "请打开以下链接重置密码：\n\n" + link + "\n\n如果不是你本人操作，请忽略此邮件。");
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        if (logDevLinks) {
            log.info("[DEV MAIL] to={}, subject={}, body={}", to, subject, text);
        }
        mailSender.send(message);
    }
}
