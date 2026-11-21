package com.yizhaoqi.smartpai.lightweight;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "docqa_session", indexes = @Index(columnList = "owner_id,updated_at"))
public class DocQaSession {
    @Id @Column(name = "conversation_id", length = 36)
    String conversationId;
    @Column(name = "owner_id", nullable = false)
    String ownerId;
    String title;
    String status;
    Instant createdAt;
    @Column(name = "updated_at")
    Instant updatedAt;

    protected DocQaSession() {}
}
