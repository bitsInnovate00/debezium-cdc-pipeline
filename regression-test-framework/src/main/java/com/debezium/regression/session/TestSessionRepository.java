package com.debezium.regression.session;

import com.debezium.regression.model.TestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link TestSession} persistence.
 */
@Repository
public interface TestSessionRepository extends JpaRepository<TestSession, UUID> {

    List<TestSession> findByTestCaseName(String testCaseName);

    List<TestSession> findByEnvironment(TestSession.Environment environment);

    List<TestSession> findByStatus(TestSession.SessionStatus status);

    List<TestSession> findByTestCaseNameAndEnvironment(String testCaseName,
                                                       TestSession.Environment environment);
}
