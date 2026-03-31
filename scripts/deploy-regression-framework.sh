#!/usr/bin/env bash
# =============================================================================
# deploy-regression-framework.sh
# Deploy the CDC regression test framework to the existing Kubernetes cluster.
#
# Requires:
#   - kubectl configured and pointing at the target cluster
#   - The debezium-pipeline namespace to already exist
#   - Docker access to build the container image
# =============================================================================
set -euo pipefail

NAMESPACE="${NAMESPACE:-debezium-pipeline}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
REGISTRY="${REGISTRY:-}"  # Optional registry prefix

echo "======================================================"
echo " Deploying CDC Regression Test Framework"
echo " Namespace: $NAMESPACE"
echo "======================================================"

cd "$(dirname "$0")/.."

# Build Docker image
echo ""
echo "==> Building regression test framework Docker image..."
docker build -t regression-test-framework:${IMAGE_TAG} ./regression-test-framework/

if [[ -n "$REGISTRY" ]]; then
  echo "==> Pushing image to $REGISTRY..."
  docker tag regression-test-framework:${IMAGE_TAG} ${REGISTRY}/regression-test-framework:${IMAGE_TAG}
  docker push ${REGISTRY}/regression-test-framework:${IMAGE_TAG}
fi

# Apply Kubernetes manifests
echo ""
echo "==> Deploying Kubernetes resources..."
kubectl apply -f kubernetes/regression-test/regression-test-configmap.yaml
kubectl apply -f kubernetes/regression-test/regression-test-secret.yaml
kubectl apply -f kubernetes/regression-test/regression-postgres.yaml
kubectl apply -f kubernetes/regression-test/regression-test-deployment.yaml

# Wait for pods
echo ""
echo "==> Waiting for regression-postgres to be ready..."
kubectl rollout status deployment/regression-postgres -n "$NAMESPACE" --timeout=120s

echo "==> Waiting for regression-test-framework to be ready..."
kubectl rollout status deployment/regression-test-framework -n "$NAMESPACE" --timeout=120s

echo ""
echo "======================================================"
echo " Deployment complete!"
echo ""
echo " Access the framework:"
echo "   kubectl port-forward svc/regression-test-framework 8085:8085 -n $NAMESPACE"
echo ""
echo " API base URL: http://localhost:8085"
echo " Health check: http://localhost:8085/actuator/health"
echo "======================================================"
