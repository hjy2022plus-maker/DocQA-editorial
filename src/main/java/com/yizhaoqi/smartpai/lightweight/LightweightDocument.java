package com.yizhaoqi.smartpai.lightweight;

import java.time.Instant;

public record LightweightDocument(String id, String fileName, String md5, String ownerId,
                                  boolean isPublic, long size, String status,
                                  Instant createdAt, int chunkCount) {}
