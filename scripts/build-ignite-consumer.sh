#!/bin/bash

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "==========================================="
echo "Building Ignite Consumer Docker Image"
echo "==========================================="

# Function to detect Kubernetes cluster type and configure Docker environment
configure_docker_environment() {
    local current_context=$(kubectl config current-context 2>/dev/null || echo "")
    
    echo -e "${BLUE}Detecting Kubernetes cluster type...${NC}"
    echo "Current context: $current_context"
    
    # Check for minikube
    if command -v minikube &> /dev/null; then
        echo -e "${BLUE}Checking if current context is a minikube profile...${NC}"
        
        # Use current context as the profile name - much simpler!
        local profile_name="$current_context"
        
        # Check if this profile exists and is running
        if minikube -p "$profile_name" status &> /dev/null 2>&1; then
            echo -e "${GREEN}✓ Detected minikube profile: $profile_name${NC}"
            echo -e "${BLUE}Configuring Docker environment for minikube profile '$profile_name'...${NC}"
            
            eval $(minikube -p "$profile_name" docker-env)
            if [ $? -eq 0 ]; then
                echo -e "${GREEN}✓ Successfully configured minikube Docker environment${NC}"
                echo -e "${BLUE}Docker daemon: minikube ($profile_name)${NC}"
                export CLUSTER_TYPE="minikube"
                return 0
            else
                echo -e "${RED}✗ Failed to configure minikube Docker environment${NC}"
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
                export CLUSTER_TYPE="minikube"
                return 0
            fi
        fi
        
        echo -e "${YELLOW}⚠ Current context '$current_context' is not a running minikube profile${NC}"
    fi
    
    # Check for kind
    if command -v kind &> /dev/null && [[ "$current_context" == *"kind"* ]]; then
        echo -e "${GREEN}✓ Detected kind cluster${NC}"
        echo -e "${YELLOW}⚠ For kind, image will be loaded into cluster after build${NC}"
        echo -e "${BLUE}Using local Docker environment...${NC}"
        export CLUSTER_TYPE="kind"
        return 0
    fi
    
    # Check for Docker Desktop Kubernetes
    if [[ "$current_context" == "docker-desktop" ]] || [[ "$current_context" == "docker-for-desktop" ]]; then
        echo -e "${GREEN}✓ Detected Docker Desktop Kubernetes${NC}"
        echo -e "${BLUE}Using local Docker environment...${NC}"
        export CLUSTER_TYPE="docker-desktop"
        return 0
    fi
    
    # Check for other local clusters
    if [[ "$current_context" == *"local"* ]] || [[ "$current_context" == *"dev"* ]]; then
        echo -e "${YELLOW}⚠ Detected possible local cluster: $current_context${NC}"
        echo -e "${BLUE}Using local Docker environment...${NC}"
        export CLUSTER_TYPE="local"
        return 0
    fi
    
    # Default case - remote or managed cluster
    echo -e "${RED}✗ Remote or managed cluster detected: $current_context${NC}"
    echo -e "${YELLOW}⚠ You may need to push the image to a registry accessible by your cluster${NC}"
    echo -e "${BLUE}Using local Docker environment for now...${NC}"
    export CLUSTER_TYPE="remote"
    return 0
}

# Function to handle post-build actions
post_build_actions() {
    local current_context=$(kubectl config current-context 2>/dev/null || echo "")
    
    # For kind clusters, load the image
    if [[ "$CLUSTER_TYPE" == "kind" ]] && command -v kind &> /dev/null; then
        echo -e "${BLUE}Loading image into kind cluster...${NC}"
        local cluster_name=$(echo "$current_context" | sed 's/kind-//')
        kind load docker-image ignite-consumer:1.0 --name "$cluster_name"
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ Image loaded into kind cluster${NC}"
        else
            echo -e "${RED}✗ Failed to load image into kind cluster${NC}"
            return 1
        fi
    fi
    
    # For remote clusters, show push instructions
    if [[ "$CLUSTER_TYPE" == "remote" ]]; then
        echo -e "${YELLOW}===========================================${NC}"
        echo -e "${YELLOW}⚠ REMOTE CLUSTER DETECTED${NC}"
        echo -e "${YELLOW}===========================================${NC}"
        echo -e "${BLUE}To use this image with your remote cluster, you need to:${NC}"
        echo -e "1. Tag the image for your registry:"
        echo -e "   ${GREEN}docker tag ignite-consumer:1.0 <your-registry>/ignite-consumer:1.0${NC}"
        echo -e "2. Push the image to your registry:"
        echo -e "   ${GREEN}docker push <your-registry>/ignite-consumer:1.0${NC}"
        echo -e "3. Update the deployment YAML to use the registry image"
        echo -e "${YELLOW}===========================================${NC}"
    fi
}

# Function to verify Docker is available
verify_docker() {
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}✗ Docker is not installed or not in PATH${NC}"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        echo -e "${RED}✗ Docker daemon is not running${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Docker is available${NC}"
}

# Function to check if ignite-consumer directory exists
verify_source() {
    if [ ! -d "./ignite-consumer" ]; then
        echo -e "${RED}✗ ignite-consumer directory not found${NC}"
        echo -e "${BLUE}Please run this script from the project root directory${NC}"
        exit 1
    fi
    
    if [ ! -f "./ignite-consumer/Dockerfile" ]; then
        echo -e "${RED}✗ Dockerfile not found in ignite-consumer directory${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Source files found${NC}"
}

# Main execution
main() {
    echo -e "${BLUE}Verifying prerequisites...${NC}"
    verify_docker
    verify_source
    
    echo -e "\n${BLUE}Configuring Docker environment...${NC}"
    configure_docker_environment
    
    echo -e "\n${BLUE}Building Ignite Consumer Docker image...${NC}"
    cd ./ignite-consumer
    docker build -t ignite-consumer:1.0 .
    cd ..
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Docker image built successfully${NC}"
        
        # Show image details
        echo -e "\n${BLUE}Image details:${NC}"
        docker images ignite-consumer:1.0
        
        echo -e "\n${BLUE}Handling post-build actions...${NC}"
        post_build_actions
        
        echo -e "\n${GREEN}==========================================="
        echo "✓ Build completed successfully!"
        echo "===========================================${NC}"
        
        echo -e "\n${BLUE}To deploy the consumer:${NC}"
        echo "kubectl apply -f kubernetes/ignite-consumer/ignite-consumer-deployment.yaml"
        
        echo -e "\n${BLUE}To restart existing deployment:${NC}"
        echo "kubectl rollout restart deployment ignite-consumer -n debezium-pipeline"
        
    else
        echo -e "${RED}✗ Docker image build failed${NC}"
        exit 1
    fi
}

# Check if script is being run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi