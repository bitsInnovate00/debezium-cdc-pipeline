#!/bin/bash

echo "==========================================="
echo "Cleaning up Debezium CDC Pipeline"
echo "==========================================="

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}This will delete all resources in the debezium-pipeline namespace.${NC}"
read -p "Are you sure you want to continue? (y/N) " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${BLUE}Cleanup cancelled.${NC}"
    exit 0
fi

echo -e "\n${RED}Deleting all resources...${NC}"

# Delete Ignite Consumer
echo -e "${BLUE}Deleting Ignite Consumer...${NC}"
kubectl delete -f kubernetes/ignite-consumer/ --ignore-not-found=true

# Delete Ignite
echo -e "${BLUE}Deleting Apache Ignite...${NC}"
kubectl delete -f kubernetes/ignite/ --ignore-not-found=true

# Delete Kafka Connect
echo -e "${BLUE}Deleting Kafka Connect...${NC}"
kubectl delete -f kubernetes/kafka-connect/ --ignore-not-found=true

# Delete Kafka
echo -e "${BLUE}Deleting Kafka...${NC}"
kubectl delete -f kubernetes/kafka/ --ignore-not-found=true

# Delete PostgreSQL
echo -e "${BLUE}Deleting PostgreSQL...${NC}"
kubectl delete -f kubernetes/postgres/ --ignore-not-found=true

# Delete namespace (this will delete everything)
echo -e "${BLUE}Deleting namespace...${NC}"
kubectl delete namespace debezium-pipeline --ignore-not-found=true

echo -e "\n${GREEN}==========================================="
echo "✓ Cleanup completed!"
echo "===========================================${NC}"

echo -e "\n${BLUE}Note: Persistent volumes (if any) may need to be manually deleted.${NC}"
echo "To list PVs: kubectl get pv"
echo "To delete a PV: kubectl delete pv <pv-name>"
