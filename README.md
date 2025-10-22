# Debezium CDC Pipeline with Postgres, Kafka, and Apache Ignite 3

This project implements a Change Data Capture (CDC) pipeline using:
- **Debezium Postgres Connector** - Captures changes from PostgreSQL database
- **Apache Kafka** - Message broker for streaming events
- **Apache Ignite 3** - In-memory cache for consuming Kafka events
- **Kubernetes** - Container orchestration

## Architecture

```
PostgreSQL → Debezium Connector → Kafka → Ignite 3 Cache
```

## Prerequisites

- Kubernetes cluster (minikube, kind, or cloud provider)
- kubectl configured
- Helm 3.x (optional, for easier deployment)

## Project Structure

```
.
├── kubernetes/
│   ├── namespace.yaml
│   ├── postgres/
│   │   ├── postgres-deployment.yaml
│   │   ├── postgres-service.yaml
│   │   └── postgres-configmap.yaml
│   ├── kafka/
│   │   ├── zookeeper-deployment.yaml
│   │   ├── zookeeper-service.yaml
│   │   ├── kafka-deployment.yaml
│   │   └── kafka-service.yaml
│   ├── kafka-connect/
│   │   ├── kafka-connect-deployment.yaml
│   │   ├── kafka-connect-service.yaml
│   │   └── debezium-connector-config.yaml
│   └── ignite/
│       ├── ignite-deployment.yaml
│       ├── ignite-service.yaml
│       └── ignite-config.yaml
├── connectors/
│   ├── postgres-source-connector.json
│   └── ignite-sink-connector.json
├── docker/
│   └── kafka-connect-ignite/
│       └── Dockerfile
└── scripts/
    ├── deploy-all.sh
    ├── create-connectors.sh
    └── cleanup.sh
```

## Quick Start

1. **Deploy all components:**
   ```bash
   chmod +x scripts/deploy-all.sh
   ./scripts/deploy-all.sh
   ```

2. **Wait for all pods to be ready:**
   ```bash
   kubectl get pods -n debezium-pipeline -w
   ```

3. **Create Debezium connectors:**
   ```bash
   chmod +x scripts/create-connectors.sh
   ./scripts/create-connectors.sh
   ```

4. **Test the pipeline:**
   ```bash
   # Connect to PostgreSQL and insert data
   kubectl exec -it -n debezium-pipeline <postgres-pod-name> -- psql -U postgres -d testdb
   
   # Insert test data
   INSERT INTO customers (name, email) VALUES ('John Doe', 'john@example.com');
   ```

## Deployment Steps

### 1. Create Namespace
```bash
kubectl apply -f kubernetes/namespace.yaml
```

### 2. Deploy PostgreSQL
```bash
kubectl apply -f kubernetes/postgres/
```

### 3. Deploy Kafka & Zookeeper
```bash
kubectl apply -f kubernetes/kafka/
```

### 4. Deploy Kafka Connect with Debezium
```bash
kubectl apply -f kubernetes/kafka-connect/
```

### 5. Deploy Apache Ignite 3
```bash
kubectl apply -f kubernetes/ignite/
```

### 6. Register Connectors
```bash
./scripts/create-connectors.sh
```

## Configuration

### PostgreSQL Configuration
- Database: `testdb`
- User: `postgres`
- Password: `postgres` (change in production!)
- WAL level: `logical` (required for Debezium)

### Kafka Configuration
- Bootstrap servers: `kafka-service.debezium-pipeline.svc.cluster.local:9092`
- Topics are auto-created by Debezium

### Debezium Postgres Connector
- Connector name: `postgres-source-connector`
- Captures all tables in `public` schema
- Topic prefix: `dbserver1`

### Apache Ignite 3 Configuration
- Cache name: `customerCache`
- Backup count: 1
- Consumes from Kafka topics created by Debezium

## Monitoring

Check connector status:
```bash
kubectl exec -it -n debezium-pipeline <kafka-connect-pod> -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector/status | jq
```

View Kafka topics:
```bash
kubectl exec -it -n debezium-pipeline <kafka-pod> -- \
  kafka-topics.sh --bootstrap-server localhost:9092 --list
```

## Cleanup

```bash
./scripts/cleanup.sh
```

## Notes

- This is a development setup. For production, consider:
  - Using persistent volumes for Kafka and PostgreSQL
  - Implementing proper security (SSL/TLS, authentication)
  - Setting up monitoring and alerting
  - Using StatefulSets for stateful services
  - Configuring resource limits and requests
  - Using secrets for sensitive data

## Troubleshooting

### Pods not starting
```bash
kubectl describe pod <pod-name> -n debezium-pipeline
kubectl logs <pod-name> -n debezium-pipeline
```

### Connector issues
```bash
# Check connector logs
kubectl logs -n debezium-pipeline <kafka-connect-pod>

# Check connector status
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector/status
```
