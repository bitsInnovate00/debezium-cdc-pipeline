# Architecture Overview

## System Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────┐     ┌─────────────┐     ┌──────────────┐
│ PostgreSQL  │────▶│   Debezium   │────▶│  Kafka  │────▶│   Ignite    │────▶│   Ignite 3   │
│  Database   │     │  Connector   │     │ Broker  │     │  Consumer   │     │    Cache     │
└─────────────┘     └──────────────┘     └─────────┘     └─────────────┘     └──────────────┘
      │                     │                   │                │                    │
      │                     │                   │                │                    │
   Changes              CDC Events          Topics           Process              Store Data
    (WAL)              (JSON Format)                          Events
```

## Component Descriptions

### 1. PostgreSQL Database
- **Purpose**: Source database for CDC
- **Key Features**:
  - Configured with `wal_level=logical` for CDC
  - Uses native `pgoutput` plugin
  - Sample `customers` table for testing
- **Port**: 5432
- **Service**: `postgres-service.debezium-pipeline.svc.cluster.local`

### 2. Apache Kafka
- **Purpose**: Message broker for streaming CDC events
- **Key Features**:
  - Single broker setup (scale for production)
  - Auto-topic creation enabled
  - JSON message format
- **Port**: 9092
- **Service**: `kafka-service.debezium-pipeline.svc.cluster.local`
- **Topics Created**:
  - `dbserver1.public.customers` - CDC events
  - `schema-changes.testdb` - Schema history
  - `debezium_configs` - Connector configs
  - `debezium_offsets` - Offset tracking
  - `debezium_statuses` - Connector statuses

### 3. Apache Zookeeper
- **Purpose**: Kafka cluster coordination
- **Port**: 2181
- **Service**: `zookeeper-service.debezium-pipeline.svc.cluster.local`

### 4. Kafka Connect with Debezium
- **Purpose**: Runs Debezium connectors
- **Key Features**:
  - Distributed mode
  - REST API for connector management
  - JSON converters
  - ExtractNewRecordState transformation
- **Port**: 8083 (REST API)
- **Service**: `kafka-connect-service.debezium-pipeline.svc.cluster.local`

### 5. Debezium Postgres Connector
- **Purpose**: Captures database changes
- **Key Features**:
  - Uses PostgreSQL logical replication
  - Initial snapshot + streaming
  - Table filtering
  - Schema evolution tracking
- **Configuration**: `connectors/postgres-source-connector.json`

### 6. Ignite Kafka Consumer
- **Purpose**: Custom application to consume Kafka events and write to Ignite
- **Key Features**:
  - Kafka consumer using consumer groups
  - Ignite 3 client
  - Handles INSERT, UPDATE, DELETE operations
  - JSON message parsing
- **Language**: Java 11
- **Build**: Maven

### 7. Apache Ignite 3
- **Purpose**: In-memory data grid / cache
- **Key Features**:
  - Distributed cache
  - SQL support
  - REST API
  - 2-node cluster
- **Ports**:
  - 10800 - Thin client
  - 10300 - REST API
  - 3344 - Discovery
- **Service**: `ignite-service.debezium-pipeline.svc.cluster.local`

## Data Flow

### 1. Change Detection
```
PostgreSQL → Write-Ahead Log (WAL) → Logical Replication Slot
```

### 2. CDC Event Creation
```
Debezium Connector → Read from Replication Slot → Create CDC Event
```

### 3. CDC Event Structure (Before Transformation)
```json
{
  "before": { "id": 1, "name": "Old Name", "email": "old@example.com" },
  "after": { "id": 1, "name": "New Name", "email": "old@example.com" },
  "source": {
    "version": "2.4.0.Final",
    "connector": "postgresql",
    "name": "dbserver1",
    "ts_ms": 1697123456789,
    "snapshot": "false",
    "db": "testdb",
    "schema": "public",
    "table": "customers",
    "txId": 123,
    "lsn": 456
  },
  "op": "u",
  "ts_ms": 1697123456790
}
```

### 4. CDC Event Structure (After Unwrap Transformation)
```json
{
  "id": 1,
  "name": "New Name",
  "email": "old@example.com",
  "__deleted": "false"
}
```

### 5. Event Publishing
```
Kafka Connect → Publish to Kafka Topic → dbserver1.public.customers
```

### 6. Event Consumption
```
Ignite Consumer → Subscribe to Topic → Process Messages
```

### 7. Data Writing
```
Ignite Consumer → Parse JSON → Execute SQL → Ignite Cache
```

## Operation Types

### INSERT (op: "c" - create)
```
PostgreSQL: INSERT INTO customers ...
         ↓
Kafka: { "op": "c", "after": {...} }
         ↓
Ignite: MERGE INTO customers ...
```

### UPDATE (op: "u" - update)
```
PostgreSQL: UPDATE customers SET ...
         ↓
Kafka: { "op": "u", "before": {...}, "after": {...} }
         ↓
Ignite: MERGE INTO customers ...
```

### DELETE (op: "d" - delete)
```
PostgreSQL: DELETE FROM customers ...
         ↓
Kafka: { "op": "d", "before": {...} }
         ↓
Ignite: DELETE FROM customers WHERE ...
```

### READ (op: "r" - snapshot)
```
Initial Snapshot:
PostgreSQL: SELECT * FROM customers
         ↓
Kafka: { "op": "r", "after": {...} }
         ↓
Ignite: MERGE INTO customers ...
```

## Network Communication

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Namespace: debezium-pipeline              │  │
│  │                                                         │  │
│  │  ┌──────────┐  5432   ┌──────────────┐               │  │
│  │  │PostgreSQL│◀────────│Kafka Connect │               │  │
│  │  └──────────┘         └──────┬───────┘               │  │
│  │                              │ 9092                    │  │
│  │                         ┌────▼────┐                   │  │
│  │                         │  Kafka  │                   │  │
│  │                         └────┬────┘                   │  │
│  │                              │ 9092                    │  │
│  │                    ┌─────────▼─────────┐             │  │
│  │                    │ Ignite Consumer   │             │  │
│  │                    └─────────┬─────────┘             │  │
│  │                              │ 10800                  │  │
│  │                         ┌────▼────┐                  │  │
│  │                         │ Ignite  │                  │  │
│  │                         └─────────┘                  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## High Availability Considerations

### Current Setup (Development)
- Single instance of each component
- No data persistence (emptyDir volumes)
- No replication

### Production Setup (Recommended)

1. **PostgreSQL**
   - Primary-Standby replication
   - Persistent volumes
   - Regular backups

2. **Kafka**
   - 3+ broker cluster
   - Replication factor: 3
   - Min in-sync replicas: 2
   - StatefulSets with persistent volumes

3. **Kafka Connect**
   - Multiple worker nodes
   - Distributed mode (already configured)
   - Task rebalancing

4. **Ignite**
   - 3+ node cluster
   - Partition backup count: 2
   - StatefulSets with persistent volumes

5. **Ignite Consumer**
   - Multiple replicas
   - Consumer group for load balancing
   - Proper error handling and retries

## Performance Characteristics

### Throughput
- **CDC Capture**: Depends on PostgreSQL transaction rate
- **Kafka**: Typically 100K+ messages/sec per broker
- **Ignite**: 100K+ operations/sec

### Latency
- **End-to-End**: Typically 50-500ms
  - PostgreSQL to Debezium: 10-50ms
  - Debezium to Kafka: 10-50ms
  - Kafka to Consumer: 10-100ms
  - Consumer to Ignite: 10-100ms

### Scalability
- **Horizontal**: Add more Kafka brokers, Connect workers, Ignite nodes
- **Vertical**: Increase resources for each component

## Monitoring Points

1. **PostgreSQL**
   - Replication lag
   - WAL size
   - Connection count

2. **Kafka**
   - Topic lag
   - Throughput
   - Disk usage

3. **Kafka Connect**
   - Connector status
   - Task status
   - Offset lag

4. **Ignite Consumer**
   - Consumer lag
   - Processing rate
   - Error rate

5. **Ignite**
   - Cache size
   - Query performance
   - Cluster topology

## Failure Scenarios and Recovery

### PostgreSQL Failure
- **Impact**: CDC stops
- **Recovery**: Restart PostgreSQL, connector resumes from offset

### Kafka Failure
- **Impact**: Events buffered in replication slot
- **Recovery**: Restart Kafka, events are processed

### Kafka Connect Failure
- **Impact**: CDC stops
- **Recovery**: Restart Connect, resumes from last offset

### Ignite Consumer Failure
- **Impact**: Events accumulate in Kafka
- **Recovery**: Restart consumer, processes from last committed offset

### Ignite Failure
- **Impact**: Consumer cannot write data
- **Recovery**: Restart Ignite, consumer retries
