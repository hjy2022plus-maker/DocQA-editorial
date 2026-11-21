package com.yizhaoqi.smartpai.lightweight;

import jakarta.persistence.*;

@Entity
@Table(name = "docqa_chunk", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "position"}))
public class StoredChunk {
    @Id @Column(length = 50)
    String id;
    @Column(name = "document_id", nullable = false, length = 36)
    String documentId;
    @Column(name = "position", nullable = false)
    int position;
    Integer pageNumber;
    @Column(nullable = false, length = 1000)
    String content;

    protected StoredChunk() {}

    StoredChunk(String documentId, int position, Integer pageNumber, String content) {
        this.id = documentId + ":" + position;
        this.documentId = documentId;
        this.position = position;
        this.pageNumber = pageNumber;
        this.content = content;
    }
}
