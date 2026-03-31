package com.debezium.regression.capture;

import com.debezium.regression.model.CdcEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for captured {@link CdcEvent} records.
 */
@Repository
public interface CdcEventRepository extends JpaRepository<CdcEvent, Long> {

    List<CdcEvent> findBySessionIdOrderBySequenceNumber(UUID sessionId);

    List<CdcEvent> findBySessionIdAndTableName(UUID sessionId, String tableName);

    @Query("SELECT DISTINCT e.tableName FROM CdcEvent e WHERE e.sessionId = :sessionId")
    List<String> findDistinctTablesBySessionId(@Param("sessionId") UUID sessionId);

    long countBySessionId(UUID sessionId);
}
