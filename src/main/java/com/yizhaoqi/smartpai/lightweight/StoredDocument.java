package com.yizhaoqi.smartpai.lightweight;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "docqa_document", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "md5"}))
public class StoredDocument {
    @Id @Column(length = 36)
    String id;
    @Column(name = "owner_id", nullable = false, length = 64)
    String ownerId;
    @Column(nullable = false, length = 32)
    String md5;
    String fileName;
    String extension;
    boolean shared;
    long fileSize;
    String status;
    Instant createdAt;
    int chunkCount;

    protected StoredDocument() {}
}
