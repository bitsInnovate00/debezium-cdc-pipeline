.PHONY: help deploy clean test monitor status logs build-consumer connectors check-prereqs

# Default target
help:
	@echo "Debezium CDC Pipeline - Available Commands"
	@echo "==========================================="
	@echo ""
	@echo "Setup & Deployment:"
	@echo "  make check-prereqs    - Check if prerequisites are installed"
	@echo "  make deploy           - Deploy all components to Kubernetes"
	@echo "  make build-consumer   - Build Ignite consumer Docker image"
	@echo "  make connectors       - Create Debezium connectors"
	@echo ""
	@echo "Testing & Monitoring:"
	@echo "  make test             - Run pipeline tests"
	@echo "  make monitor          - Monitor the pipeline (Ctrl+C to exit)"
	@echo "  make status           - Show status of all components"
	@echo "  make logs             - Show logs from all components"
	@echo ""
	@echo "Debugging:"
	@echo "  make logs-postgres    - Show PostgreSQL logs"
	@echo "  make logs-kafka       - Show Kafka logs"
	@echo "  make logs-connect     - Show Kafka Connect logs"
	@echo "  make logs-ignite      - Show Ignite logs"
	@echo "  make logs-consumer    - Show Ignite Consumer logs"
	@echo "  make connector-status - Show Debezium connector status"
	@echo "  make topics           - List Kafka topics"
	@echo ""
	@echo "Database Operations:"
	@echo "  make psql             - Connect to PostgreSQL"
	@echo "  make insert-test      - Insert test data"
	@echo ""
	@echo "Cleanup:"
	@echo "  make clean            - Delete all resources"
	@echo ""

# Check prerequisites
check-prereqs:
	@echo "Checking prerequisites..."
	@command -v kubectl >/dev/null 2>&1 || { echo "kubectl is not installed"; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "docker is not installed"; exit 1; }
	@kubectl cluster-info >/dev/null 2>&1 || { echo "Cannot connect to Kubernetes cluster"; exit 1; }
	@echo "✓ All prerequisites met"

# Deploy all components
deploy: check-prereqs
	@echo "Deploying Debezium CDC Pipeline..."
	@./scripts/deploy-all.sh

# Build Ignite consumer image
build-consumer:
	@echo "Building Ignite consumer Docker image..."
	@if command -v minikube >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then \
		eval $$(minikube docker-env); \
	fi
	@docker build -t ignite-consumer:1.0 ./ignite-consumer

# Create Debezium connectors
connectors:
	@echo "Creating Debezium connectors..."
	@./scripts/create-connectors.sh

# Run tests
test:
	@echo "Testing the pipeline..."
	@./scripts/test-pipeline.sh

# Monitor the pipeline
monitor:
	@./scripts/monitor.sh

# Show status of all components
status:
	@echo "=== Pods Status ==="
	@kubectl get pods -n debezium-pipeline
	@echo ""
	@echo "=== Services ==="
	@kubectl get svc -n debezium-pipeline
	@echo ""
	@echo "=== PVCs ==="
	@kubectl get pvc -n debezium-pipeline 2>/dev/null || echo "No PVCs found"

# Show logs from all components (last 20 lines each)
logs:
	@echo "=== PostgreSQL Logs ==="
	@kubectl logs --tail=20 -n debezium-pipeline -l app=postgres 2>/dev/null || echo "Pod not ready"
	@echo ""
	@echo "=== Kafka Logs ==="
	@kubectl logs --tail=20 -n debezium-pipeline -l app=kafka 2>/dev/null || echo "Pod not ready"
	@echo ""
	@echo "=== Kafka Connect Logs ==="
	@kubectl logs --tail=20 -n debezium-pipeline -l app=kafka-connect 2>/dev/null || echo "Pod not ready"
	@echo ""
	@echo "=== Ignite Consumer Logs ==="
	@kubectl logs --tail=20 -n debezium-pipeline -l app=ignite-consumer 2>/dev/null || echo "Pod not ready"

# Individual component logs
logs-postgres:
	@kubectl logs -f -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=postgres -o jsonpath='{.items[0].metadata.name}')

logs-kafka:
	@kubectl logs -f -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=kafka -o jsonpath='{.items[0].metadata.name}')

logs-connect:
	@kubectl logs -f -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=kafka-connect -o jsonpath='{.items[0].metadata.name}')

logs-ignite:
	@kubectl logs -f -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=ignite -o jsonpath='{.items[0].metadata.name}')

logs-consumer:
	@kubectl logs -f -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=ignite-consumer -o jsonpath='{.items[0].metadata.name}')

# Show connector status
connector-status:
	@echo "Connector Status:"
	@kubectl exec -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=kafka-connect -o jsonpath='{.items[0].metadata.name}') -- \
		curl -s http://localhost:8083/connectors/postgres-source-connector/status 2>/dev/null | jq . || echo "Connector not available"

# List Kafka topics
topics:
	@echo "Kafka Topics:"
	@kubectl exec -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=kafka -o jsonpath='{.items[0].metadata.name}') -- \
		kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep -E "dbserver1|debezium" || echo "No topics found"

# Connect to PostgreSQL
psql:
	@kubectl exec -it -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=postgres -o jsonpath='{.items[0].metadata.name}') -- \
		psql -U postgres -d testdb

# Insert test data
insert-test:
	@echo "Inserting test data..."
	@kubectl exec -n debezium-pipeline $$(kubectl get pods -n debezium-pipeline -l app=postgres -o jsonpath='{.items[0].metadata.name}') -- \
		psql -U postgres -d testdb -c "INSERT INTO customers (name, email) VALUES ('Test User', 'test-$$(date +%s)@example.com');"
	@echo "✓ Test data inserted"

# Clean up all resources
clean:
	@echo "Cleaning up..."
	@./scripts/cleanup.sh

# Port forwarding for local access
forward-kafka-connect:
	@echo "Forwarding Kafka Connect to localhost:8083"
	@kubectl port-forward -n debezium-pipeline svc/kafka-connect-service 8083:8083

forward-postgres:
	@echo "Forwarding PostgreSQL to localhost:5432"
	@kubectl port-forward -n debezium-pipeline svc/postgres-service 5432:5432

forward-ignite:
	@echo "Forwarding Ignite to localhost:10300"
	@kubectl port-forward -n debezium-pipeline svc/ignite-rest-service 10300:10300

# Quick start - deploy everything
quickstart: check-prereqs deploy connectors
	@echo ""
	@echo "✓ Deployment complete!"
	@echo ""
	@echo "Run 'make test' to test the pipeline"
	@echo "Run 'make monitor' to monitor the system"
