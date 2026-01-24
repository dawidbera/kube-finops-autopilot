#!/bin/bash
set -e

echo "🚀 Starting KubeFinOps Autopilot Platform (Lite Profile)..."

# 1. Check prerequisites
command -v docker >/dev/null 2>&1 || { echo >&2 "Docker is required but not installed. Aborting."; exit 1; }
command -v k3d >/dev/null 2>&1 || { echo >&2 "k3d is required but not installed. Aborting."; exit 1; }
command -v helm >/dev/null 2>&1 || { echo >&2 "Helm is required but not installed. Aborting."; exit 1; }

CLUSTER_NAME="kubefinops-cluster"

# 2. Create Cluster (if not exists)
if k3d cluster list | grep -q "$CLUSTER_NAME"; then
    echo "✅ Cluster $CLUSTER_NAME already exists."
else
    echo "Creating k3d cluster: $CLUSTER_NAME..."
    k3d cluster create $CLUSTER_NAME \
        --servers 1 \
        --agents 1 \
        --port "8080:80@loadbalancer" \
        --port "3000:3000@loadbalancer" \
        --wait
    echo "✅ Cluster created."
fi

# 3. Install Argo CD
echo "Installing Argo CD..."
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
echo "⏳ Waiting for Argo CD server..."
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=300s

# 4. Install Monitoring Stack (Prometheus + Grafana)
echo "Installing Prometheus & Grafana (kube-prometheus-stack)..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
# Installing with minimal resources for Lite profile
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
    --namespace monitoring \
    --set prometheus.prometheusSpec.resources.requests.memory=256Mi \
    --set grafana.resources.requests.memory=128Mi

# 5. Install Kafka (Strimzi or Bitnami Lite)
echo "Installing Kafka (Bitnami Lite)..."
helm repo add bitnami https://charts.bitnami.com/bitnami
kubectl create namespace kafka --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install kafka bitnami/kafka \
    --namespace kafka \
    --set replicas=1 \
    --set zookeeper.replicas=1 \
    --set persistence.enabled=false \
    --set resources.requests.memory=256Mi

echo "✅ Platform Setup Complete!"
echo "➡️  Argo CD: https://localhost:8080 (port-forward needed if not using ingress)"
echo "➡️  Grafana: http://localhost:3000 (default creds: admin/prom-operator)"
