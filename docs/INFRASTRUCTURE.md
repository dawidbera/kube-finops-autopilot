# Infrastructure Overview - KubeFinOps Autopilot

This document describes the infrastructure components required to run the KubeFinOps Autopilot platform.

## 🏗️ Core Components

### 1. Messaging: Apache Kafka
- **Role**: Event-driven backbone for service decoupling.
- **Port**: `9092`
- **Managed Topics**: 
  - `recommendation.created`: Analysis result.
  - `recommendation.approved`: Governance pass.
  - `policy.violated`: Governance rejection.
  - `gitops.pr.created`: PR link and branch info.
  - `change.applied`: Successful Argo CD sync.
  - `change.failed`: Sync failure notification.

### 2. Metadata Database: MongoDB
- **Role**: Persistent storage for policies, recommendation logs, and technical reports index.
- **Port**: `27017`
- **Key Collections**: `policies`, `recommendations`, `reports`.

### 3. Object Storage: MinIO (S3 Compatible)
- **Role**: Audit storage for detailed technical reports.
- **Ports**: `9000` (API), `9001` (Console)
- **Usage**: JSON reports are pushed by `recommender-service` for long-term audit and compliance.

### 4. Metrics & Source of Truth: Prometheus
- **Role**: Collects performance data from target workloads.
- **Port**: `9090`
- **Queries**: Used by Recommender to fetch `p95` CPU/RAM usage over a 5-minute window.

### 5. Visualization: Grafana
- **Role**: FinOps Dashboards.
- **Port**: `3000`
- **Provisioning**: Dashboards and Prometheus datasource are automatically loaded via `infra/grafana/provisioning`.

## 🔄 Data Flow

1.  **Prometheus** scrapes usage metrics.
2.  **Recommender** queries Prometheus and calculates optimization proposals.
3.  **Kafka** carries the proposal to **Policy Service**.
4.  **MongoDB** provides the rules for validation.
5.  **GitOps Bot** receives approved changes and commits to **Git**.
6.  **Argo CD** (Planned) pulls Git changes and updates **Kubernetes**.

## 💻 Local Development Setup

The local infrastructure is orchestrated via Docker Compose:
- **Location**: `kube-finops-autopilot/infra/docker-compose-lite.yml`
- **Service Ports**:
  - Kafka: `9092`
  - MongoDB: `27017`
  - MinIO: `9000/9001`
  - Prometheus: `9090`
  - Grafana: `3000`

### Host Resolution (Linux)
To allow Docker containers (Prometheus) to reach Spring Boot services running on the host, we use `extra_hosts` mapping `host.docker.internal` to `host-gateway`.