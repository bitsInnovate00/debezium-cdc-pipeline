#!/bin/bash

# Script to run Ignite 3 CLI in Kubernetes
# This creates an ephemeral pod for interactive CLI sessions

set -e

NAMESPACE="debezium-pipeline"
POD_NAME="ignite-cli-$(date +%s)"

echo "Starting Ignite 3 CLI pod..."

kubectl run $POD_NAME \
  -n $NAMESPACE \
  --rm -it \
  --image=apacheignite/ignite:3.0.0 \
  --restart=Never \
  --env="LANG=C.UTF-8" \
  --env="LC_ALL=C.UTF-8" \
  -- cli

echo "CLI session ended."