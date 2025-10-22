# Project Summary

## Overview
This project implements a complete Change Data Capture (CDC) pipeline using Debezium, Kafka, and Apache Ignite 3, all deployed on Kubernetes.

## What This Project Does

1. **Captures Database Changes**: Monitors PostgreSQL for INSERT, UPDATE, and DELETE operations
2. **Streams Events**: Publishes database changes to Kafka topics in real-time
3. **Caches Data**: Consumes Kafka events and stores them in Apache Ignite 3 in-memory cache
4. **Runs in Kubernetes**: All components deployed as containers, ready for production scaling

## Technology Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Database | PostgreSQL | 15 | Source database with CDC enabled |
| CDC Platform | Debezium | 2.4 | Change Data Capture connector |
| Message Broker | Apache Kafka | 7.5 (Confluent) | Event streaming platform |
| Coordination | Apache Zookeeper | 7.5 (Confluent) | Kafka cluster coordination |
| Runtime | Kafka Connect | 7.5 (Confluent) | Connector runtime |
| Cache | Apache Ignite | 3.0 | In-memory data grid |
| Consumer | Custom Java App | Java 11 | Kafka to Ignite integration |
| Orchestration | Kubernetes | - | Container orchestration |

## Project Structure

```
debezium/
├── README.md                      # Main documentation
├── QUICKSTART.md                  # Quick start guide
├── ARCHITECTURE.md                # Architecture details
├── CONFIGURATION.md               # Configuration guide
├── TROUBLESHOOTING.md             # Troubleshooting guide
├── .gitignore                     # Git ignore file
│
├── kubernetes/                    # Kubernetes manifests
│   ├── namespace.yaml            # Namespace definition
│   ├── postgres/                 # PostgreSQL deployment
│   │   ├── postgres-configmap.yaml
│   │   ├── postgres-deployment.yaml
│   │   └── postgres-service.yaml
│   ├── kafka/                    # Kafka & Zookeeper
│   │   ├── zookeeper-deployment.yaml
│   │   ├── zookeeper-service.yaml
│   │   ├── kafka-deployment.yaml
│   │   └── kafka-service.yaml
│   ├── kafka-connect/            # Kafka Connect
│   │   ├── kafka-connect-deployment.yaml
│   │   └── kafka-connect-service.yaml
│   ├── ignite/                   # Apache Ignite 3
│   │   ├── ignite-config.yaml
│   │   ├── ignite-deployment.yaml
│   │   └── ignite-service.yaml
│   └── ignite-consumer/          # Ignite Consumer
│       └── ignite-consumer-deployment.yaml
│
├── connectors/                    # Connector configurations
│   ├── postgres-source-connector.json
│   └── ignite-sink-connector.json
│
├── ignite-consumer/              # Custom Kafka consumer
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/debezium/ignite/
│       └── IgniteKafkaConsumer.java
│
└── scripts/                      # Automation scripts
    ├── deploy-all.sh            # Deploy everything
    ├── create-connectors.sh     # Create Debezium connectors
    ├── cleanup.sh               # Clean up resources
    ├── test-pipeline.sh         # Test the pipeline
    └── monitor.sh               # Monitor the system
```

## Components Deployed

### 1. PostgreSQL (1 pod)
- Database with logical replication enabled
- Sample `customers` table
- WAL level set to `logical`

### 2. Zookeeper (1 pod)
- Kafka cluster coordination
- Metadata management

### 3. Kafka (1 pod)
- Message broker
- Auto-topic creation
- Single broker (scalable to 3+ for production)

### 4. Kafka Connect (1 pod)
- Runs Debezium connectors
- REST API on port 8083
- Distributed mode

### 5. Apache Ignite (2 pods)
- In-memory data grid
- Distributed cache
- SQL support
- StatefulSet deployment

### 6. Ignite Consumer (1 pod)
- Custom Java application
- Kafka consumer
- Writes to Ignite cache

## Data Flow

```
1. Application writes to PostgreSQL
   ↓
2. PostgreSQL WAL captures changes
   ↓
3. Debezium reads from WAL via replication slot
   ↓
4. Debezium publishes events to Kafka
   ↓
5. Ignite Consumer subscribes to Kafka topics
   ↓
6. Consumer parses CDC events
   ↓
7. Consumer writes to Ignite cache
```

## Key Features

### Change Data Capture
- ✅ Captures all INSERT, UPDATE, DELETE operations
- ✅ Initial snapshot of existing data
- ✅ Schema evolution tracking
- ✅ Transaction metadata included

### Event Streaming
- ✅ Reliable message delivery via Kafka
- ✅ Ordered message processing
- ✅ Consumer groups for scalability
- ✅ Offset management for fault tolerance

### Caching
- ✅ In-memory data storage
- ✅ SQL query support
- ✅ Distributed architecture
- ✅ High availability (2+ nodes)

### Kubernetes Deployment
- ✅ All components containerized
- ✅ Service discovery via DNS
- ✅ ConfigMaps for configuration
- ✅ Resource limits defined
- ✅ Health checks configured

## Usage Examples

### 1. Insert Data
```sql
INSERT INTO customers (name, email) VALUES ('John Doe', 'john@example.com');
```
→ Creates CDC event → Published to Kafka → Stored in Ignite

### 2. Update Data
```sql
UPDATE customers SET name = 'John Smith' WHERE email = 'john@example.com';
```
→ Creates CDC event with before/after → Published to Kafka → Updated in Ignite

### 3. Delete Data
```sql
DELETE FROM customers WHERE email = 'john@example.com';
```
→ Creates CDC event → Published to Kafka → Deleted from Ignite

## Deployment Options

### Quick Deploy (5 minutes)
```bash
./scripts/deploy-all.sh
./scripts/create-connectors.sh
```

### Manual Deploy (Step by step)
See `QUICKSTART.md` for detailed instructions

### Production Deploy
See `CONFIGURATION.md` for production recommendations

## Testing

### Automated Test
```bash
./scripts/test-pipeline.sh
```

### Manual Test
1. Connect to PostgreSQL
2. Insert/Update/Delete data
3. Verify in Kafka
4. Check Ignite Consumer logs

## Monitoring

### Simple Monitor
```bash
./scripts/monitor.sh
```

### Individual Component Logs
```bash
kubectl logs -f -n debezium-pipeline <pod-name>
```

### Connector Status
```bash
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector/status
```

## Cleanup

### Full Cleanup
```bash
./scripts/cleanup.sh
```

### Manual Cleanup
```bash
kubectl delete namespace debezium-pipeline
```

## Production Readiness Checklist

### Security
- [ ] Use Kubernetes Secrets for credentials
- [ ] Enable SSL/TLS for all connections
- [ ] Configure network policies
- [ ] Set up RBAC
- [ ] Enable authentication on all services

### High Availability
- [ ] Deploy 3+ Kafka brokers
- [ ] Use replication factor 3
- [ ] Deploy 3+ Ignite nodes
- [ ] Configure backup count 2
- [ ] Use PostgreSQL replication

### Data Persistence
- [ ] Use PersistentVolumes for Kafka
- [ ] Use PersistentVolumes for PostgreSQL
- [ ] Use PersistentVolumes for Ignite
- [ ] Configure proper retention policies
- [ ] Set up backup strategy

### Monitoring
- [ ] Deploy Prometheus
- [ ] Deploy Grafana
- [ ] Configure alerting
- [ ] Set up log aggregation
- [ ] Monitor resource usage

### Performance
- [ ] Configure resource requests/limits
- [ ] Tune JVM settings
- [ ] Optimize Kafka settings
- [ ] Configure proper batch sizes
- [ ] Set up horizontal pod autoscaling

## Customization

### Add More Tables
Edit `connectors/postgres-source-connector.json`:
```json
"table.include.list": "public.customers,public.orders,public.products"
```

### Change Topic Naming
Edit connector configuration:
```json
"topic.prefix": "myapp"
```
Topics will be: `myapp.public.customers`

### Modify Consumer Logic
Edit `ignite-consumer/src/main/java/.../IgniteKafkaConsumer.java`

### Add Transformations
See Debezium documentation for available SMTs (Single Message Transforms)

## Known Limitations

1. **Ignite 3 Kafka Connect**: No official sink connector exists yet
   - Solution: Custom consumer application (included)

2. **Single Node Setup**: Default is single instance per component
   - Solution: Scale up for production (see CONFIGURATION.md)

3. **No Persistence**: Uses emptyDir volumes by default
   - Solution: Configure PersistentVolumes (examples in comments)

4. **Basic Security**: No encryption or authentication
   - Solution: Enable SSL/TLS and authentication (see CONFIGURATION.md)

## Learning Resources

### Debezium
- Official docs: https://debezium.io/documentation/
- Tutorial: https://debezium.io/documentation/reference/tutorial.html

### Apache Kafka
- Official docs: https://kafka.apache.org/documentation/
- Confluent docs: https://docs.confluent.io/

### Apache Ignite 3
- Official docs: https://ignite.apache.org/docs/3.0.0/
- Getting started: https://ignite.apache.org/docs/3.0.0/quick-start/getting-started-guide

### Kubernetes
- Official docs: https://kubernetes.io/docs/
- Kubectl cheatsheet: https://kubernetes.io/docs/reference/kubectl/cheatsheet/

## Contributing

To extend this project:

1. Add more connectors (sources or sinks)
2. Implement data transformations
3. Add monitoring dashboards
4. Create Helm charts
5. Add integration tests
6. Implement schema registry
7. Add data validation
8. Implement dead letter queue

## License

This is an educational project. Adjust as needed for your use case.

## Support

For issues:
1. Check `TROUBLESHOOTING.md`
2. Review component logs
3. Verify configurations
4. Check Kubernetes events

## Version History

- **v1.0.0** - Initial release
  - PostgreSQL CDC
  - Kafka streaming
  - Ignite 3 caching
  - Kubernetes deployment
  - Complete automation scripts
  - Comprehensive documentation
