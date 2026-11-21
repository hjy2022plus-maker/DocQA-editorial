package com.yizhaoqi.smartpai.lightweight;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface DocQaSessionRepository extends JpaRepository<DocQaSession, String> {
    List<DocQaSession> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);
    Optional<DocQaSession> findByConversationIdAndOwnerId(String id, String ownerId);
}
