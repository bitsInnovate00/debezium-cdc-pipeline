package com.debezium.regression.baseline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link BaselineEvent} persistence.
 */
@Repository
public interface BaselineEventRepository extends JpaRepository<BaselineEvent, Long> {

    List<BaselineEvent> findByBaselineIdOrderBySequenceNumber(UUID baselineId);

    List<BaselineEvent> findByBaselineIdAndTableName(UUID baselineId, String tableName);

    long countByBaselineId(UUID baselineId);
}
