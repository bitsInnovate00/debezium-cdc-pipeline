package com.debezium.regression.baseline;

import com.debezium.regression.model.Baseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Baseline} persistence.
 */
@Repository
public interface BaselineRepository extends JpaRepository<Baseline, UUID> {

    @Query("SELECT b FROM Baseline b WHERE b.testCaseName = :name AND b.status = 'APPROVED' ORDER BY b.version DESC")
    List<Baseline> findApprovedByTestCaseName(@Param("name") String testCaseName);

    @Query("SELECT MAX(b.version) FROM Baseline b WHERE b.testCaseName = :name")
    Optional<Integer> findLatestVersionByTestCaseName(@Param("name") String testCaseName);

    List<Baseline> findByTestCaseNameOrderByVersionDesc(String testCaseName);

    List<Baseline> findByStatus(Baseline.BaselineStatus status);
}
