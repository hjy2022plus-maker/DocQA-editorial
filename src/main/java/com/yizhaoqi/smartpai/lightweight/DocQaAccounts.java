package com.yizhaoqi.smartpai.lightweight;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
class DocQaAccounts {
    private final DocQaAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final DocQaSecurity.Tokens tokens;
    @Value("${app.registration-open}") private boolean registrationOpen;

    DocQaAccounts(DocQaAccountRepository accounts, PasswordEncoder passwords, DocQaSecurity.Tokens tokens) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @PostMapping("/users/register")
    Map<String, Object> register(@RequestBody Credentials body) {
        if (!registrationOpen) throw LightweightDocumentService.error(HttpStatus.FORBIDDEN, "当前不开放注册");
        return ok(info(create(body.username, body.password, "USER")));
    }

    @PostMapping("/users/login")
    Map<String, Object> login(@RequestBody Credentials body) {
        return accounts.findByUsername(Objects.requireNonNullElse(body.username, ""))
                .filter(account -> body.password != null && passwords.matches(body.password, account.passwordHash))
                .map(account -> ok(tokens.issue(account)))
                .orElseThrow(() -> LightweightDocumentService.error(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
    }

    @PostMapping("/auth/refreshToken")
    Map<String, Object> refresh(@RequestBody Refresh body) {
        return tokens.user(body.refreshToken, "refresh").flatMap(accounts::findById)
                .map(account -> ok(tokens.issue(account)))
                .orElseThrow(() -> LightweightDocumentService.error(HttpStatus.UNAUTHORIZED, "登录已过期"));
    }

    @GetMapping("/users/me")
    Map<String, Object> me(Principal caller) {
        return ok(info(accounts.findById(Long.parseLong(caller.getName())).orElseThrow()));
    }

    @PostMapping("/users/logout")
    Map<String, Object> logout() { return ok(true); }

    @GetMapping("/admin/users")
    Map<String, Object> users() { return ok(accounts.findAll().stream().map(this::info).toList()); }

    @Bean
    ApplicationRunner initializeDocQaAdmin(@Value("${app.admin.enabled}") boolean enabled,
                                           @Value("${app.admin.username}") String username,
                                           @Value("${app.admin.password}") String password) {
        return args -> {
            if (enabled && accounts.findByUsername(username).isEmpty()) create(username, password, "ADMIN");
        };
    }

    private DocQaAccount create(String username, String pass, String role) {
        if (username == null || !username.matches("[\\p{L}\\p{N}_-]{3,32}") || pass == null
                || pass.length() < 8 || pass.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw LightweightDocumentService.error(HttpStatus.BAD_REQUEST, "用户名 3–32 位；密码至少 8 位且不超过 72 字节");
        }
        DocQaAccount account = new DocQaAccount();
        account.username = username;
        account.passwordHash = passwords.encode(pass);
        account.role = role;
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException conflict) {
            throw LightweightDocumentService.error(HttpStatus.CONFLICT, "用户名已存在");
        }
    }

    private Map<String, Object> info(DocQaAccount account) {
        return Map.of("id", account.id, "username", account.username, "role", account.role,
                "orgTags", List.of(), "primaryOrg", "");
    }

    static Map<String, Object> ok(Object data) { return Map.of("code", 200, "message", "ok", "data", data); }

    record Credentials(String username, String password) {}
    record Refresh(String refreshToken) {}
}
