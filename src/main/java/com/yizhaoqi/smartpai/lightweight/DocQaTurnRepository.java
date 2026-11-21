package com.yizhaoqi.smartpai.lightweight;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface DocQaTurnRepository extends JpaRepository<DocQaTurn, Long> {
    List<DocQaTurn> findByConversationIdOrderById(String conversationId);
    List<DocQaTurn> findTop4ByConversationIdOrderByIdDesc(String conversationId);
}
