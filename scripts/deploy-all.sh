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

# Function to detect Kubernetes cluster type and configure Docker environment
configure_docker_environment() {
    local current_context=$(kubectl config current-context 2>/dev/null || echo "")
    
    echo -e "${BLUE}Detecting Kubernetes cluster type...${NC}"
    echo "Current context: $current_context"
    
    # Check for minikube using current context
    if command -v minikube &> /dev/null; then
        # Use current context as the profile name
        local profile_name="$current_context"
        
        # Check if this profile exists and is running
        if minikube -p "$profile_name" status &> /dev/null 2>&1; then
            echo -e "${GREEN}✓ Detected minikube profile: $profile_name${NC}"
            echo -e "${BLUE}Configuring Docker environment for minikube profile '$profile_name'...${NC}"
            eval $(minikube -p "$profile_name" docker-env)
            if [ $? -eq 0 ]; then
                echo -e "${GREEN}✓ Successfully configured minikube Docker environment${NC}"
                return 0
            else
                echo -e "\033[0;31m✗ Failed to configure minikube Docker environment${NC}"
                return 1
            fi
        fi
        
        # Fallback: try default minikube profile (if context is "minikube")
        if [[ "$current_context" == "minikube" ]] && minikube status &> /dev/null; then
            echo -e "${GREEN}✓ Detected default minikube cluster${NC}"
            echo -e "${BLUE}Configuring Docker environment for default minikube...${NC}"
            eval $(minikube docker-env)
            if [ $? -eq 0 ]; then
                echo -e "${GREEN}✓ Successfully configured minikube Docker environment${NC}"
                return 0
            fi
        fi
    fi
    
    # Check for kind
    if command -v kind &> /dev/null && [[ "$current_context" == *"kind"* ]]; then
        echo -e "${GREEN}✓ Detected kind cluster${NC}"
        echo -e "\033[0;33m⚠ For kind, image needs to be loaded manually after build${NC}"
        echo -e "${BLUE}Using local Docker environment...${NC}"
        return 0
    fi
    
    # Check for Docker Desktop Kubernetes
    if [[ "$current_context" == "docker-desktop" ]] || [[ "$current_context" == "docker-for-desktop" ]]; then
        echo -e "${GREEN}✓ Detected Docker Desktop Kubernetes${NC}"
        echo -e "${BLUE}Using local Docker environment...${NC}"
        return 0
    fi
    
    # Check for other local clusters
    if [[ "$current_context" == *"local"* ]] || [[ "$current_context" == *"dev"* ]]; then
        echo -e "\033[0;33m⚠ Detected possible local cluster: $current_context${NC}"
        echo -e "${BLUE}Using local Docker environment...${NC}"
        return 0
    fi
    
    # Default case - remote or managed cluster
    echo -e "\033[0;31m✗ Remote or managed cluster detected: $current_context${NC}"
    echo -e "\033[0;33m⚠ You may need to push the image to a registry accessible by your cluster${NC}"
    echo -e "${BLUE}Using local Docker environment for now...${NC}"
    return 0
}

# Configure Docker environment based on cluster type
configure_docker_environment

# Build the Docker image
echo -e "${BLUE}Building Ignite Consumer Docker image...${NC}"
docker build -t ignite-consumer:1.0 ./ignite-consumer

# Handle post-build steps for specific cluster types
post_build_actions() {
    local current_context=$(kubectl config current-context 2>/dev/null || echo "")
    
    # For kind clusters, load the image
    if command -v kind &> /dev/null && [[ "$current_context" == *"kind"* ]]; then
        echo -e "${BLUE}Loading image into kind cluster...${NC}"
        local cluster_name=$(echo "$current_context" | sed 's/kind-//')
        kind load docker-image ignite-consumer:1.0 --name "$cluster_name"
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ Image loaded into kind cluster${NC}"
        else
            echo -e "\033[0;31m✗ Failed to load image into kind cluster${NC}"
        fi
    fi
}

post_build_actions

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
