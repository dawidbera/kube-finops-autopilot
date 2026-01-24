#!/bin/bash

# Script to check inserted recommendations in MongoDB

echo "🔍 Querying MongoDB for the last 5 recommendations..."

docker compose -f infra/docker-compose-lite.yml exec -T mongodb mongosh kubefinops --quiet --eval "db.recommendations.find().sort({_id: -1}).limit(5)"

echo ""
echo "✅ Done."
