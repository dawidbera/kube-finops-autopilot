#!/bin/bash

# KubeFinOps Autopilot - Local Dev Start Script
# This script starts the infrastructure and microservices in background.

set -e

echo "🚀 Starting infrastructure (Kafka, MongoDB, MinIO)..."
docker compose -f infra/docker-compose-lite.yml up -d

echo "⏳ Waiting for Kafka to be ready..."
# Simple sleep to ensure Kafka is up before apps start
sleep 10

echo "🔨 Building project..."
./mvnw clean install -DskipTests

echo "🏃 Starting Recommender Service..."
./mvnw spring-boot:run -pl services/recommender-service > recommender.log 2>&1 &
REC_PID=$!
echo "   [PID: $REC_PID] Logs: tail -f recommender.log"

echo "🏃 Starting Policy Service..."
./mvnw spring-boot:run -pl services/policy-service > policy.log 2>&1 &
POL_PID=$!
echo "   [PID: $POL_PID] Logs: tail -f policy.log"

echo ""
echo "✅ All services are starting in the background."
echo "   To see Recommender logs: tail -f recommender.log"
echo "   To see Policy logs:      tail -f policy.log"
echo "   To stop everything:      kill $REC_PID $POL_PID && docker compose -f infra/docker-compose-lite.yml down"

# Save PIDs to a file for easy stopping later
echo "$REC_PID $POL_PID" > .services.pids
