# Configuration Guide

## Overview

This document explains the configuration options for each component in the Debezium CDC pipeline.

## PostgreSQL Configuration

### Connection Settings
Located in `kubernetes/postgres/postgres-configmap.yaml`:

```yaml
POSTGRES_DB: testdb          # Database name
POSTGRES_USER: postgres      # Database user
POSTGRES_PASSWORD: postgres  # Database password (change in production!)
```

### WAL Configuration (Required for Debezium)
```yaml
wal_level: logical           # Must be 'logical' for CDC
max_wal_senders: 4          # Number of WAL sender processes
max_replication_slots: 4    # Number of replication slots
```

### Production Recommendations
- Use Kubernetes Secrets for sensitive data
- Enable SSL/TLS connections
- Use persistent volumes for data
- Configure proper backup strategy
- Set appropriate resource limits

## Kafka Configuration

### Basic Settings
Located in `kubernetes/kafka/kafka-deployment.yaml`:

```yaml
KAFKA_BROKER_ID: "1"                        # Broker ID
KAFKA_ZOOKEEPER_CONNECT: "zookeeper-service:2181"
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1" # Set to 3 for production
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"     # Allow auto topic creation
```

### Listeners
```yaml
KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://kafka-service:9092,PLAINTEXT_INTERNAL://localhost:9093"
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT"
```

### Production Recommendations
- Deploy at least 3 Kafka brokers
- Use StatefulSets instead of Deployments
- Enable authentication (SASL/SSL)
- Configure proper retention policies
- Use persistent volumes
- Set replication factor to 3

## Kafka Connect with Debezium

### Basic Settings
Located in `kubernetes/kafka-connect/kafka-connect-deployment.yaml`:

```yaml
BOOTSTRAP_SERVERS: "kafka-service:9092"
GROUP_ID: "debezium-cluster"
```

### Storage Topics
```yaml
CONFIG_STORAGE_TOPIC: "debezium_configs"
OFFSET_STORAGE_TOPIC: "debezium_offsets"
STATUS_STORAGE_TOPIC: "debezium_statuses"
```

### Converters
```yaml
CONNECT_KEY_CONVERTER: "org.apache.kafka.connect.json.JsonConverter"
CONNECT_VALUE_CONVERTER: "org.apache.kafka.connect.json.JsonConverter"
CONNECT_KEY_CONVERTER_SCHEMAS_ENABLE: "true"
CONNECT_VALUE_CONVERTER_SCHEMAS_ENABLE: "true"
```

### Production Recommendations
- Deploy multiple Kafka Connect workers
- Use distributed mode (already configured)
- Enable authentication
- Configure proper logging
- Monitor connector health

## Debezium Postgres Connector

### Core Configuration
Located in `connectors/postgres-source-connector.json`:

```json
{
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "tasks.max": "1",
  "database.hostname": "postgres-service",
  "database.port": "5432",
  "database.user": "postgres",
  "database.password": "postgres",
  "database.dbname": "testdb",
  "database.server.name": "dbserver1"
}
```

### Table Selection
```json
{
  "table.include.list": "public.customers",  // Specific tables
  // OR
  "schema.include.list": "public",           // All tables in schema
  // OR
  "table.exclude.list": "public.audit_log"   // Exclude specific tables
}
```

### Snapshot Configuration
```json
{
  "snapshot.mode": "initial",  // Options: initial, always, never, exported, custom
  "snapshot.fetch.size": "10240"
}
```

**Snapshot Modes:**
- `initial`: Perform initial snapshot on first run
- `always`: Always perform snapshot
- `never`: Never perform snapshot (streaming only)
- `exported`: Use PostgreSQL exported snapshot
- `custom`: Custom snapshot implementation

### CDC Behavior
```json
{
  "plugin.name": "pgoutput",  // Use native PostgreSQL logical replication
  "publication.autocreate.mode": "filtered",
  "slot.name": "debezium_slot"
}
```

### Transformations
```json
{
  "transforms": "unwrap",
  "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
  "transforms.unwrap.drop.tombstones": "false",
  "transforms.unwrap.delete.handling.mode": "rewrite"
}
```

**ExtractNewRecordState**: Simplifies the CDC event structure by extracting only the row data.

### Data Type Handling
```json
{
  "time.precision.mode": "adaptive",      // How to handle timestamps
  "decimal.handling.mode": "double",      // How to handle decimal values
  "hstore.handling.mode": "json",         // How to handle hstore
  "interval.handling.mode": "numeric"     // How to handle intervals
}
```

### Production Recommendations
- Use specific table includes/excludes
- Configure appropriate snapshot mode
- Set proper heartbeat interval
- Enable schema history in Kafka
- Use signal table for on-demand snapshots
- Configure max batch size and poll interval

### Advanced Configuration

#### Heartbeat
```json
{
  "heartbeat.interval.ms": "10000",
  "heartbeat.topics.prefix": "__debezium-heartbeat"
}
```

#### Performance Tuning
```json
{
  "max.batch.size": "2048",
  "max.queue.size": "8192",
  "poll.interval.ms": "1000"
}
```

#### Schema Evolution
```json
{
  "schema.history.internal.kafka.bootstrap.servers": "kafka-service:9092",
  "schema.history.internal.kafka.topic": "schema-changes.testdb",
  "include.schema.changes": "true"
}
```

## Apache Ignite 3 Configuration

### Basic Settings
Located in `kubernetes/ignite/ignite-config.yaml`:

```json
{
  "node": {
    "name": "ignite-node",
    "network": {
      "port": 10800,
      "portRange": 100
    }
  },
  "cluster": {
    "name": "debezium-ignite-cluster"
  },
  "rest": {
    "port": 10300
  }
}
```

### Deployment Configuration
Located in `kubernetes/ignite/ignite-deployment.yaml`:

```yaml
replicas: 2  # Number of Ignite nodes
```

### Java Options
```yaml
JAVA_OPTS: "-Xms512m -Xmx1g -server -XX:+UseG1GC"
```

### Storage
```yaml
volumeClaimTemplates:
  - metadata:
      name: ignite-storage
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 5Gi
```

### Production Recommendations
- Deploy at least 3 nodes for HA
- Configure proper backup count
- Use appropriate cache modes
- Set proper heap sizes
- Enable persistence
- Configure proper network timeout
- Use StatefulSets (already configured)

## Ignite Consumer Application

### Environment Variables
Located in `kubernetes/ignite-consumer/ignite-consumer-deployment.yaml`:

```yaml
KAFKA_BOOTSTRAP_SERVERS: "kafka-service:9092"
KAFKA_TOPIC: "dbserver1.public.customers"
KAFKA_GROUP_ID: "ignite-consumer-group"
IGNITE_ADDRESS: "ignite-service:10800"
```

### Kafka Consumer Configuration
In the Java code (`ignite-consumer/src/main/java/...`):

```java
props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
```

### Production Recommendations
- Configure proper error handling
- Implement retry logic
- Add metrics and monitoring
- Use transactions for consistency
- Handle schema evolution
- Implement proper logging

## Resource Configuration

### Resource Limits

#### PostgreSQL
```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

#### Kafka
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

#### Kafka Connect
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

#### Ignite
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

### Production Resource Recommendations

For production workloads, increase resources based on:
- Data volume
- Number of tables
- Change frequency
- Retention requirements
- Query load

Example production values:
```yaml
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    cpu: "2000m"
```

## Security Configuration

### PostgreSQL
```yaml
# Use Kubernetes Secrets
apiVersion: v1
kind: Secret
metadata:
  name: postgres-credentials
type: Opaque
data:
  username: <base64-encoded>
  password: <base64-encoded>
```

### Kafka
- Enable SASL/SCRAM or SASL/PLAIN authentication
- Configure SSL/TLS for encryption
- Set up ACLs for authorization

### Network Policies
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: debezium-network-policy
spec:
  podSelector:
    matchLabels:
      app: kafka-connect
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: kafka
```

## Monitoring Configuration

### Kafka Connect JMX
```yaml
env:
- name: KAFKA_JMX_PORT
  value: "9999"
- name: KAFKA_JMX_HOSTNAME
  value: "localhost"
```

### Prometheus Integration
Add annotations to deployments:
```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "9999"
  prometheus.io/path: "/metrics"
```

## Backup and Recovery

### PostgreSQL
```bash
# Backup
kubectl exec -n debezium-pipeline <postgres-pod> -- \
  pg_dump -U postgres testdb > backup.sql

# Restore
kubectl exec -i -n debezium-pipeline <postgres-pod> -- \
  psql -U postgres testdb < backup.sql
```

### Kafka Topics
```bash
# List topics
kubectl exec -n debezium-pipeline <kafka-pod> -- \
  kafka-topics --bootstrap-server localhost:9092 --list

# Backup consumer offsets
kubectl exec -n debezium-pipeline <kafka-pod> -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group ignite-consumer-group --describe
```

## Environment-Specific Configuration

### Development
- Single replicas
- No persistence (emptyDir)
- Default credentials
- Auto-create topics
- Verbose logging

### Staging
- 2-3 replicas
- Persistent volumes
- Secrets for credentials
- Manual topic creation
- Info-level logging
- Resource limits

### Production
- 3+ replicas (HA)
- Persistent volumes with backups
- Secrets for all credentials
- Strict network policies
- Manual topic creation
- Warning-level logging
- Proper resource limits and requests
- Monitoring and alerting
- Disaster recovery plan
