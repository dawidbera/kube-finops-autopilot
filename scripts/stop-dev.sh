#!/bin/bash

# KubeFinOps Autopilot - Local Dev Stop Script

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🛑 Stopping services..."
if [ -f "$PROJECT_ROOT/.services.pids" ]; then
    PIDS=$(cat "$PROJECT_ROOT/.services.pids")
    kill $PIDS 2>/dev/null || true
    rm "$PROJECT_ROOT/.services.pids"
    echo "   Microservices stopped."
else
    echo "   No .services.pids file found. Services might not be running."
fi

echo "📉 Bringing down infrastructure..."
docker compose -f "$PROJECT_ROOT/infra/docker-compose-lite.yml" down

echo "🧹 Cleaning up logs..."
rm -f "$PROJECT_ROOT/recommender.log" "$PROJECT_ROOT/policy.log" "$PROJECT_ROOT/gitops-bot.log"

echo "✅ Done."
