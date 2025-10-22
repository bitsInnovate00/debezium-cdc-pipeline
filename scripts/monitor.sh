#!/bin/bash

echo "==========================================="
echo "Monitoring Debezium CDC Pipeline"
echo "==========================================="

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

while true; do
    clear
    echo -e "${BLUE}=== Pod Status ===${NC}"
    kubectl get pods -n debezium-pipeline -o wide
    
    echo -e "\n${BLUE}=== Services ===${NC}"
    kubectl get svc -n debezium-pipeline
    
    echo -e "\n${BLUE}=== Kafka Connect Status ===${NC}"
    KAFKA_CONNECT_POD=$(kubectl get pods -n debezium-pipeline -l app=kafka-connect -o jsonpath='{.items[0].metadata.name}')
    if [ ! -z "$KAFKA_CONNECT_POD" ]; then
        kubectl exec -n debezium-pipeline $KAFKA_CONNECT_POD -- \
            curl -s http://localhost:8083/connectors 2>/dev/null | jq . || echo "Kafka Connect not ready"
    fi
    
    echo -e "\n${BLUE}=== Recent Events ===${NC}"
    echo -e "${YELLOW}Press Ctrl+C to exit monitoring${NC}"
    
    sleep 10
done
