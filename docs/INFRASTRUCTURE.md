# Infrastructure Architecture - KubeFinOps Autopilot (K3s)

This document provides a detailed technical overview of the infrastructure supporting the KubeFinOps platform, now fully orchestrated within a **K3s Kubernetes cluster**.

## 🏗️ Core Infrastructure (Namespace: `kubefinops`)

The entire platform is deployed using **Helm**, ensuring consistent and reproducible environments.

### 1. Event Bus: Apache Kafka (Bitnami)
- **Service Name**: `infra-kafka`
- **Internal Port**: `9092`
- **Security**: Secured with **SASL/SCRAM-SHA-256** authentication.
- **Role**: Decouples analysis from policy and execution. All service communication happens via Kafka topics using Spring Cloud Stream.

### 2. Governance Database: MongoDB (Bitnami)
- **Service Name**: `infra-mongodb`
- **Internal Port**: `27017`
- **Role**: Stores rightsizing policies, historical recommendations, and the state of the FinOps control loop.

### 3. Monitoring Stack: Prometheus & Grafana
- **Source of Truth**: Prometheus (`infra-monitoring-kube-prom-prometheus`)
- **Query Engine**: Used by the Recommender Service to fetch P95 resource usage.
- **Visualization**: Grafana (`infra-monitoring-grafana`)
- **Provisioning**: Dashboards (e.g., *FinOps Overview*) are automatically provisioned using a sidecar container watching for ConfigMaps with the label `grafana_dashboard: "1"`.

### 4. GitOps Controller: Argo CD
- **Service Name**: `infra-argocd-server`
- **Role**: Continuously monitors the `smarthealth-gitops` folder in the Git repository and synchronizes the cluster state with the desired configuration (Rightsizing manifests).

### 5. Object Storage: MinIO
- **Service Name**: `infra-minio`
- **Role**: S3-compatible storage for detailed technical reports and audit snapshots generated during the recommendation cycle.

---

## 🔒 Security & Connectivity

### Service Communication
Microservices connect to infrastructure using internal Kubernetes DNS names:
- Kafka: `infra-kafka.kubefinops.svc.cluster.local:9092`
- MongoDB: `infra-mongodb.kubefinops.svc.cluster.local:27017`
- Prometheus: `http://infra-monitoring-kube-prom-prometheus.kubefinops.svc.cluster.local:9090`

### Authentication
- **Kafka**: Authenticated via SASL. Credentials are managed via Kubernetes Secrets (`infra-kafka-user-passwords`).
- **GitOps**: The Bot uses a **GitHub Personal Access Token (PAT)** to push changes to the repository.

---

## 🔄 Deployment Strategy

The platform follows a **3-Repo Pattern**:
1. **Platform Repo**: This repository containing the core logic and Helm charts.
2. **GitOps Repo**: A dedicated folder (`smarthealth-gitops`) tracked by Argo CD for workload configurations.
3. **Application Repo**: Agnostic source code of the target applications.

---

## 🛠️ Management Commands

### Monitoring Cluster Health
```bash
kubectl get pods -n kubefinops
```

### Accessing Grafana
```bash
kubectl port-forward -n kubefinops svc/infra-monitoring-grafana 3000:80
```

### Accessing Argo CD UI
```bash
kubectl port-forward -n kubefinops svc/infra-argocd-server 8080:443
```
