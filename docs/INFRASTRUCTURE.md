# Infrastructure Overview - KubeFinOps Autopilot

This document describes the infrastructure components required to run the KubeFinOps Autopilot platform.

## Core Components

### 1. Messaging: Apache Kafka
- **Role**: Event bus for the entire platform.
- **Topics**: 
  - `recommendation.created`
  - `recommendation.approved`
  - `policy.violated`
  - `gitops.pr.created`
- **Configuration**: Standard bitnami/kafka helm chart or single-node docker for lite profile.

### 2. Database: MongoDB
- **Role**: Stores policies, recommendation history, and audit logs.
- **Data Model**: Document-based, allows flexible policy definitions.

### 3. Object Storage: MinIO
- **Role**: S3-compatible storage for cost reports and audit snapshots.
- **Usage**: Exporting detailed analysis reports for historical review.

### 4. Metrics: Prometheus
- **Role**: Source of truth for workload resource usage (CPU/Memory).
- **Queries**: p95/p99 usage metrics are pulled by the Recommender Service.

### 5. GitOps Controller: Argo CD
- **Role**: Continuous Delivery. Synchronizes the GitOps repository with the target Kubernetes clusters.
- **Interaction**: The GitOps Bot commits changes to Git, and Argo CD applies them.

## Deployment Profiles

### Lite Profile (Local Dev)
- **Target**: Local K3s / Kind.
- **Resource Usage**: ~20GB RAM.
- **Setup**: `infra/start-platform-lite.sh`.

### Full Profile (Production-like)
- **Target**: Cloud EKS/GKE/AKS.
- **Includes**: HA Kafka, HA MongoDB, multi-cluster support.
