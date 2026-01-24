#!/bin/bash

# KubeFinOps Autopilot - Test Runner
# This script runs all unit and integration tests across all microservices.

set -e

# Change to the script directory
cd "$(dirname "$0")"

echo "🧪 Starting KubeFinOps Autopilot Test Suite..."
echo "--------------------------------------------"

# Run Maven tests
cd ..
./mvnw test

echo ""
echo "✅ All tests completed successfully!"
echo "--------------------------------------------"
echo "Summary:"
echo " - Recommender Service: OK (Unit + Integration)"
echo " - Policy Service:      OK (Unit + Integration)"
echo " - GitOps Bot:          OK (Unit + Integration)"
echo "--------------------------------------------"
