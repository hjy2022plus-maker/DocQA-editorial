package com.yizhaoqi.smartpai.lightweight;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/users")
class DocQaConversationController {
    private final DocQaConversations conversations;
    DocQaConversationController(DocQaConversations conversations) { this.conversations = conversations; }

    @GetMapping("/conversations")
    Map<String, Object> list(Principal principal) { return DocQaAccounts.ok(conversations.list(principal.getName())); }

    @PostMapping("/conversations")
    Map<String, Object> create(Principal principal) { return DocQaAccounts.ok(conversations.create(principal.getName())); }

    @PutMapping("/conversations/{id}/switch")
    Map<String, Object> change(@PathVariable String id, Principal principal) {
        conversations.owned(id, principal.getName());
        return DocQaAccounts.ok(true);
    }

    @PutMapping("/conversations/{id}/archive")
    Map<String, Object> archive(@PathVariable String id, Principal principal) {
        return DocQaAccounts.ok(conversations.status(id, principal.getName(), "ARCHIVED"));
    }

    @PutMapping("/conversations/{id}/unarchive")
    Map<String, Object> unarchive(@PathVariable String id, Principal principal) {
        return DocQaAccounts.ok(conversations.status(id, principal.getName(), "ACTIVE"));
    }

    @GetMapping("/conversation")
    Map<String, Object> history(@RequestParam String conversationId, Principal principal) throws JsonProcessingException {
        return DocQaAccounts.ok(conversations.history(conversationId, principal.getName()));
    }
}

@Service
class DocQaConversations {
    private final DocQaSessionRepository sessions;
    private final DocQaTurnRepository turns;
    private final ObjectMapper json;
    DocQaConversations(DocQaSessionRepository sessions, DocQaTurnRepository turns, ObjectMapper json) {
        this.sessions = sessions;
        this.turns = turns;
        this.json = json;
    }

    DocQaSession owned(String id, String owner) {
        return sessions.findByConversationIdAndOwnerId(id, owner)
                .orElseThrow(() -> LightweightDocumentService.error(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    List<Session> list(String owner) { return sessions.findByOwnerIdOrderByUpdatedAtDesc(owner).stream().map(this::view).toList(); }

    Session create(String owner) {
        var session = new DocQaSession();
        session.conversationId = UUID.randomUUID().toString();
        session.ownerId = owner;
        session.title = "新对话";
        session.status = "ACTIVE";
        session.createdAt = session.updatedAt = Instant.now();
        return view(sessions.save(session));
    }

    Session status(String id, String owner, String status) {
        var session = owned(id, owner);
        session.status = status;
        return view(sessions.save(session));
    }

    List<Map<String, String>> recent(DocQaSession session) {
        var rows = new ArrayList<>(turns.findTop4ByConversationIdOrderByIdDesc(session.conversationId));
        Collections.reverse(rows);
        List<Map<String, String>> messages = new ArrayList<>();
        for (var turn : rows) {
            messages.add(Map.of("role", "user", "content", turn.question));
            messages.add(Map.of("role", "assistant", "content", turn.answer));
        }
        return messages;
    }

    List<Map<String, Object>> history(String id, String owner) throws JsonProcessingException {
        owned(id, owner);
        var messages = new ArrayList<Map<String, Object>>();
        for (var turn : turns.findByConversationIdOrderById(id)) {
            messages.add(Map.of("role", "user", "content", turn.question, "timestamp", turn.createdAt, "conversationId", id));
            messages.add(Map.of("role", "assistant", "content", turn.answer, "timestamp", turn.createdAt,
                    "conversationId", id, "status", "finished", "referenceMappings", json.readTree(turn.referencesJson)));
        }
        return messages;
    }

    @Transactional
    public void save(String id, String owner, String question, String answer, String references) {
        var session = owned(id, owner);
        var turn = new DocQaTurn();
        turn.conversationId = id;
        turn.question = question;
        turn.answer = answer;
        turn.referencesJson = references;
        turn.createdAt = Instant.now();
        turns.save(turn);
        if ("新对话".equals(session.title)) {
            int end = question.offsetByCodePoints(0, Math.min(question.codePointCount(0, question.length()), 32));
            session.title = question.substring(0, end);
        }
        session.updatedAt = turn.createdAt;
    }

    private Session view(DocQaSession s) { return new Session(s.conversationId, s.title, s.status, s.createdAt, s.updatedAt); }
    record Session(String conversationId, String title, String status, Instant createdAt, Instant updatedAt) {}
}
