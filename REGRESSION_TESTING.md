# CDC Regression Testing Guide

This guide explains how to use the **CDC Regression Test Framework** to build
and validate a regression test suite for the airline reservation system migration
from monolithic PostgreSQL to microservices.

---

## Overview

The framework uses Debezium CDC events as the source of truth for test assertions.
Instead of writing test data by hand, the system captures real database mutations
during test execution and stores them as a **golden baseline**.  During migration
validation, the same test scenario is replayed against the microservices system
and the new CDC events are compared against the baseline.

```
Monolithic System                    Microservices System
────────────────────                 ────────────────────
1. Start session           ──▶       1. Start session
2. Execute test scenario              2. Execute SAME scenario
3. End session                        3. End session
4. Capture CDC events                 4. Capture CDC events
5. Store as baseline       ──▶       5. Compare vs baseline
6. Approve baseline                   6. PASS or FAIL
```

---

## Quick Start

### 1. Deploy the Framework

```bash
# Deploy alongside the existing CDC pipeline
make deploy-regression

# Or directly:
./scripts/deploy-regression-framework.sh

# Port-forward for local access
kubectl port-forward svc/regression-test-framework 8085:8085 -n debezium-pipeline
```

### 2. Capture a Monolithic Baseline

```bash
# Start a capture session before running your test
SESSION_ID=$(./scripts/start-test-session.sh \
  --name "book_domestic_flight_one_way" \
  --env  MONOLITH \
  --tag  "sprint=1" \
  --tag  "jira=AIRL-101")

# ↑ Note the SESSION_ID printed

# ... now execute your test scenario manually or via JMeter/Postman ...
# e.g.: insert a booking into the monolithic system

# Stop capturing
./scripts/end-test-session.sh --session "$SESSION_ID"

# Wait for COMPLETED status (async capture)
# Poll: curl http://localhost:8085/api/sessions/$SESSION_ID

# Create a baseline
./scripts/create-baseline.sh --session "$SESSION_ID"
# ↑ Note the BASELINE_ID printed

# Review captured events, then approve
./scripts/approve-baseline.sh \
  --baseline "$BASELINE_ID" \
  --by       "qa-lead" \
  --notes    "Verified 5 CDC events for flight booking scenario"
```

### 3. Validate Microservices Run

```bash
# Start a new capture session against microservices
SESSION_ID=$(./scripts/start-test-session.sh \
  --name "book_domestic_flight_one_way" \
  --env  MICROSERVICES \
  --tag  "sprint=1")

# Execute the SAME test scenario against microservices

./scripts/end-test-session.sh --session "$SESSION_ID"

# Compare against approved baseline (exit 0=PASS, 1=FAIL)
./scripts/compare-baselines.sh --session "$SESSION_ID"

# Download reports
./scripts/generate-report.sh --session "$SESSION_ID" --format html   --output report.html
./scripts/generate-report.sh --session "$SESSION_ID" --format junit  --output report.xml
```

---

## Configuration

### Application Configuration (`application.yml`)

```yaml
cdc:
  kafka:
    topics: debezium.public.reservations,debezium.public.flights,...

  capture:
    excluded-tables:
      - public.flyway_schema_history
    primary-key-columns:
      public.reservations: [reservation_id]
    non-deterministic-columns:        # excluded from value comparison
      public.reservations: [created_at, updated_at]

  assertion:
    order-mismatch-permitted-tables:  # ORDER_MISMATCH not treated as failure
      - public.seat_assignments
    permitted-columns:                # VALUE_MISMATCH not treated as failure
      public.payments: [gateway_txn_id]
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | `localhost` | Regression store host |
| `POSTGRES_DB` | `regression_db` | Database name |
| `POSTGRES_USER` | `regression` | DB user |
| `POSTGRES_PASSWORD` | `regression` | DB password |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka broker |
| `SERVER_PORT` | `8085` | HTTP port |

---

## REST API Reference

### Sessions

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions` | Create and start a session |
| GET  | `/api/sessions` | List all sessions |
| GET  | `/api/sessions/{id}` | Get session details |
| POST | `/api/sessions/{id}/end` | End session and trigger capture |
| DELETE | `/api/sessions/{id}` | Delete a session |

**Start a session:**
```bash
curl -X POST http://localhost:8085/api/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "testCaseName": "book_domestic_flight_one_way",
    "environment":  "MONOLITH",
    "tags": { "sprint": "1" }
  }'
```

### Baselines

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET  | `/api/baselines` | List all baselines |
| POST | `/api/baselines/from-session/{sessionId}` | Create baseline from session |
| POST | `/api/baselines/{id}/approve` | Approve a baseline |
| POST | `/api/baselines/{id}/reject` | Reject a baseline |

### Reports

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/compare/{sessionId}` | Compare session vs baseline |
| GET  | `/api/reports/{sessionId}` | JSON comparison result |
| GET  | `/api/reports/{sessionId}/junit` | JUnit-XML report |
| GET  | `/api/reports/{sessionId}/html` | HTML report |
| GET  | `/api/reports/suite/junit` | Suite-level JUnit-XML |

---

## CI/CD Integration (GitHub Actions Example)

```yaml
name: CDC Regression Validation

on:
  push:
    branches: [main, 'feature/**']

jobs:
  regression:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Start regression session
        id: session
        run: |
          SESSION_ID=$(./scripts/start-test-session.sh \
            --name "full_regression_suite" \
            --env  MICROSERVICES \
            --commit ${{ github.sha }})
          echo "session_id=$SESSION_ID" >> $GITHUB_OUTPUT

      - name: Execute test scenarios
        run: ./scripts/run-all-test-scenarios.sh

      - name: End session and compare
        run: |
          ./scripts/end-test-session.sh --session ${{ steps.session.outputs.session_id }}
          sleep 10  # wait for async capture
          ./scripts/compare-baselines.sh --session ${{ steps.session.outputs.session_id }}

      - name: Download JUnit report
        if: always()
        run: |
          ./scripts/generate-report.sh \
            --session ${{ steps.session.outputs.session_id }} \
            --format  junit \
            --output  regression-results.xml

      - name: Publish test results
        if: always()
        uses: mikepenz/action-junit-report@v4
        with:
          report_paths: regression-results.xml
```

---

## Airline Reservation Test Scenarios

### Included Test Cases

| Test Case | Tables Affected | Expected Events |
|-----------|----------------|-----------------|
| `book_domestic_flight_one_way` | flights, reservations, tickets, payments, seat_assignments | 5+ |
| `cancel_booking` | reservations, tickets, payments, seat_assignments | 4+ |
| `check_in_passenger` | boarding_passes, seat_assignments | 2+ |
| `upgrade_seat_class` | tickets, payments, seat_assignments | 3+ |
| `add_passenger_to_booking` | passengers, tickets | 2+ |
| `process_refund` | payments, reservations | 2+ |
| `search_available_flights` | (read-only, no CDC expected) | 0 |

### Adding a New Test Scenario

1. Define the test scenario name (use `snake_case`).
2. Run it on the monolithic system with session capture.
3. Review and approve the baseline.
4. Add non-deterministic column exclusions if needed.
5. Add to CI/CD pipeline.

---

## Diff Categories

| Category | Meaning | Action |
|----------|---------|--------|
| `MISSING_EVENT` | Baseline has event but microservices didn't produce it | **Investigate** — data not written |
| `EXTRA_EVENT`   | Microservices produced event not in baseline | **Investigate** — unexpected side effect |
| `VALUE_MISMATCH` | Same row/op but different column value | **Investigate** or add to `permitted-columns` if non-deterministic |
| `ORDER_MISMATCH` | Event present in both but different sequence | Often acceptable — add to `order-mismatch-permitted-tables` |

---

## Troubleshooting

### Session stays in CAPTURING status

The async CDC capture may have failed. Check:
```bash
kubectl logs deployment/regression-test-framework -n debezium-pipeline | grep ERROR
```

### No events captured

Verify:
1. Kafka topics are correct in `application.yml`
2. Debezium connector is running: `curl http://localhost:8083/connectors`
3. Start and end offsets differ (test scenario actually produced changes)

### False positives from timestamps

Add timestamp columns to `non-deterministic-columns` in `application.yml`:
```yaml
cdc:
  capture:
    non-deterministic-columns:
      public.your_table: [created_at, updated_at, timestamp_col]
```

### Reset framework state

```bash
# Delete all sessions and results (preserves approved baselines)
curl -X DELETE http://localhost:8085/api/sessions
```

---

## Architecture

See [REGRESSION_TEST_PRD.md](REGRESSION_TEST_PRD.md) for the full Product
Requirements Document including the component diagram, data flow, and
implementation phases.
