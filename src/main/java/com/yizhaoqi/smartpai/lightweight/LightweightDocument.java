package com.yizhaoqi.smartpai.lightweight;

import java.time.Instant;
import java.util.List;

public record LightweightDocument(String id, String fileName, String md5, String ownerId,
                                  boolean isPublic, long size, String status,
                                  Instant createdAt, List<String> chunks) {
    public LightweightDocument withStatus(String newStatus, List<String> newChunks) {
        return new LightweightDocument(id, fileName, md5, ownerId, isPublic, size, newStatus, createdAt, newChunks);
    }
}
