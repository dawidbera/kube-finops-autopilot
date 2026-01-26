#!/bin/bash

# KubeFinOps Autopilot - Local Dev Start Script (DOCKERIZED)
# This script starts the entire platform (infra + apps) using Docker Compose.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load .env file if it exists
if [ -f "$PROJECT_ROOT/.env" ]; then
  echo "📄 Loading environment variables from .env file..."
  export $(grep -v '^#' "$PROJECT_ROOT/.env" | xargs)
fi

echo "🔨 Building JAR files..."
cd "$PROJECT_ROOT"
./mvnw clean package -DskipTests

echo "🚀 Starting everything via Docker Compose..."
docker compose -f "$PROJECT_ROOT/infra/docker-compose-full.yml" up -d --build

echo "⏳ Waiting for MongoDB to be ready..."
sleep 10

echo "🛡️ Initializing MongoDB Policies..."
docker compose -f "$PROJECT_ROOT/infra/docker-compose-full.yml" exec -T mongodb mongosh kubefinops --quiet --eval '
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

echo ""
echo "✅ Platform is UP and running in Docker!"
echo "   - Infrastructure & Services: Orchestrated by Docker Compose"
echo ""
echo "👉 Use 'docker compose -f infra/docker-compose-full.yml logs -f' to see logs."
echo "👉 Use './scripts/check-mongo.sh' to watch recommendations."
echo "👉 Use 'docker compose -f infra/docker-compose-full.yml down' to stop everything."
