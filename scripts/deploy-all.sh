#!/bin/bash

set -e

echo "==========================================="
echo "Deploying Debezium CDC Pipeline"
echo "==========================================="

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to wait for pods
wait_for_pods() {
    local namespace=$1
    local label=$2
    local replicas=$3
    
    echo -e "${BLUE}Waiting for $label pods to be ready...${NC}"
    kubectl wait --for=condition=ready pod -l app=$label -n $namespace --timeout=300s || true
    echo -e "${GREEN}✓ $label is ready${NC}"
}

# Step 1: Create namespace
echo -e "\n${BLUE}Step 1: Creating namespace...${NC}"
kubectl apply -f kubernetes/namespace.yaml
echo -e "${GREEN}✓ Namespace created${NC}"

# Step 2: Deploy PostgreSQL
echo -e "\n${BLUE}Step 2: Deploying PostgreSQL...${NC}"
kubectl apply -f kubernetes/postgres/postgres-configmap.yaml
kubectl apply -f kubernetes/postgres/postgres-deployment.yaml
kubectl apply -f kubernetes/postgres/postgres-service.yaml
wait_for_pods "debezium-pipeline" "postgres" 1

# Step 3: Deploy Zookeeper
echo -e "\n${BLUE}Step 3: Deploying Zookeeper...${NC}"
kubectl apply -f kubernetes/kafka/zookeeper-deployment.yaml
kubectl apply -f kubernetes/kafka/zookeeper-service.yaml
wait_for_pods "debezium-pipeline" "zookeeper" 1

# Step 4: Deploy Kafka
echo -e "\n${BLUE}Step 4: Deploying Kafka...${NC}"
kubectl apply -f kubernetes/kafka/kafka-deployment.yaml
kubectl apply -f kubernetes/kafka/kafka-service.yaml
wait_for_pods "debezium-pipeline" "kafka" 1

# Wait a bit for Kafka to fully initialize
echo -e "${BLUE}Waiting for Kafka to fully initialize...${NC}"
sleep 20

# Step 5: Deploy Kafka Connect
echo -e "\n${BLUE}Step 5: Deploying Kafka Connect with Debezium...${NC}"
kubectl apply -f kubernetes/kafka-connect/kafka-connect-deployment.yaml
kubectl apply -f kubernetes/kafka-connect/kafka-connect-service.yaml
wait_for_pods "debezium-pipeline" "kafka-connect" 1

# Wait for Kafka Connect to be fully ready
echo -e "${BLUE}Waiting for Kafka Connect to fully initialize...${NC}"
sleep 30

# Step 6: Deploy Apache Ignite 3
echo -e "\n${BLUE}Step 6: Deploying Apache Ignite 3...${NC}"
kubectl apply -f kubernetes/ignite/ignite-config.yaml
kubectl apply -f kubernetes/ignite/ignite-deployment.yaml
kubectl apply -f kubernetes/ignite/ignite-service.yaml
wait_for_pods "debezium-pipeline" "ignite" 2

# Wait for Ignite to fully initialize
echo -e "${BLUE}Waiting for Ignite to fully initialize...${NC}"
sleep 20

# Initialize Ignite cluster
echo -e "\n${BLUE}Step 6b: Initializing Ignite cluster...${NC}"
./scripts/init-ignite-cluster.sh
echo -e "${GREEN}✓ Ignite cluster initialized${NC}"

# Step 7: Build and deploy Ignite Consumer
echo -e "\n${BLUE}Step 7: Building and deploying Ignite Consumer...${NC}"

# Check if running on minikube
if command -v minikube &> /dev/null && minikube status &> /dev/null; then
    echo -e "${BLUE}Detected minikube, using minikube docker environment...${NC}"
    eval $(minikube docker-env)
fi

# Build the Docker image
echo -e "${BLUE}Building Ignite Consumer Docker image...${NC}"
docker build -t ignite-consumer:1.0 ./ignite-consumer

# Deploy the consumer
kubectl apply -f kubernetes/ignite-consumer/ignite-consumer-deployment.yaml
wait_for_pods "debezium-pipeline" "ignite-consumer" 1

echo -e "\n${GREEN}==========================================="
echo "✓ All components deployed successfully!"
echo "===========================================${NC}"

echo -e "\n${BLUE}To view all pods:${NC}"
echo "kubectl get pods -n debezium-pipeline"

echo -e "\n${BLUE}To create Debezium connector, run:${NC}"
echo "./scripts/create-connectors.sh"

echo -e "\n${BLUE}To view logs:${NC}"
echo "kubectl logs -f <pod-name> -n debezium-pipeline"

echo -e "\n${BLUE}To access PostgreSQL:${NC}"
echo "kubectl exec -it -n debezium-pipeline \$(kubectl get pods -n debezium-pipeline -l app=postgres -o jsonpath='{.items[0].metadata.name}') -- psql -U postgres -d testdb"
