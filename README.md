# KubeFinOps Autopilot - Platform Repository

This repository contains the core services and infrastructure configuration for the **KubeFinOps Autopilot** platform.

## Overview
The platform components stored here include:
- **Recommender Service**: Analyzes usage and generates rightsizing recommendations.
- **Policy Service**: Validates recommendations against budgets.
- **GitOps Bot**: Automates Pull Request creation.
- **Infrastructure**: Helm charts and Docker Compose setups for local development.

## Quick Start (Local Development)
To run the microservices locally (with Kafka and MongoDB):

1. **Start Development Environment**:
   ```bash
   ./scripts/start-dev.sh
   ```
   This script builds the services, starts the infrastructure (Kafka, Mongo), and runs the applications.

2. **Check Status**:
   ```bash
   ./scripts/check-mongo.sh
   ```
   Queries MongoDB to see generated recommendations.

3. **Stop Environment**:
   ```bash
   ./scripts/stop-dev.sh
   ```

## Repository Structure
- `services/`: Source code for microservices.
- `infra/`: Infrastructure definitions (Docker Compose, scripts).
- `deploy/`: Helm charts and GitOps configurations.
