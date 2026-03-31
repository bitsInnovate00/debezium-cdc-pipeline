package com.debezium.regression.comparator;

import com.debezium.regression.model.ComparisonResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ComparisonResult} persistence.
 */
@Repository
public interface ComparisonResultRepository extends JpaRepository<ComparisonResult, UUID> {

    Optional<ComparisonResult> findBySessionId(UUID sessionId);

    List<ComparisonResult> findByTestCaseName(String testCaseName);

    List<ComparisonResult> findByVerdict(ComparisonResult.Verdict verdict);
}
