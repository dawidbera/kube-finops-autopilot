#!/bin/bash

# KubeFinOps Autopilot - Test Runner
# This script runs all unit and integration tests across all microservices.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🧪 Starting KubeFinOps Autopilot Test Suite..."
echo "--------------------------------------------"

# Run Maven tests
cd "$PROJECT_ROOT"
./mvnw test

echo ""
echo "✅ All tests completed successfully!"
echo "--------------------------------------------"
echo "Summary:"
echo " - Recommender Service: OK (Unit + Integration)"
echo " - Policy Service:      OK (Unit + Integration)"
echo " - GitOps Bot:          OK (Unit + Integration)"
echo "--------------------------------------------"
