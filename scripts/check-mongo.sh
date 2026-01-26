#!/bin/bash

# Script to check inserted recommendations in MongoDB

echo "🔍 Querying MongoDB for the last 5 recommendations..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

docker compose -f "$PROJECT_ROOT/infra/docker-compose-lite.yml" exec -T mongodb mongosh kubefinops --quiet --eval "db.recommendations.find().sort({_id: -1}).limit(5)"

echo ""
echo "✅ Done."
