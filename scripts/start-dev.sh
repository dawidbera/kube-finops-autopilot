#!/bin/bash

# KubeFinOps Autopilot - Local Dev Start Script
# This script starts the infrastructure, microservices, and initializes policies.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load .env file if it exists
if [ -f "$PROJECT_ROOT/.env" ]; then
  echo "📄 Loading environment variables from .env file..."
  export $(grep -v '^#' "$PROJECT_ROOT/.env" | xargs)
fi

export SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS=localhost:9092

echo "🚀 Starting infrastructure (Kafka, MongoDB, MinIO)..."
docker compose -f "$PROJECT_ROOT/infra/docker-compose-lite.yml" up -d

echo "⏳ Waiting for Kafka & Mongo to be ready..."
sleep 10

echo "🛡️ Initializing MongoDB Policies..."
docker compose -f "$PROJECT_ROOT/infra/docker-compose-lite.yml" exec -T mongodb mongosh kubefinops --quiet --eval '
db.policies.deleteMany({});
db.policies.insertOne({
  name: "Global Budget Limit",
  namespace: null,
  maxCpu: "1000m",
  maxMemory: "2Gi",
  enabled: true
});
db.policies.insertOne({
  name: "Restrictive Dev Policy",
  namespace: "dev",
  maxCpu: "100m",
  maxMemory: "128Mi",
  enabled: true
});
'
echo "   Policies seeded successfully."

echo "🔨 Building project..."
cd "$PROJECT_ROOT"
./mvnw clean install -DskipTests

echo "🏃 Starting Recommender Service..."
./mvnw spring-boot:run -pl services/recommender-service > "$PROJECT_ROOT/recommender.log" 2>&1 &
REC_PID=$!
echo "   [PID: $REC_PID] Logs: tail -f recommender.log"

echo "🏃 Starting Policy Service..."
./mvnw spring-boot:run -pl services/policy-service > "$PROJECT_ROOT/policy.log" 2>&1 &
POL_PID=$!
echo "   [PID: $POL_PID] Logs: tail -f policy.log"

echo "🏃 Starting GitOps Bot..."
./mvnw spring-boot:run -pl services/gitops-bot > "$PROJECT_ROOT/gitops-bot.log" 2>&1 &
BOT_PID=$!
echo "   [PID: $BOT_PID] Logs: tail -f gitops-bot.log"

# Save PIDs to a file for easy stopping later
echo "$REC_PID $POL_PID $BOT_PID" > "$PROJECT_ROOT/.services.pids"

echo ""
echo "✅ Environment is UP and running!"
echo "   - Infrastructure: Docker (Kafka, Mongo, MinIO)"
echo "   - Services: Recommender & Policy (Background)"
echo ""
echo "👉 Use './scripts/check-mongo.sh' to watch recommendations in real-time."
echo "👉 Use './scripts/stop-dev.sh' to stop everything."