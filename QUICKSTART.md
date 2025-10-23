# Quick Start Guide

## Prerequisites Check

Before starting, ensure you have:

```bash
# Check if kubectl is installed
kubectl version --client

# Check if you have access to a Kubernetes cluster
kubectl cluster-info

# Check if Docker is installed (for building Ignite consumer)
docker version

# Optional: If using minikube
minikube status
```

## Step-by-Step Deployment

### Option 1: Automated Deployment (Recommended)

```bash
# 1. Navigate to project directory
cd /home/user/work/study/debezium

# 2. Deploy all components (this will take 5-10 minutes)
./scripts/deploy-all.sh

# 3. Wait for all pods to be ready
kubectl get pods -n debezium-pipeline -w
# Press Ctrl+C when all pods show 'Running' and '1/1' ready

# 4. Create Debezium connector
./scripts/create-connectors.sh

# 5. Test the pipeline
./scripts/test-pipeline.sh
```

### Option 2: Manual Step-by-Step Deployment

```bash
# 1. Create namespace
kubectl apply -f kubernetes/namespace.yaml

# 2. Deploy PostgreSQL
kubectl apply -f kubernetes/postgres/
kubectl wait --for=condition=ready pod -l app=postgres -n debezium-pipeline --timeout=300s

# 3. Deploy Zookeeper
kubectl apply -f kubernetes/kafka/zookeeper-deployment.yaml
kubectl apply -f kubernetes/kafka/zookeeper-service.yaml
kubectl wait --for=condition=ready pod -l app=zookeeper -n debezium-pipeline --timeout=300s

# 4. Deploy Kafka
kubectl apply -f kubernetes/kafka/kafka-deployment.yaml
kubectl apply -f kubernetes/kafka/kafka-service.yaml
kubectl wait --for=condition=ready pod -l app=kafka -n debezium-pipeline --timeout=300s
sleep 20  # Wait for Kafka to fully initialize

# 5. Deploy Kafka Connect
kubectl apply -f kubernetes/kafka-connect/
kubectl wait --for=condition=ready pod -l app=kafka-connect -n debezium-pipeline --timeout=300s
sleep 30  # Wait for Kafka Connect to fully initialize

# 6. Deploy Ignite
kubectl apply -f kubernetes/ignite/
kubectl wait --for=condition=ready pod -l app=ignite -n debezium-pipeline --timeout=300s
sleep 20  # Wait for Ignite to fully initialize

# 6b. Initialize Ignite cluster (REQUIRED)
./scripts/init-ignite-cluster.sh

# 7. Build and deploy Ignite Consumer
# The build script automatically detects your Kubernetes environment (minikube, kind, Docker Desktop, etc.)
./scripts/build-ignite-consumer.sh
kubectl apply -f kubernetes/ignite-consumer/
kubectl wait --for=condition=ready pod -l app=ignite-consumer -n debezium-pipeline --timeout=300s

# 8. Create Debezium connector
./scripts/create-connectors.sh

# 9. Test the pipeline
./scripts/test-pipeline.sh
```

## Verification Steps

### 1. Check All Pods are Running

```bash
kubectl get pods -n debezium-pipeline
```

Expected output:
```
NAME                              READY   STATUS    RESTARTS   AGE
ignite-0                          1/1     Running   0          5m
ignite-1                          1/1     Running   0          5m
ignite-consumer-xxx               1/1     Running   0          5m
kafka-xxx                         1/1     Running   0          8m
kafka-connect-xxx                 1/1     Running   0          7m
postgres-xxx                      1/1     Running   0          10m
zookeeper-xxx                     1/1     Running   0          9m
```

### 2. Check Connector Status

```bash
KAFKA_CONNECT_POD=$(kubectl get pods -n debezium-pipeline -l app=kafka-connect -o jsonpath='{.items[0].metadata.name}')

kubectl exec -n debezium-pipeline $KAFKA_CONNECT_POD -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector/status | jq .
```

Expected output:
```json
{
  "name": "postgres-source-connector",
  "connector": {
    "state": "RUNNING",
    "worker_id": "kafka-connect-xxx:8083"
  },
  "tasks": [
    {
      "id": 0,
      "state": "RUNNING",
      "worker_id": "kafka-connect-xxx:8083"
    }
  ]
}
```

### 3. Verify Kafka Topics

```bash
KAFKA_POD=$(kubectl get pods -n debezium-pipeline -l app=kafka -o jsonpath='{.items[0].metadata.name}')

kubectl exec -n debezium-pipeline $KAFKA_POD -- \
  kafka-topics --bootstrap-server localhost:9092 --list | grep dbserver1
```

Expected output:
```
dbserver1.public.customers
```

### 4. Check Data in PostgreSQL

```bash
POSTGRES_POD=$(kubectl get pods -n debezium-pipeline -l app=postgres -o jsonpath='{.items[0].metadata.name}')

kubectl exec -n debezium-pipeline $POSTGRES_POD -- \
  psql -U postgres -d testdb -c "SELECT * FROM customers;"
```

### 5. View CDC Events in Kafka

```bash
kubectl exec -n debezium-pipeline $KAFKA_POD -- \
  kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic dbserver1.public.customers --from-beginning --max-messages 20
```

### 6. Check Ignite Consumer Logs

```bash
IGNITE_CONSUMER_POD=$(kubectl get pods -n debezium-pipeline -l app=ignite-consumer -o jsonpath='{.items[0].metadata.name}')

kubectl logs -f -n debezium-pipeline $IGNITE_CONSUMER_POD
```

### 7. Access Ignite CLI (Optional)

```bash
# Run interactive Ignite CLI
./scripts/ignite-cli.sh

# Once in the CLI, connect to the cluster:
connect --url http://ignite-0.ignite-service:10300

# Check cluster status:
cluster status

# Run SQL queries:
sql "SELECT * FROM customers"

# Exit:
exit
```

## Testing the CDC Pipeline

### Insert Data

```bash
kubectl exec -n debezium-pipeline $POSTGRES_POD -- \
  psql -U postgres -d testdb -c \
  "INSERT INTO customers (name, email) VALUES ('Jane Doe', 'jane@example.com');"
```

### Update Data

```bash
kubectl exec -n debezium-pipeline $POSTGRES_POD -- \
  psql -U postgres -d testdb -c \
  "UPDATE customers SET name = 'Jane Smith' WHERE email = 'jane@example.com';"
```

### Delete Data

```bash
kubectl exec -n debezium-pipeline $POSTGRES_POD -- \
  psql -U postgres -d testdb -c \
  "DELETE FROM customers WHERE email = 'jane@example.com';"
```

### Watch Events Flow

Open multiple terminal windows:

**Terminal 1: PostgreSQL**
```bash
kubectl exec -it -n debezium-pipeline $POSTGRES_POD -- \
  psql -U postgres -d testdb
```

**Terminal 2: Kafka Consumer**
```bash
kubectl exec -it -n debezium-pipeline $KAFKA_POD -- \
  kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic dbserver1.public.customers --from-beginning
```

**Terminal 3: Ignite Consumer Logs**
```bash
kubectl logs -f -n debezium-pipeline $IGNITE_CONSUMER_POD
```

## Common Issues

### Issue: Ignite Consumer Can't Connect (Connection Refused on Port 10800)

This happens when the Ignite cluster hasn't been initialized yet.

**Solution:**
```bash
# Initialize the Ignite cluster
./scripts/init-ignite-cluster.sh

# Restart the ignite-consumer pod
kubectl delete pod -n debezium-pipeline -l app=ignite-consumer

# Verify cluster is active
IGNITE_POD=$(kubectl get pods -n debezium-pipeline -l app=ignite -o jsonpath='{.items[0].metadata.name}')
kubectl run curl-check --rm -i --image=curlimages/curl:latest --restart=Never -n debezium-pipeline -- \
  curl -s http://ignite-0.ignite-service:10300/management/v1/cluster/state
```

**Note:** Apache Ignite 3 requires explicit cluster initialization before accepting client connections on port 10800. The REST API (port 10300) is available immediately, but the thin client protocol (port 10800) only starts after cluster initialization.

### Issue: Ignite Pods Crashing (OOMKilled)

If Ignite pods show `CrashLoopBackOff` or `OOMKilled` status:

**Solution:**
```bash
# Check pod status
kubectl get pods -n debezium-pipeline -l app=ignite

# If OOMKilled, the memory limits may need adjustment
# The deployment already has 2Gi limit, but you can increase if needed
kubectl describe pod <ignite-pod-name> -n debezium-pipeline | grep -i oom
```

The current configuration allocates:
- Memory request: 1Gi
- Memory limit: 2Gi
- JVM heap: -Xms1g -Xmx1536m

### Issue: Pods Not Starting

```bash
# Check pod status
kubectl get pods -n debezium-pipeline

# Check pod details
kubectl describe pod <pod-name> -n debezium-pipeline

# Check logs
kubectl logs <pod-name> -n debezium-pipeline
```

### Issue: Connector Not Running

```bash
# Check connector status
kubectl exec -n debezium-pipeline $KAFKA_CONNECT_POD -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector/status

# Delete and recreate connector
kubectl exec -n debezium-pipeline $KAFKA_CONNECT_POD -- \
  curl -X DELETE http://localhost:8083/connectors/postgres-source-connector

./scripts/create-connectors.sh
```

### Issue: No Messages in Kafka

```bash
# Check if topic exists
kubectl exec -n debezium-pipeline $KAFKA_POD -- \
  kafka-topics --bootstrap-server localhost:9092 --list

# Check connector logs
kubectl logs -f -n debezium-pipeline $KAFKA_CONNECT_POD

# Verify PostgreSQL WAL level
kubectl exec -n debezium-pipeline $POSTGRES_POD -- \
  psql -U postgres -c "SHOW wal_level;"
```

### Issue: Ignite Consumer Not Processing

```bash
# Check consumer logs
kubectl logs -f -n debezium-pipeline $IGNITE_CONSUMER_POD

# Check consumer group status
kubectl exec -n debezium-pipeline $KAFKA_POD -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group ignite-consumer-group --describe
```

## Monitoring

### Start Monitoring Dashboard

```bash
./scripts/monitor.sh
```

Press Ctrl+C to exit.

### View Logs for All Components

```bash
# PostgreSQL
kubectl logs -f -n debezium-pipeline $(kubectl get pods -n debezium-pipeline -l app=postgres -o jsonpath='{.items[0].metadata.name}')

# Kafka
kubectl logs -f -n debezium-pipeline $(kubectl get pods -n debezium-pipeline -l app=kafka -o jsonpath='{.items[0].metadata.name}')

# Kafka Connect
kubectl logs -f -n debezium-pipeline $(kubectl get pods -n debezium-pipeline -l app=kafka-connect -o jsonpath='{.items[0].metadata.name}')

# Ignite
kubectl logs -f -n debezium-pipeline $(kubectl get pods -n debezium-pipeline -l app=ignite -o jsonpath='{.items[0].metadata.name}')

# Ignite Consumer
kubectl logs -f -n debezium-pipeline $(kubectl get pods -n debezium-pipeline -l app=ignite-consumer -o jsonpath='{.items[0].metadata.name}')
```

## Cleanup

### Remove All Resources

```bash
./scripts/cleanup.sh
```

### Manual Cleanup

```bash
kubectl delete namespace debezium-pipeline
```

## Next Steps

1. **Read Documentation**:
   - `ARCHITECTURE.md` - Understand the system architecture
   - `CONFIGURATION.md` - Learn about configuration options
   - `TROUBLESHOOTING.md` - Common issues and solutions

2. **Customize Configuration**:
   - Modify PostgreSQL tables
   - Add more connectors
   - Configure different Kafka topics
   - Customize Ignite cache settings

3. **Production Readiness**:
   - Use Kubernetes Secrets for credentials
   - Add persistent volumes
   - Configure resource limits
   - Enable SSL/TLS
   - Set up monitoring and alerting
   - Configure backups

4. **Extend Functionality**:
   - Add more tables to CDC
   - Implement data transformations
   - Add error handling in consumer
   - Implement retry logic
   - Add metrics and monitoring

## Useful Commands Reference

```bash
# Get all resources
kubectl get all -n debezium-pipeline

# Scale deployments
kubectl scale deployment kafka --replicas=2 -n debezium-pipeline

# Port forward to access services locally
kubectl port-forward -n debezium-pipeline svc/kafka-connect-service 8083:8083
kubectl port-forward -n debezium-pipeline svc/postgres-service 5432:5432

# Execute interactive shell
kubectl exec -it -n debezium-pipeline <pod-name> -- /bin/bash

# Copy files from pod
kubectl cp debezium-pipeline/<pod-name>:/path/to/file ./local-file

# View resource usage
kubectl top pods -n debezium-pipeline
kubectl top nodes
```

## Support

For issues or questions:
1. Check the `TROUBLESHOOTING.md` guide
2. Review logs using the commands above
3. Check Kubernetes events: `kubectl get events -n debezium-pipeline --sort-by='.lastTimestamp'`
