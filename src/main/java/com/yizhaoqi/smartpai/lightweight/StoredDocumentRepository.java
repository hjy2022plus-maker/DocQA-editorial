package com.yizhaoqi.smartpai.lightweight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

interface StoredDocumentRepository extends JpaRepository<StoredDocument, String> {
    Optional<StoredDocument> findByOwnerIdAndMd5(String ownerId, String md5);

    @Query("select d from StoredDocument d where d.ownerId = :owner or d.shared = true order by d.createdAt desc")
    List<StoredDocument> visibleTo(@Param("owner") String owner);

    @Query("select d.id from StoredDocument d where d.status = 'READY' and (d.ownerId = :owner or d.shared = true)")
    List<String> readyIds(@Param("owner") String owner);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update StoredDocument d set d.status = 'FAILED' where d.status = 'PROCESSING'")
    int failInterrupted();
}
