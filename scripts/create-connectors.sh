#!/bin/bash

set -e

echo "==========================================="
echo "Creating Debezium Connectors"
echo "==========================================="

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

# Get Kafka Connect pod name
KAFKA_CONNECT_POD=$(kubectl get pods -n debezium-pipeline -l app=kafka-connect -o jsonpath='{.items[0].metadata.name}')

if [ -z "$KAFKA_CONNECT_POD" ]; then
    echo -e "${RED}Error: Kafka Connect pod not found${NC}"
    exit 1
fi

echo -e "${BLUE}Using Kafka Connect pod: $KAFKA_CONNECT_POD${NC}"

# Wait for Kafka Connect to be ready
echo -e "${BLUE}Checking Kafka Connect status...${NC}"
for i in {1..30}; do
    STATUS=$(kubectl exec -n debezium-pipeline $KAFKA_CONNECT_POD -- curl -s http://localhost:8083/ | grep -o "version" || true)
    if [ ! -z "$STATUS" ]; then
        echo -e "${GREEN}✓ Kafka Connect is ready${NC}"
        break
    fi
    echo "Waiting for Kafka Connect to be ready... ($i/30)"
    sleep 5
done

# Create Postgres Source Connector
echo -e "\n${BLUE}Creating Postgres Source Connector...${NC}"

kubectl exec -n debezium-pipeline $KAFKA_CONNECT_POD -- \
    curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
    http://localhost:8083/connectors/ \
    -d @- << EOF
{
  "name": "postgres-source-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "tasks.max": "1",
    "database.hostname": "postgres-service",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "testdb",
    "database.server.name": "dbserver1",
    "table.include.list": "public.customers",
    "plugin.name": "pgoutput",
    "publication.autocreate.mode": "filtered",
    "slot.name": "debezium_slot",
    "topic.prefix": "dbserver1",
    "schema.history.internal.kafka.bootstrap.servers": "kafka-service:9092",
    "schema.history.internal.kafka.topic": "schema-changes.testdb",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "true",
    "value.converter.schemas.enable": "true",
    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false",
    "transforms.unwrap.delete.handling.mode": "rewrite",
    "snapshot.mode": "initial",
    "time.precision.mode": "adaptive",
    "decimal.handling.mode": "double"
  }
}
EOF

echo -e "\n${GREEN}✓ Postgres Source Connector created${NC}"

# Wait a bit for connector to initialize
sleep 5

# Check connector status
echo -e "\n${BLUE}Checking connector status...${NC}"
kubectl exec -n debezium-pipeline $KAFKA_CONNECT_POD -- \
    curl -s http://localhost:8083/connectors/postgres-source-connector/status | jq .

echo -e "\n${GREEN}==========================================="
echo "✓ Connectors created successfully!"
echo "===========================================${NC}"

echo -e "\n${BLUE}To check connector status:${NC}"
echo "kubectl exec -n debezium-pipeline $KAFKA_CONNECT_POD -- curl -s http://localhost:8083/connectors/postgres-source-connector/status | jq ."

echo -e "\n${BLUE}To list Kafka topics:${NC}"
echo "kubectl exec -n debezium-pipeline \$(kubectl get pods -n debezium-pipeline -l app=kafka -o jsonpath='{.items[0].metadata.name}') -- kafka-topics.sh --bootstrap-server localhost:9092 --list"

echo -e "\n${BLUE}To consume messages from Kafka:${NC}"
echo "kubectl exec -n debezium-pipeline \$(kubectl get pods -n debezium-pipeline -l app=kafka -o jsonpath='{.items[0].metadata.name}') -- kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic dbserver1.public.customers --from-beginning"
