package com.yizhaoqi.smartpai.lightweight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

interface StoredChunkRepository extends JpaRepository<StoredChunk, String> {
    List<StoredChunk> findByDocumentIdOrderByPosition(String documentId);
    List<StoredChunk> findByDocumentIdAndPositionBetweenOrderByPosition(String documentId, int start, int end);

    @Transactional
    void deleteByDocumentId(String documentId);
}
