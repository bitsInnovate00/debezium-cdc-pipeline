# Product Requirements Document (PRD)
# CDC-Based Regression Test Suite for Airline Reservation System

**Version:** 1.0  
**Date:** 2026-03-31  
**Status:** Approved  
**Owner:** Platform Engineering / QA Innovation Team

---

## 1. Executive Summary

Legacy enterprise airline reservation systems present a significant modernisation risk: they have no automated regression test suites. Manual testing is slow, error-prone, and cannot scale with the velocity demanded by a microservices migration.

This PRD describes a **CDC-Based Regression Test Suite (CRTS)** that harnesses the existing Debezium Change Data Capture pipeline to:

1. Capture every database mutation that occurs during a test-case execution window on the **monolithic system** and record it as a **golden baseline**.
2. Replay the same user actions against the **migrated microservices system** and capture its CDC events.
3. Automatically **compare** the two CDC event streams and **assert** correctness.
4. Produce **human-readable reports** that identify regressions with precise table/column-level diffs.

The result is a self-building regression suite that requires **zero upfront test data authoring**: the production system writes the test data itself.

---

## 2. Problem Statement

| Pain Point | Impact |
|------------|--------|
| No automated regression test suite | Cannot validate microservices migration safely |
| 100 % manual testing | 3–6 week regression cycles per release |
| No authoritative test-data baseline | Inconsistent test results across environments |
| Legacy EJB system complexity | Black-box behaviour difficult to assert |
| Modernisation risk | Microservices may silently diverge from monolith behaviour |

---

## 3. Goals and Non-Goals

### 3.1 Goals

- **G1** — Build a regression test suite with zero hand-authored test data.
- **G2** — Capture CDC events scoped to individual test-case execution windows.
- **G3** — Store a durable golden baseline per test case.
- **G4** — Compare baseline vs new-run CDC streams and report row-level diffs.
- **G5** — Integrate with existing CI/CD pipelines via a REST API and CLI.
- **G6** — Support both monolithic (PostgreSQL) and microservices (individual service DBs) topologies.
- **G7** — Produce JUnit-XML-compatible reports consumable by any CI system.

### 3.2 Non-Goals

- Replacing functional/exploratory testing.
- Generating synthetic test data (all data comes from real test executions).
- Testing UI or presentation layer behaviour (data-layer assertions only).
- Supporting non-PostgreSQL databases in v1.

---

## 4. Stakeholders

| Role | Name / Team | Responsibility |
|------|-------------|---------------|
| Product Owner | Platform Engineering | Prioritisation and acceptance |
| Lead Architect | Architecture Guild | Technical design review |
| QA Lead | QA Team | Test scenario definition |
| Dev Lead | Microservices Team | Integration with CI/CD |
| DBA | Database Team | WAL/replication configuration |

---

## 5. Functional Requirements

### 5.1 Test Session Manager

| ID | Requirement |
|----|-------------|
| FR-01 | The system SHALL allow a test orchestrator to start a named test session via a REST API call. |
| FR-02 | On session start the system SHALL record the current Kafka topic offsets for all monitored topics as the session boundary. |
| FR-03 | The system SHALL allow the orchestrator to end a test session, triggering CDC event collection for the session window. |
| FR-04 | Each session SHALL be assigned a globally unique Session ID (UUID) and associated with a human-readable test-case name. |
| FR-05 | Sessions SHALL support a tagging mechanism (e.g., `sprint=42`, `env=monolith`) for filtering. |

### 5.2 CDC Event Capture

| ID | Requirement |
|----|-------------|
| FR-06 | The system SHALL consume Debezium CDC events from all configured Kafka topics. |
| FR-07 | Events SHALL be filtered by session boundary offsets so that only changes within the test window are captured. |
| FR-08 | Each captured event SHALL record: topic, partition, offset, timestamp, operation (INSERT/UPDATE/DELETE), table name, before-image, after-image. |
| FR-09 | The system SHALL handle schema changes gracefully and store the schema version with each event. |
| FR-10 | Events from excluded tables (configurable) SHALL be ignored. |

### 5.3 Baseline Store

| ID | Requirement |
|----|-------------|
| FR-11 | The system SHALL persist captured event streams to a durable baseline store (PostgreSQL or file system). |
| FR-12 | A baseline SHALL be immutable once committed; subsequent captures create new baseline versions. |
| FR-13 | The system SHALL support baseline versioning, linking each version to a code commit SHA. |
| FR-14 | Baselines SHALL be exportable as JSON archives for portability between environments. |
| FR-15 | The system SHALL support selective baseline approval workflow (approve, reject, pending). |

### 5.4 Data Comparator

| ID | Requirement |
|----|-------------|
| FR-16 | The system SHALL compare a new run's CDC event stream against the approved baseline for the same test case. |
| FR-17 | Comparison SHALL operate at the row level: same primary key, same operation, same column values. |
| FR-18 | The comparator SHALL classify each difference as: MISSING_EVENT, EXTRA_EVENT, VALUE_MISMATCH, ORDER_MISMATCH. |
| FR-19 | Columns MAY be configured as non-deterministic (e.g., timestamps, auto-generated IDs) and SHALL be excluded from value comparison. |
| FR-20 | The comparator SHALL produce a structured diff result in JSON format. |

### 5.5 Assertion Engine

| ID | Requirement |
|----|-------------|
| FR-21 | The system SHALL evaluate diff results against configurable assertion rules. |
| FR-22 | Default assertion: zero differences → PASS; any difference → FAIL. |
| FR-23 | Rules SHALL support partial matching for tables where order is non-deterministic. |
| FR-24 | Rules SHALL support tolerance thresholds for numerical columns (e.g., allow ±0.01 in fare amounts). |
| FR-25 | Assertion results SHALL be exposed via REST API for CI/CD integration. |

### 5.6 Report Generator

| ID | Requirement |
|----|-------------|
| FR-26 | The system SHALL generate a JUnit-XML report compatible with Jenkins, GitHub Actions, and GitLab CI. |
| FR-27 | The system SHALL generate an HTML report with a visual diff view per test case. |
| FR-28 | The HTML report SHALL highlight PASS/FAIL status, diff counts, and affected tables. |
| FR-29 | Reports SHALL be exportable to S3-compatible storage. |
| FR-30 | Summary statistics SHALL include: total tests, passed, failed, skipped, execution time. |

---

## 6. Non-Functional Requirements

| ID | Category | Requirement |
|----|----------|-------------|
| NFR-01 | Performance | Capture latency SHALL be < 500 ms for a batch of 1,000 CDC events. |
| NFR-02 | Scalability | SHALL support 50 concurrent test sessions without data interleaving. |
| NFR-03 | Reliability | SHALL tolerate Kafka consumer restarts without losing session data. |
| NFR-04 | Availability | Baseline store SHALL use PostgreSQL with daily backups. |
| NFR-05 | Security | Baseline data SHALL not contain PII unless explicitly configured; masking rules SHALL apply. |
| NFR-06 | Auditability | All session lifecycle events SHALL be logged with timestamp and user identity. |
| NFR-07 | Compatibility | Framework SHALL run on the same Kubernetes cluster as the existing CDC pipeline. |
| NFR-08 | Portability | Baselines SHALL be importable/exportable between clusters. |

---

## 7. Architecture

### 7.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Airline Reservation System                       │
│                                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐               │
│  │  Test Suite  │    │  Monolithic  │    │ Microservices│               │
│  │Orchestrator  │───▶│  PostgreSQL  │    │  PostgreSQL  │               │
│  │  (JMeter /   │    │     DB       │    │  Databases   │               │
│  │   Postman /  │    └──────┬───────┘    └──────┬───────┘               │
│  │   Custom)    │           │                   │                        │
│  └──────┬───────┘    Debezium CDC          Debezium CDC                 │
│         │            ┌──────▼───────┐    ┌──────▼───────┐               │
│         │            │  Kafka Topic │    │  Kafka Topic │               │
│         │            │  (monolith)  │    │ (microsvcs)  │               │
│         │            └──────┬───────┘    └──────┬───────┘               │
│         │                   │                   │                        │
│         │         ┌─────────▼───────────────────▼──────┐                │
│         │         │       CDC Regression Test            │               │
│         ├────────▶│           Framework                  │               │
│  Start/End        │                                      │               │
│  Session          │  ┌──────────────────────────────┐   │               │
│                   │  │     Test Session Manager     │   │               │
│                   │  ├──────────────────────────────┤   │               │
│                   │  │      CDC Event Capture       │   │               │
│                   │  ├──────────────────────────────┤   │               │
│                   │  │       Baseline Store         │   │               │
│                   │  ├──────────────────────────────┤   │               │
│                   │  │       Data Comparator        │   │               │
│                   │  ├──────────────────────────────┤   │               │
│                   │  │      Assertion Engine        │   │               │
│                   │  ├──────────────────────────────┤   │               │
│                   │  │      Report Generator        │   │               │
│                   │  └──────────────────────────────┘   │               │
│                   └─────────────────┬────────────────────┘               │
│                                     │                                    │
│                   ┌─────────────────▼────────────────────┐               │
│                   │           PostgreSQL                   │               │
│                   │        (Baseline Store DB)             │               │
│                   └──────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Data Flow — Baseline Capture Phase

```
1. Test Orchestrator calls POST /api/sessions  → Session created, offsets recorded
2. Test Orchestrator executes test scenario    → DB mutations occur
3. Debezium captures changes                  → CDC events published to Kafka
4. Test Orchestrator calls POST /api/sessions/{id}/end
5. CDC Event Capture reads events in offset window
6. Events serialised and saved to Baseline Store
7. QA team reviews and approves baseline
```

### 7.3 Data Flow — Regression Validation Phase

```
1. Test Orchestrator calls POST /api/sessions (tagged env=microservices)
2. Test Orchestrator re-executes same scenario against microservices
3. New CDC events captured in same manner
4. Data Comparator loads approved baseline + new run events
5. Assertion Engine evaluates diff
6. Report Generator produces JUnit-XML + HTML report
7. CI/CD pipeline marks build PASS or FAIL
```

---

## 8. Airline Reservation Domain — Key Tables & CDC Scope

The following PostgreSQL tables in the monolithic schema are in scope for CDC capture:

| Table | Operation | CDC Significance |
|-------|-----------|-----------------|
| `reservations` | INSERT, UPDATE, DELETE | Core booking lifecycle |
| `passengers` | INSERT, UPDATE | Passenger profile changes |
| `flights` | UPDATE | Seat availability changes |
| `tickets` | INSERT, UPDATE | Ticket issuance / modification |
| `payments` | INSERT, UPDATE | Payment processing |
| `boarding_passes` | INSERT | Check-in events |
| `seat_assignments` | INSERT, UPDATE, DELETE | Seat management |
| `fare_classes` | UPDATE | Pricing changes |

Non-deterministic columns to exclude from comparison: `created_at`, `updated_at`, `id` (sequence), `session_token`.

---

## 9. REST API Specification

### 9.1 Session Management

```
POST   /api/sessions                    Create and start a test session
GET    /api/sessions/{id}               Get session details and status
POST   /api/sessions/{id}/end           End a session and trigger capture
DELETE /api/sessions/{id}               Delete a session and its captured data
GET    /api/sessions                    List all sessions (filterable by tags)
```

### 9.2 Baseline Management

```
GET    /api/baselines                   List all baselines
GET    /api/baselines/{testCaseName}    Get latest approved baseline for test case
POST   /api/baselines/{id}/approve      Approve a baseline
POST   /api/baselines/{id}/reject       Reject a baseline
GET    /api/baselines/{id}/export       Export baseline as JSON archive
POST   /api/baselines/import            Import baseline from JSON archive
```

### 9.3 Comparison and Reporting

```
POST   /api/compare                     Compare session capture vs baseline
GET    /api/reports/{sessionId}         Get comparison report (JSON)
GET    /api/reports/{sessionId}/junit   Get JUnit-XML report
GET    /api/reports/{sessionId}/html    Get HTML report
```

---

## 10. Sample Test Scenario — Flight Booking

### Scenario: Book a one-way domestic flight

**Preconditions:** Flight AA101 has 10 available seats.

**Test Steps:**
1. Search for flight AA101 on 2026-04-15
2. Select economy seat 14B
3. Enter passenger details (John Doe, john@example.com)
4. Process payment ($350.00 via credit card ending 4242)
5. Confirm booking, receive PNR `XYZ123`

**Expected CDC Events (Baseline):**

```json
[
  { "op": "u", "table": "flights", "after": { "flight_id": "AA101", "available_seats": 9 } },
  { "op": "i", "table": "reservations", "after": { "pnr": "XYZ123", "status": "CONFIRMED" } },
  { "op": "i", "table": "tickets", "after": { "flight_id": "AA101", "seat": "14B", "class": "Y" } },
  { "op": "i", "table": "payments", "after": { "amount": 350.00, "status": "CAPTURED" } },
  { "op": "i", "table": "seat_assignments", "after": { "seat": "14B", "status": "OCCUPIED" } }
]
```

**Assertion:** Microservices run must produce an identical event stream (excluding non-deterministic columns). Any difference → FAIL.

---

## 11. Implementation Phases

### Phase 1 — Foundation (Weeks 1–4)

- [ ] Deploy regression test framework on existing Kubernetes cluster
- [ ] Implement Test Session Manager with Kafka offset management
- [ ] Implement CDC Event Capture and Baseline Store
- [ ] Create REST API skeleton
- [ ] Capture first 10 monolithic test scenarios manually

### Phase 2 — Comparison & Reporting (Weeks 5–8)

- [ ] Implement Data Comparator with column-level diff
- [ ] Implement Assertion Engine with configurable rules
- [ ] Generate JUnit-XML reports
- [ ] Integrate with CI/CD pipeline (GitHub Actions)
- [ ] Define non-deterministic column exclusion rules

### Phase 3 — Scale & Automation (Weeks 9–12)

- [ ] Capture full regression suite of 50–100 test scenarios
- [ ] Implement HTML report generator with visual diff
- [ ] Implement baseline approval workflow
- [ ] Performance test: 50 concurrent sessions
- [ ] Security: PII masking for passenger data

### Phase 4 — Microservices Validation (Weeks 13–20)

- [ ] Configure Debezium connectors for each microservice database
- [ ] Run full regression suite against microservices
- [ ] Triage and resolve failures
- [ ] Document equivalence proof for each migrated service
- [ ] Hand over to QA team for ongoing maintenance

---

## 12. Success Metrics

| Metric | Target |
|--------|--------|
| Test scenarios captured | ≥ 100 |
| Baseline capture time per scenario | < 30 s |
| False positive rate | < 2 % |
| CI/CD integration | 100 % automated |
| Regression cycle time reduction | 80 % (from 4 weeks to < 1 week) |
| Manual testing effort reduction | > 70 % |
| Microservices migration defects caught | > 90 % pre-production |

---

## 13. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| WAL replication overhead impacts production | Medium | High | Use separate replica for CDC; test in staging first |
| Non-deterministic data causes false failures | High | Medium | Comprehensive exclusion list; fuzzy matching for numeric tolerances |
| Kafka consumer lag during high-load tests | Medium | Medium | Consumer group isolation per session |
| PII in baseline store | High | High | Automatic PII detection and masking before persistence |
| Microservices emit different event order | Medium | High | Order-independent comparison mode for non-transactional tables |
| Large test scenarios with thousands of events | Low | Medium | Baseline pagination and streaming diff |

---

## 14. Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Debezium PostgreSQL Connector | 2.4+ | CDC source |
| Apache Kafka | 3.5+ | Event streaming |
| Apache Kafka Streams | 3.5+ | Stateful processing |
| PostgreSQL | 15+ | Baseline store |
| Spring Boot | 3.2+ | REST API framework |
| Flyway | 9+ | Baseline store schema migrations |
| JUnit 5 | 5.10+ | Report format |
| Thymeleaf | 3.1+ | HTML report templates |
| Testcontainers | 1.19+ | Integration testing of the framework itself |

---

## 15. Glossary

| Term | Definition |
|------|-----------|
| Baseline | An approved snapshot of CDC events captured during a test scenario on the reference system |
| CDC | Change Data Capture — recording all data changes at the database level via PostgreSQL WAL |
| Golden Baseline | The authoritative, approved baseline against which future runs are compared |
| Non-deterministic Column | A column whose value is expected to differ between runs (e.g., timestamps, auto-IDs) |
| Session | A bounded time window during which CDC events are captured for a specific test case |
| Test Scenario | A named, repeatable sequence of user actions with defined preconditions and expected outcomes |
| WAL | Write-Ahead Log — the PostgreSQL mechanism used by Debezium to capture changes |

---

*End of PRD*
