#!/bin/bash

# KubeFinOps Autopilot - Local Dev Stop Script

echo "🛑 Stopping services..."
if [ -f .services.pids ]; then
    PIDS=$(cat .services.pids)
    kill $PIDS 2>/dev/null || true
    rm .services.pids
    echo "   Microservices stopped."
else
    echo "   No .services.pids file found. Services might not be running."
fi

echo "📉 Bringing down infrastructure..."
docker compose -f infra/docker-compose-lite.yml down

echo "🧹 Cleaning up logs..."
rm -f recommender.log policy.log gitops-bot.log

echo "✅ Done."
