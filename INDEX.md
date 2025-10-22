# Debezium CDC Pipeline - Documentation Index

## 🎯 Where to Start

### New Users
1. **[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)** - Start here for a complete overview
2. **[README.md](README.md)** - Main project documentation
3. **[QUICKSTART.md](QUICKSTART.md)** - Get up and running in 5 minutes

### Developers
1. **[ARCHITECTURE.md](ARCHITECTURE.md)** - Understand the system design
2. **[CONFIGURATION.md](CONFIGURATION.md)** - Customize configurations
3. **Java Source**: `ignite-consumer/src/main/java/com/debezium/ignite/IgniteKafkaConsumer.java`

### Operations
1. **[QUICKSTART.md](QUICKSTART.md)** - Deployment instructions
2. **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common issues and solutions
3. **[Makefile](Makefile)** - Available commands

## 📚 Complete Documentation

### Getting Started
- **[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)** - Visual overview with diagrams and statistics
- **[README.md](README.md)** - Comprehensive project documentation
- **[QUICKSTART.md](QUICKSTART.md)** - Step-by-step deployment guide
- **[SUMMARY.md](SUMMARY.md)** - Executive summary and feature list

### Technical Documentation
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture and data flow
- **[CONFIGURATION.md](CONFIGURATION.md)** - Configuration options for all components
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Debugging and problem resolution

### Build & Deployment
- **[Makefile](Makefile)** - Make commands for common operations
- **[scripts/deploy-all.sh](scripts/deploy-all.sh)** - Automated deployment
- **[scripts/create-connectors.sh](scripts/create-connectors.sh)** - Connector setup
- **[scripts/test-pipeline.sh](scripts/test-pipeline.sh)** - Testing script
- **[scripts/monitor.sh](scripts/monitor.sh)** - Monitoring script
- **[scripts/cleanup.sh](scripts/cleanup.sh)** - Cleanup script

## 🗂️ Project Structure

```
debezium/
│
├── 📘 Documentation (7 markdown files)
│   ├── INDEX.md                     ← You are here
│   ├── PROJECT_OVERVIEW.md          ← Start here
│   ├── README.md                    ← Main documentation
│   ├── QUICKSTART.md                ← Quick deployment guide
│   ├── ARCHITECTURE.md              ← System architecture
│   ├── CONFIGURATION.md             ← Configuration reference
│   ├── TROUBLESHOOTING.md           ← Problem solving
│   └── SUMMARY.md                   ← Project summary
│
├── 🐳 Kubernetes (14 YAML files)
│   ├── namespace.yaml               ← Kubernetes namespace
│   ├── postgres/                    ← PostgreSQL database
│   ├── kafka/                       ← Kafka & Zookeeper
│   ├── kafka-connect/               ← Kafka Connect + Debezium
│   ├── ignite/                      ← Apache Ignite 3
│   └── ignite-consumer/             ← Custom consumer app
│
├── 🔌 Connectors (2 JSON files)
│   ├── postgres-source-connector.json    ← Debezium config
│   └── ignite-sink-connector.json        ← Sink config
│
├── ☕ Java Application
│   ├── ignite-consumer/
│   │   ├── Dockerfile               ← Container image
│   │   ├── pom.xml                  ← Maven build
│   │   └── src/main/java/...        ← Java source code
│
├── 🔧 Scripts (5 shell scripts)
│   ├── scripts/deploy-all.sh        ← Deploy everything
│   ├── scripts/create-connectors.sh ← Create connectors
│   ├── scripts/test-pipeline.sh     ← Test the system
│   ├── scripts/monitor.sh           ← Monitor components
│   └── scripts/cleanup.sh           ← Remove everything
│
└── ⚙️ Build Tools
    ├── Makefile                     ← Make commands
    └── .gitignore                   ← Git ignore rules
```

## 🚀 Common Tasks

### Initial Setup
```bash
# Check prerequisites
make check-prereqs

# Deploy entire pipeline
make deploy

# Create Debezium connectors
make connectors

# Test the pipeline
make test
```

### Daily Operations
```bash
# Check status
make status

# View logs
make logs

# Monitor system
make monitor

# Insert test data
make insert-test

# Connect to PostgreSQL
make psql
```

### Troubleshooting
```bash
# View component logs
make logs-postgres
make logs-kafka
make logs-connect
make logs-consumer

# Check connector status
make connector-status

# List Kafka topics
make topics
```

### Cleanup
```bash
# Remove everything
make clean
```

## 🔍 Quick Reference

### Kubernetes Namespaces
- All resources are in: `debezium-pipeline`

### Services
- PostgreSQL: `postgres-service:5432`
- Kafka: `kafka-service:9092`
- Kafka Connect: `kafka-connect-service:8083`
- Ignite: `ignite-service:10800` (client), `10300` (REST)

### Kafka Topics
- CDC Events: `dbserver1.public.customers`
- Schema History: `schema-changes.testdb`
- Connector Configs: `debezium_configs`
- Connector Offsets: `debezium_offsets`
- Connector Status: `debezium_statuses`

### Default Credentials (Development Only)
- PostgreSQL: `postgres` / `postgres`
- Database: `testdb`

⚠️ **Change these for production!**

## 📖 Documentation by Topic

### Understanding the System
1. [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) - Complete visual overview
2. [ARCHITECTURE.md](ARCHITECTURE.md) - Detailed architecture
3. [README.md](README.md) - Component descriptions

### Deploying the System
1. [QUICKSTART.md](QUICKSTART.md) - Quick deployment
2. [Makefile](Makefile) - Make commands
3. [scripts/deploy-all.sh](scripts/deploy-all.sh) - Deployment script

### Configuring the System
1. [CONFIGURATION.md](CONFIGURATION.md) - All configuration options
2. [kubernetes/](kubernetes/) - YAML manifests
3. [connectors/](connectors/) - Connector configs

### Operating the System
1. [QUICKSTART.md](QUICKSTART.md) - Verification steps
2. [scripts/monitor.sh](scripts/monitor.sh) - Monitoring
3. [Makefile](Makefile) - Operational commands

### Troubleshooting
1. [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Problem solving guide
2. [QUICKSTART.md](QUICKSTART.md) - Common issues section
3. Make logs commands - View component logs

### Extending the System
1. [CONFIGURATION.md](CONFIGURATION.md) - Configuration options
2. [ARCHITECTURE.md](ARCHITECTURE.md) - Understanding components
3. [ignite-consumer/](ignite-consumer/) - Consumer source code

## 💡 Tips for Success

### First Time Users
1. Start with [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)
2. Follow [QUICKSTART.md](QUICKSTART.md) step by step
3. Run `make test` to verify
4. Explore with `make psql` and insert data

### Developers
1. Read [ARCHITECTURE.md](ARCHITECTURE.md) to understand design
2. Review [CONFIGURATION.md](CONFIGURATION.md) for options
3. Examine the Java consumer code
4. Check Kubernetes manifests

### Operators
1. Use [Makefile](Makefile) commands
2. Keep [TROUBLESHOOTING.md](TROUBLESHOOTING.md) handy
3. Monitor logs regularly
4. Test changes in dev first

### Production Deployment
1. Read production sections in [CONFIGURATION.md](CONFIGURATION.md)
2. Review security checklist in [SUMMARY.md](SUMMARY.md)
3. Implement monitoring and alerting
4. Plan backup and recovery strategy

## 🎓 Learning Path

### Beginner
1. What is CDC? → [README.md](README.md)
2. Deploy the system → [QUICKSTART.md](QUICKSTART.md)
3. Test data flow → [scripts/test-pipeline.sh](scripts/test-pipeline.sh)
4. Explore logs → `make logs`

### Intermediate
1. Understand architecture → [ARCHITECTURE.md](ARCHITECTURE.md)
2. Modify configurations → [CONFIGURATION.md](CONFIGURATION.md)
3. Add more tables → Edit connector config
4. Customize consumer → Edit Java code

### Advanced
1. Production deployment → [CONFIGURATION.md](CONFIGURATION.md) production sections
2. HA setup → Scale components
3. Performance tuning → Adjust resources
4. Security hardening → Enable SSL/TLS, authentication
5. Monitoring setup → Add Prometheus/Grafana

## 📞 Getting Help

### Self-Help Resources
1. **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Most common issues covered
2. **Logs** - `make logs` or `make logs-<component>`
3. **Status** - `make status` to check components
4. **Events** - `kubectl get events -n debezium-pipeline`

### External Resources
- Debezium: https://debezium.io/documentation/
- Kafka: https://kafka.apache.org/documentation/
- Ignite 3: https://ignite.apache.org/docs/3.0.0/
- Kubernetes: https://kubernetes.io/docs/

## 🎯 Goals by User Type

### Data Engineer
- ✅ Understand CDC concepts
- ✅ Configure Debezium connectors
- ✅ Monitor data flow
- ✅ Handle schema changes

### DevOps Engineer
- ✅ Deploy on Kubernetes
- ✅ Monitor system health
- ✅ Troubleshoot issues
- ✅ Scale components

### Application Developer
- ✅ Understand event structure
- ✅ Consume CDC events
- ✅ Process changes
- ✅ Integrate with applications

### System Architect
- ✅ Design data pipelines
- ✅ Plan for scale
- ✅ Ensure high availability
- ✅ Security and compliance

---

**Need help?** Start with [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) for a complete visual overview!
