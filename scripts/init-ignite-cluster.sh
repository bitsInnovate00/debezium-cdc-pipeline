#!/bin/bash

# Script to initialize the Ignite 3 cluster
# This must be run after the Ignite pods are deployed and running

set -e

NAMESPACE="debezium-pipeline"

echo "========================================="
echo "Initializing Ignite 3 Cluster"
echo "========================================="

# Wait for Ignite pods to be ready
echo "Waiting for Ignite pods to be ready..."
kubectl wait --for=condition=ready pod -l app=ignite -n $NAMESPACE --timeout=120s

# Get the physical topology to find node names
echo ""
echo "Fetching cluster topology..."
TOPOLOGY=$(kubectl run curl-temp --rm -i --quiet --image=curlimages/curl:latest --restart=Never -n $NAMESPACE -- \
  curl -s http://ignite-0.ignite-service:10300/management/v1/cluster/topology/physical 2>/dev/null)

echo "Topology: $TOPOLOGY"

# Extract the first node name from the topology
NODE_NAME=$(echo "$TOPOLOGY" | grep -o '"name":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$NODE_NAME" ]; then
  echo "ERROR: Could not determine node name from topology"
  exit 1
fi

echo "Found node: $NODE_NAME"
echo ""

# Check if cluster is already initialized
echo "Checking if cluster is already initialized..."
CLUSTER_STATE=$(kubectl run curl-check --rm -i --quiet --image=curlimages/curl:latest --restart=Never -n $NAMESPACE -- \
  curl -s http://ignite-0.ignite-service:10300/management/v1/cluster/state 2>/dev/null || echo "")

if echo "$CLUSTER_STATE" | grep -q "ACTIVE"; then
  echo "Cluster is already initialized and active."
  exit 0
fi

# Initialize the cluster using REST API
echo "Initializing cluster with node: $NODE_NAME"
kubectl run curl-init --rm -i --quiet --image=curlimages/curl:latest --restart=Never -n $NAMESPACE -- \
  curl -X POST http://ignite-0.ignite-service:10300/management/v1/cluster/init \
  -H "Content-Type: application/json" \
  -d "{\"metaStorageNodes\":[\"$NODE_NAME\"],\"cmgNodes\":[\"$NODE_NAME\"],\"clusterName\":\"ignite-cluster\"}" 2>/dev/null

echo ""
echo "Waiting for cluster to become active..."
sleep 5

# Verify the cluster is initialized
echo "Verifying cluster status..."
FINAL_STATE=$(kubectl run curl-verify --rm -i --quiet --image=curlimages/curl:latest --restart=Never -n $NAMESPACE -- \
  curl -s http://ignite-0.ignite-service:10300/management/v1/cluster/state 2>/dev/null)

echo "Cluster state: $FINAL_STATE"

echo ""
echo "========================================="
echo "Cluster initialization complete!"
echo "========================================="
echo ""
echo "Client port 10800 is now available."
echo "The ignite-consumer can now connect to the cluster."
