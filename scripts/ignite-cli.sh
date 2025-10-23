#!/bin/bash

# Script to run Ignite 3 CLI in Kubernetes and connect to the cluster
# This creates an ephemeral pod for interactive CLI sessions

set -e

NAMESPACE="debezium-pipeline"
POD_NAME="ignite-cli-$(date +%s)"
CLUSTER_NAME="debezium-ignite-cluster"
SERVICE_NAME="ignite-service"
REST_PORT="10300"

echo "Checking Ignite cluster status..."

# Check if Ignite pods are running
IGNITE_PODS=$(kubectl get pods -n $NAMESPACE -l app=ignite --field-selector=status.phase=Running -o name 2>/dev/null || echo "")
if [ -z "$IGNITE_PODS" ]; then
    echo "ERROR: No running Ignite pods found in namespace $NAMESPACE"
    echo "Please ensure the Ignite cluster is deployed and running."
    exit 1
fi

echo "Found running Ignite pods:"
kubectl get pods -n $NAMESPACE -l app=ignite -o wide

# Check if Ignite service exists
kubectl get service $SERVICE_NAME -n $NAMESPACE > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "ERROR: Ignite service '$SERVICE_NAME' not found in namespace $NAMESPACE"
    exit 1
fi

echo "Ignite service '$SERVICE_NAME' is available"

# Get cluster endpoint
CLUSTER_ENDPOINT="http://${SERVICE_NAME}.${NAMESPACE}.svc.cluster.local:10300"
echo "Cluster endpoint: $CLUSTER_ENDPOINT"

echo "Starting Ignite 3 CLI pod with cluster connection..."

# For Ignite 3, we need to connect after starting the CLI
echo "The CLI will start and you can connect using: connect $CLUSTER_ENDPOINT"
echo "Note: Ignite 3 CLI connects via REST API on port 10300"
echo ""

kubectl run $POD_NAME \
  -n $NAMESPACE \
  --rm -it \
  --image=apacheignite/ignite:3.0.0 \
  --restart=Never \
  --env="LANG=C.UTF-8" \
  --env="LC_ALL=C.UTF-8" \
  -- cli

echo "CLI session ended."