package com.yizhaoqi.smartpai.lightweight;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "docqa_turn", indexes = @Index(columnList = "conversation_id,id"))
public class DocQaTurn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "conversation_id", nullable = false, length = 36)
    String conversationId;
    @Column(length = 2000)
    String question;
    @Lob
    String answer;
    @Lob
    String referencesJson;
    Instant createdAt;

    protected DocQaTurn() {}
}
