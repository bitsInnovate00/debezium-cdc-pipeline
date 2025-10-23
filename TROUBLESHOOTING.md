# Debezium CDC Pipeline - Troubleshooting Guide

## Common Issues and Solutions

### 1. PostgreSQL Pod Not Starting

**Symptoms:**
- PostgreSQL pod in CrashLoopBackOff or Error state

**Solutions:**
```bash
# Check pod logs
kubectl logs -n debezium-pipeline <postgres-pod-name>

# Check pod description
kubectl describe pod -n debezium-pipeline <postgres-pod-name>

# Common issues:
# - Insufficient resources: Check cluster resources
# - ConfigMap not found: Ensure postgres-configmap.yaml is applied
```

### 2. Kafka Connect Cannot Connect to Kafka

**Symptoms:**
- Kafka Connect logs show connection errors
- Connectors fail to start

**Solutions:**
```bash
# Check Kafka pod status
kubectl get pods -n debezium-pipeline -l app=kafka

# Check Kafka logs
kubectl logs -n debezium-pipeline <kafka-pod-name>

# Verify Kafka service
kubectl get svc -n debezium-pipeline kafka-service

# Test connectivity from Kafka Connect pod
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  nc -zv kafka-service 9092
```

### 3. Debezium Connector Fails to Start

**Symptoms:**
- Connector status shows FAILED state
- No topics are created

**Solutions:**
```bash
# Check connector status
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector/status | jq .

# Check connector configuration
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector | jq .

# Delete and recreate connector
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  curl -X DELETE http://localhost:8083/connectors/postgres-source-connector

# Then run create-connectors.sh again
./scripts/create-connectors.sh
```

**Common Connector Issues:**

#### PostgreSQL Permissions
```sql
-- Connect to PostgreSQL
kubectl exec -it -n debezium-pipeline <postgres-pod> -- psql -U postgres -d testdb

-- Check replication slot
SELECT * FROM pg_replication_slots;

-- Drop stuck slot if needed
SELECT pg_drop_replication_slot('debezium_slot');

-- Check publication
SELECT * FROM pg_publication;
```

#### WAL Level Configuration
```bash
# Check WAL level
kubectl exec -n debezium-pipeline <postgres-pod> -- \
  psql -U postgres -c "SHOW wal_level;"

# Should return: logical
```

### 4. Ignite Pods Not Starting

**Symptoms:**
- Ignite pods stuck in Pending or CrashLoopBackOff

**Solutions:**
```bash
# Check pod status
kubectl describe pod -n debezium-pipeline <ignite-pod-name>

# Check logs
kubectl logs -n debezium-pipeline <ignite-pod-name>

# Common issues:
# - PVC not bound: Check if PersistentVolumeClaim is bound
kubectl get pvc -n debezium-pipeline

# For testing, you can use emptyDir instead of PVC
# Edit ignite-deployment.yaml and change volumeClaimTemplates to emptyDir
```

### 5. Ignite Consumer Not Processing Messages

**Symptoms:**
- Consumer pod running but not processing CDC events
- No logs showing message processing

**Solutions:**
```bash
# Check consumer logs
kubectl logs -f -n debezium-pipeline <ignite-consumer-pod>

# Verify Kafka topics exist
kubectl exec -n debezium-pipeline <kafka-pod> -- \
  kafka-topics --bootstrap-server localhost:9092 --list

# Manually consume from Kafka to verify messages
kubectl exec -n debezium-pipeline <kafka-pod> -- \
  kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic dbserver1.public.customers --from-beginning --max-messages 10

# Check consumer group
kubectl exec -n debezium-pipeline <kafka-pod> -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group ignite-consumer-group --describe
```

### 6. No CDC Events Being Generated

**Symptoms:**
- Data changes in PostgreSQL but no Kafka messages

**Solutions:**
```bash
# Verify connector is running
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector/status

# Check if topic was created
kubectl exec -n debezium-pipeline <kafka-pod> -- \
  kafka-topics --bootstrap-server localhost:9092 --list | grep dbserver1

# Check Kafka Connect logs
kubectl logs -f -n debezium-pipeline <kafka-connect-pod>

# Verify PostgreSQL table is being monitored
# The table should be in the table.include.list configuration
```

### 7. Out of Memory Errors

**Symptoms:**
- Pods getting OOMKilled
- Java heap space errors in logs

**Solutions:**
```bash
# Increase resource limits in deployment files
# Edit the deployment YAML files and adjust:
resources:
  requests:
    memory: "1Gi"  # Increase from default
  limits:
    memory: "2Gi"  # Increase from default

# Reapply the deployment
kubectl apply -f <deployment-file.yaml>
```

### 8. Connectivity Between Services

**Test connectivity:**
```bash
# From Kafka Connect to PostgreSQL
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  nc -zv postgres-service 5432

# From Kafka Connect to Kafka
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  nc -zv kafka-service 9092

# From Ignite Consumer to Kafka
kubectl exec -n debezium-pipeline <ignite-consumer-pod> -- \
  nc -zv kafka-service 9092

# From Ignite Consumer to Ignite
kubectl exec -n debezium-pipeline <ignite-consumer-pod> -- \
  nc -zv ignite-service 10800
```

## Useful Commands

### View All Resources
```bash
kubectl get all -n debezium-pipeline
```

### Get Pod Logs
```bash
# Follow logs
kubectl logs -f -n debezium-pipeline <pod-name>

# Last 100 lines
kubectl logs --tail=100 -n debezium-pipeline <pod-name>

# Previous container (if pod restarted)
kubectl logs -p -n debezium-pipeline <pod-name>
```

### Execute Commands in Pods
```bash
# PostgreSQL
kubectl exec -it -n debezium-pipeline <postgres-pod> -- psql -U postgres -d testdb

# Kafka
kubectl exec -it -n debezium-pipeline <kafka-pod> -- bash

# Kafka Connect
kubectl exec -it -n debezium-pipeline <kafka-connect-pod> -- bash
```

### Restart a Deployment
```bash
kubectl rollout restart deployment/<deployment-name> -n debezium-pipeline
```

### Delete and Redeploy
```bash
kubectl delete -f <yaml-file>
kubectl apply -f <yaml-file>
```

## Performance Tuning

### Kafka
- Adjust `KAFKA_LOG_RETENTION_HOURS` for log retention
- Increase `KAFKA_LOG_SEGMENT_BYTES` for larger segments
- Add more Kafka brokers for scalability

### Debezium
- Adjust `tasks.max` for parallel processing
- Configure `snapshot.mode` based on requirements
- Use `schema.history.internal.kafka.bootstrap.servers` for distributed schema history

### Ignite
- Increase replicas for high availability
- Configure proper backup count
- Adjust heap size with JAVA_OPTS

## Monitoring and Metrics

### Check Connector Metrics
```bash
kubectl exec -n debezium-pipeline <kafka-connect-pod> -- \
  curl -s http://localhost:8083/connectors/postgres-source-connector/status
```

### View Kafka Consumer Lag
```bash
kubectl exec -n debezium-pipeline <kafka-pod> -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group ignite-consumer-group --describe
```

### PostgreSQL Replication Stats
```bash
kubectl exec -n debezium-pipeline <postgres-pod> -- \
  psql -U postgres -c "SELECT * FROM pg_stat_replication;"
```

## Clean Slate Recovery

If everything is broken and you want to start fresh:

```bash
# Full cleanup
./scripts/cleanup.sh

# Wait for namespace deletion
kubectl get namespace debezium-pipeline

# Redeploy everything
./scripts/deploy-all.sh
./scripts/create-connectors.sh
```
