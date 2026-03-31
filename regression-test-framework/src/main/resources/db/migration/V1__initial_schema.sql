-- V1__initial_schema.sql
-- CDC Regression Test Framework - Initial Database Schema

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- Test Sessions
-- ============================================================
CREATE TABLE test_sessions (
    id              UUID PRIMARY KEY,
    test_case_name  VARCHAR(255) NOT NULL,
    environment     VARCHAR(50)  NOT NULL CHECK (environment IN ('MONOLITH','MICROSERVICES')),
    status          VARCHAR(50)  NOT NULL CHECK (status IN ('STARTED','CAPTURING','COMPLETED','FAILED')),
    started_at      TIMESTAMPTZ  NOT NULL,
    ended_at        TIMESTAMPTZ,
    start_offsets   TEXT,
    end_offsets     TEXT,
    commit_sha      VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_test_case ON test_sessions (test_case_name);
CREATE INDEX idx_session_status    ON test_sessions (status);
CREATE INDEX idx_session_env       ON test_sessions (environment);

CREATE TABLE session_tags (
    session_id UUID         NOT NULL REFERENCES test_sessions(id) ON DELETE CASCADE,
    tag_key    VARCHAR(100) NOT NULL,
    tag_value  VARCHAR(255) NOT NULL,
    PRIMARY KEY (session_id, tag_key)
);

-- ============================================================
-- CDC Events (live session captures)
-- ============================================================
CREATE TABLE cdc_events (
    id               BIGSERIAL   PRIMARY KEY,
    session_id       UUID        NOT NULL REFERENCES test_sessions(id) ON DELETE CASCADE,
    kafka_topic      VARCHAR(255) NOT NULL,
    kafka_partition  INTEGER     NOT NULL,
    kafka_offset     BIGINT      NOT NULL,
    event_timestamp  TIMESTAMPTZ,
    operation        CHAR(1)     NOT NULL CHECK (operation IN ('c','u','d','r')),
    table_name       VARCHAR(255) NOT NULL,
    primary_key      TEXT,
    before_image     TEXT,
    after_image      TEXT,
    sequence_number  BIGINT      NOT NULL,
    schema_version   VARCHAR(100)
);

CREATE INDEX idx_cdc_session_id  ON cdc_events (session_id);
CREATE INDEX idx_cdc_table_name  ON cdc_events (table_name);
CREATE INDEX idx_cdc_operation   ON cdc_events (operation);

-- ============================================================
-- Baselines
-- ============================================================
CREATE TABLE baselines (
    id               UUID PRIMARY KEY,
    test_case_name   VARCHAR(255) NOT NULL,
    version          INTEGER      NOT NULL,
    source_session_id UUID        REFERENCES test_sessions(id) ON DELETE SET NULL,
    status           VARCHAR(50)  NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED')),
    created_at       TIMESTAMPTZ  NOT NULL,
    approved_at      TIMESTAMPTZ,
    approved_by      VARCHAR(255),
    commit_sha       VARCHAR(40),
    event_count      INTEGER      NOT NULL DEFAULT 0,
    summary          TEXT,
    notes            TEXT,
    UNIQUE (test_case_name, version)
);

CREATE INDEX idx_baseline_test_case ON baselines (test_case_name);
CREATE INDEX idx_baseline_status    ON baselines (status);

-- ============================================================
-- Baseline Events (immutable copies of CDC events)
-- ============================================================
CREATE TABLE baseline_events (
    id               BIGSERIAL    PRIMARY KEY,
    baseline_id      UUID         NOT NULL REFERENCES baselines(id) ON DELETE CASCADE,
    kafka_topic      VARCHAR(255) NOT NULL,
    kafka_partition  INTEGER      NOT NULL,
    kafka_offset     BIGINT       NOT NULL,
    event_timestamp  TIMESTAMPTZ,
    operation        CHAR(1)      NOT NULL CHECK (operation IN ('c','u','d','r')),
    table_name       VARCHAR(255) NOT NULL,
    primary_key      TEXT,
    before_image     TEXT,
    after_image      TEXT,
    sequence_number  BIGINT       NOT NULL,
    schema_version   VARCHAR(100)
);

CREATE INDEX idx_be_baseline_id  ON baseline_events (baseline_id);
CREATE INDEX idx_be_table_name   ON baseline_events (table_name);
CREATE INDEX idx_be_primary_key  ON baseline_events (primary_key);

-- ============================================================
-- Comparison Results
-- ============================================================
CREATE TABLE comparison_results (
    id                    UUID PRIMARY KEY,
    session_id            UUID         NOT NULL REFERENCES test_sessions(id) ON DELETE CASCADE,
    baseline_id           UUID         NOT NULL REFERENCES baselines(id) ON DELETE CASCADE,
    test_case_name        VARCHAR(255) NOT NULL,
    verdict               VARCHAR(10)  NOT NULL CHECK (verdict IN ('PASS','FAIL','ERROR')),
    compared_at           TIMESTAMPTZ  NOT NULL,
    baseline_event_count  INTEGER,
    session_event_count   INTEGER,
    missing_event_count   INTEGER,
    extra_event_count     INTEGER,
    value_mismatch_count  INTEGER,
    order_mismatch_count  INTEGER,
    diff_detail           TEXT,
    summary               TEXT
);

CREATE INDEX idx_comp_session_id  ON comparison_results (session_id);
CREATE INDEX idx_comp_baseline_id ON comparison_results (baseline_id);
CREATE INDEX idx_comp_verdict     ON comparison_results (verdict);
