# Debezium CDC Pipeline - Project Overview

## 🎯 Project Goal

Create a complete Change Data Capture (CDC) pipeline that:
1. Captures changes from PostgreSQL database
2. Streams events through Apache Kafka
3. Stores data in Apache Ignite 3 in-memory cache
4. All running on Kubernetes

## 📊 Project Statistics

- **Total Files**: 32
- **Lines of Code/Config**: ~3,300
- **Kubernetes YAML Files**: 14
- **Shell Scripts**: 5
- **Documentation Files**: 6
- **Java Source Files**: 1
- **Docker Images**: 7 (PostgreSQL, Kafka, Zookeeper, Kafka Connect, Ignite, Custom Consumer)

## 🏗️ Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Kubernetes Cluster                               │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                  Namespace: debezium-pipeline                      │  │
│  │                                                                     │  │
│  │  ┌──────────────┐                                                 │  │
│  │  │  PostgreSQL  │  Logical Replication (WAL)                      │  │
│  │  │   Database   │────────────────────┐                            │  │
│  │  │              │                     │                            │  │
│  │  │ Port: 5432   │                     ▼                            │  │
│  │  └──────────────┘            ┌─────────────────┐                  │  │
│  │        │                      │ Kafka Connect   │                  │  │
│  │        │                      │   + Debezium    │                  │  │
│  │        │                      │                 │                  │  │
│  │        │                      │ Port: 8083      │                  │  │
│  │        │                      └────────┬────────┘                  │  │
│  │        │                               │ Publish CDC Events        │  │
│  │        │                               ▼                            │  │
│  │        │                      ┌─────────────────┐                  │  │
│  │        │                      │  Apache Kafka   │                  │  │
│  │        │                      │     Broker      │                  │  │
│  │        │                      │                 │                  │  │
│  │        │                      │ Port: 9092      │                  │  │
│  │        │                      └────────┬────────┘                  │  │
│  │        │                               │ Subscribe to Topics       │  │
│  │        │                               ▼                            │  │
│  │        │                      ┌─────────────────┐                  │  │
│  │        │                      │     Ignite      │                  │  │
│  │        │                      │    Consumer     │                  │  │
│  │        │                      │  (Custom Java)  │                  │  │
│  │        │                      └────────┬────────┘                  │  │
│  │        │                               │ Write via SQL             │  │
│  │        │                               ▼                            │  │
│  │        │                      ┌─────────────────┐                  │  │
│  │        │                      │ Apache Ignite 3 │                  │  │
│  │        │                      │   Data Grid     │                  │  │
│  │        │                      │   (2 nodes)     │                  │  │
│  │        │                      │                 │                  │  │
│  │        │                      │ Ports: 10800,   │                  │  │
│  │        │                      │        10300    │                  │  │
│  │        │                      └─────────────────┘                  │  │
│  │        │                                                            │  │
│  │        └────────────────────────────────────────────────────────┐  │  │
│  │                          Zookeeper (Coordination)                │  │  │
│  │                          Port: 2181                              │  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

## 📁 Project Structure

```
debezium/
│
├── 📘 Documentation (6 files)
│   ├── README.md                    # Main project documentation
│   ├── QUICKSTART.md                # Quick start guide
│   ├── ARCHITECTURE.md              # Detailed architecture
│   ├── CONFIGURATION.md             # Configuration reference
│   ├── TROUBLESHOOTING.md           # Troubleshooting guide
│   └── SUMMARY.md                   # Project summary
│
├── ⚙️ Build & Deploy
│   ├── Makefile                     # Make commands for easy ops
│   └── .gitignore                   # Git ignore rules
│
├── 🐳 Kubernetes Manifests (14 YAML files)
│   ├── namespace.yaml               # Namespace definition
│   │
│   ├── postgres/                    # PostgreSQL (3 files)
│   │   ├── postgres-configmap.yaml
│   │   ├── postgres-deployment.yaml
│   │   └── postgres-service.yaml
│   │
│   ├── kafka/                       # Kafka & Zookeeper (4 files)
│   │   ├── zookeeper-deployment.yaml
│   │   ├── zookeeper-service.yaml
│   │   ├── kafka-deployment.yaml
│   │   └── kafka-service.yaml
│   │
│   ├── kafka-connect/               # Kafka Connect (2 files)
│   │   ├── kafka-connect-deployment.yaml
│   │   └── kafka-connect-service.yaml
│   │
│   ├── ignite/                      # Apache Ignite (3 files)
│   │   ├── ignite-config.yaml
│   │   ├── ignite-deployment.yaml
│   │   └── ignite-service.yaml
│   │
│   └── ignite-consumer/             # Consumer (1 file)
│       └── ignite-consumer-deployment.yaml
│
├── 🔌 Connector Configurations (2 JSON files)
│   ├── postgres-source-connector.json
│   └── ignite-sink-connector.json
│
├── ☕ Ignite Consumer Application
│   ├── Dockerfile                   # Container image
│   ├── pom.xml                      # Maven build
│   └── src/main/java/com/debezium/ignite/
│       └── IgniteKafkaConsumer.java # Main application
│
└── 🔧 Automation Scripts (5 shell scripts)
    ├── deploy-all.sh                # Deploy everything
    ├── create-connectors.sh         # Create connectors
    ├── cleanup.sh                   # Clean up resources
    ├── test-pipeline.sh             # Test pipeline
    └── monitor.sh                   # Monitor system
```

## 🚀 Quick Start Commands

### Using Make (Recommended)
```bash
make check-prereqs    # Check prerequisites
make deploy           # Deploy everything
make connectors       # Create connectors
make test             # Test pipeline
make monitor          # Monitor system
make clean            # Cleanup
```

### Using Scripts
```bash
./scripts/deploy-all.sh
./scripts/create-connectors.sh
./scripts/test-pipeline.sh
./scripts/cleanup.sh
```

## 🔄 Data Flow Example

### 1. Insert Operation
```sql
-- In PostgreSQL
INSERT INTO customers (name, email) VALUES ('Alice', 'alice@example.com');
```

### 2. CDC Event Generated
```json
{
  "id": 4,
  "name": "Alice",
  "email": "alice@example.com",
  "created_at": "2025-10-13T12:00:00Z",
  "updated_at": "2025-10-13T12:00:00Z"
}
```

### 3. Published to Kafka
- Topic: `dbserver1.public.customers`
- Key: `{"id": 4}`
- Value: Full record JSON

### 4. Consumed by Ignite Consumer
- Java application reads from Kafka
- Parses JSON message
- Extracts data fields

### 5. Written to Ignite
```sql
MERGE INTO customers (id, name, email) VALUES (4, 'Alice', 'alice@example.com');
```

## 📦 Components Version Matrix

| Component | Image/Version | Purpose |
|-----------|---------------|---------|
| PostgreSQL | `postgres:15-alpine` | Source database |
| Zookeeper | `confluentinc/cp-zookeeper:7.5.0` | Kafka coordination |
| Kafka | `confluentinc/cp-kafka:7.5.0` | Message broker |
| Kafka Connect | `debezium/connect:2.4` | CDC runtime |
| Ignite | `apacheignite/ignite3:3.0.0` | In-memory cache |
| Consumer | Custom built (Java 11) | Kafka to Ignite |

## 🎓 Key Concepts

### Change Data Capture (CDC)
- Captures database changes in real-time
- Uses PostgreSQL Write-Ahead Log (WAL)
- No application code changes needed
- Minimal performance impact

### Event Streaming
- Decouples source and target systems
- Enables multiple consumers
- Provides durability and replay capability
- Scales horizontally

### In-Memory Caching
- Fast data access
- Distributed storage
- SQL query support
- High availability with replication

### Kubernetes Deployment
- Container orchestration
- Service discovery
- Self-healing
- Horizontal scaling

## 🛠️ Available Operations

### Deployment
```bash
make deploy           # Full deployment
make build-consumer   # Build consumer image
make connectors       # Create connectors
```

### Testing
```bash
make test             # Run tests
make insert-test      # Insert test data
make psql             # Connect to PostgreSQL
```

### Monitoring
```bash
make status           # Show status
make monitor          # Live monitoring
make logs             # View all logs
make logs-postgres    # PostgreSQL logs
make logs-kafka       # Kafka logs
make logs-connect     # Kafka Connect logs
make logs-consumer    # Consumer logs
make connector-status # Connector status
make topics           # List Kafka topics
```

### Cleanup
```bash
make clean            # Remove everything
```

## 📈 Production Readiness

### Current: Development Setup
- ✅ Single instance per component
- ✅ No persistence (emptyDir)
- ✅ Simple configuration
- ✅ Easy to deploy and test
- ⚠️ Not suitable for production

### Recommended: Production Setup
- ✅ 3+ replicas for HA
- ✅ Persistent volumes
- ✅ SSL/TLS encryption
- ✅ Authentication enabled
- ✅ Resource limits configured
- ✅ Monitoring and alerting
- ✅ Backup strategy
- ✅ Disaster recovery plan

See `CONFIGURATION.md` for production setup details.

## 🎯 Use Cases

1. **Real-time Data Replication**
   - Sync data between databases
   - Create read replicas
   - Migrate data

2. **Cache Warming**
   - Populate caches automatically
   - Keep caches in sync
   - Invalidate stale data

3. **Event-Driven Architecture**
   - Trigger downstream processes
   - Audit logging
   - Analytics pipelines

4. **Microservices Data Sharing**
   - Share data between services
   - Maintain eventual consistency
   - Decouple services

## 🔍 Monitoring Endpoints

| Component | Endpoint | Purpose |
|-----------|----------|---------|
| Kafka Connect | http://localhost:8083 | REST API |
| Ignite REST | http://localhost:10300 | Management API |
| PostgreSQL | localhost:5432 | Database access |
| Kafka | localhost:9092 | Broker |

Use `make forward-*` commands to access locally.

## 📚 Documentation Guide

- **Start Here**: `README.md`
- **Quick Deploy**: `QUICKSTART.md`
- **Understanding**: `ARCHITECTURE.md`
- **Customizing**: `CONFIGURATION.md`
- **Issues**: `TROUBLESHOOTING.md`
- **Overview**: `SUMMARY.md`

## 🤝 Next Steps

1. **Deploy**: `make deploy`
2. **Test**: `make test`
3. **Explore**: `make psql`, insert data, check Kafka
4. **Monitor**: `make monitor`
5. **Learn**: Read documentation files
6. **Customize**: Modify configurations
7. **Scale**: Add more replicas
8. **Secure**: Enable authentication
9. **Monitor**: Add Prometheus/Grafana
10. **Productionize**: Follow production checklist

## ⚡ Performance Tips

- Increase Kafka partitions for parallelism
- Add more Kafka Connect workers
- Scale Ignite cluster for distribution
- Tune JVM heap sizes
- Configure proper batch sizes
- Use connection pooling
- Enable compression

## 🔐 Security Considerations

- Use Kubernetes Secrets for credentials
- Enable SSL/TLS for all connections
- Configure network policies
- Set up RBAC
- Enable authentication on Kafka
- Use PostgreSQL SSL connections
- Implement audit logging

## 📞 Getting Help

1. Check `TROUBLESHOOTING.md`
2. View component logs: `make logs`
3. Check Kubernetes events
4. Verify configurations
5. Test connectivity between services
6. Review Debezium/Kafka/Ignite docs

## ✨ Features Highlights

- ✅ Complete CDC pipeline
- ✅ Kubernetes-native deployment
- ✅ Automated scripts
- ✅ Comprehensive documentation
- ✅ Production-ready architecture
- ✅ Scalable design
- ✅ Easy to customize
- ✅ Well-tested components

## 🏁 Success Criteria

Your deployment is successful when:

1. ✅ All pods are Running (1/1 or 2/2 ready)
2. ✅ Connector status shows RUNNING
3. ✅ Kafka topics are created
4. ✅ Test data flows from PostgreSQL to Ignite
5. ✅ Consumer logs show message processing
6. ✅ No errors in component logs

Run `make test` to verify!
