#!/bin/bash

echo "==========================================="
echo "Testing Debezium CDC Pipeline"
echo "==========================================="

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Get pod names
POSTGRES_POD=$(kubectl get pods -n debezium-pipeline -l app=postgres -o jsonpath='{.items[0].metadata.name}')
KAFKA_POD=$(kubectl get pods -n debezium-pipeline -l app=kafka -o jsonpath='{.items[0].metadata.name}')
IGNITE_POD=$(kubectl get pods -n debezium-pipeline -l app=ignite -o jsonpath='{.items[0].metadata.name}')

echo -e "${BLUE}Step 1: Inserting test data into PostgreSQL...${NC}"
kubectl exec -n debezium-pipeline $POSTGRES_POD -- psql -U postgres -d testdb -c \
    "INSERT INTO customers (name, email) VALUES ('Test User', 'test@example.com');"

echo -e "${GREEN}✓ Data inserted${NC}"

echo -e "\n${BLUE}Step 2: Checking PostgreSQL data...${NC}"
kubectl exec -n debezium-pipeline $POSTGRES_POD -- psql -U postgres -d testdb -c \
    "SELECT * FROM customers ORDER BY id DESC LIMIT 5;"

echo -e "\n${BLUE}Step 3: Listing Kafka topics...${NC}"
kubectl exec -n debezium-pipeline $KAFKA_POD -- \
    kafka-topics --bootstrap-server localhost:9092 --list | grep dbserver1 || echo "No Debezium topics found yet"

echo -e "\n${BLUE}Step 4: Consuming last 5 messages from Kafka (waiting 10 seconds)...${NC}"
timeout 10 kubectl exec -n debezium-pipeline $KAFKA_POD -- \
    kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic dbserver1.public.customers --from-beginning --max-messages 5 2>/dev/null || echo -e "${YELLOW}No messages consumed (may still be processing)${NC}"

echo -e "\n${BLUE}Step 5: Checking Ignite Consumer logs for CDC events...${NC}"
IGNITE_CONSUMER_POD=$(kubectl get pods -n debezium-pipeline -l app=ignite-consumer -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ ! -z "$IGNITE_CONSUMER_POD" ]; then
    kubectl logs -n debezium-pipeline $IGNITE_CONSUMER_POD --tail=10 2>/dev/null || echo "Ignite Consumer logs not available"
else
    echo "Ignite Consumer pod not found"
fi

echo -e "\n${YELLOW}Step 6: Update a record in PostgreSQL...${NC}"
kubectl exec -n debezium-pipeline $POSTGRES_POD -- psql -U postgres -d testdb -c \
    "UPDATE customers SET name = 'Updated User' WHERE email = 'test@example.com';"

echo -e "${GREEN}✓ Record updated${NC}"

echo -e "\n${YELLOW}Step 7: Delete a record from PostgreSQL...${NC}"
kubectl exec -n debezium-pipeline $POSTGRES_POD -- psql -U postgres -d testdb -c \
    "DELETE FROM customers WHERE email = 'test@example.com';"

echo -e "${GREEN}✓ Record deleted${NC}"

echo -e "\n${GREEN}==========================================="
echo "✓ Test completed!"
echo "===========================================${NC}"

echo -e "\n${BLUE}Check the Ignite Consumer logs to see CDC events:${NC}"
echo "kubectl logs -f -n debezium-pipeline \$(kubectl get pods -n debezium-pipeline -l app=ignite-consumer -o jsonpath='{.items[0].metadata.name}')"
