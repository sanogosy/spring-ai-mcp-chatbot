package com.mcppostgres.mcppostgresql.repository;

import com.mcppostgres.mcppostgresql.entity.Documents;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Documents, Long> {

    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO documents (content, embedding, metadata) " +
                    "VALUES (:content, CAST(:embedding AS vector), CAST(:metadata AS jsonb))",
            nativeQuery = true
    )
    void saveDocument(
            @Param("content") String content,
            @Param("embedding") String embedding,
            @Param("metadata") String metadata
    );

}
